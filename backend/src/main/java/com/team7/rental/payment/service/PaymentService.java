package com.team7.rental.payment.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.item.service.ItemQueryService;
import com.team7.rental.payment.client.PaymentGateway;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.dto.PaymentConfirmRequest;
import com.team7.rental.payment.dto.PaymentConfirmResponse;
import com.team7.rental.payment.dto.PaymentOrderResponse;
import com.team7.rental.payment.entity.PaymentType;
import com.team7.rental.payment.repository.PaymentRef;
import com.team7.rental.payment.service.ExtensionPaymentTxService.ExtensionConfirmOutcome;
import com.team7.rental.payment.service.PaymentTxService.AlreadyProcessed;
import com.team7.rental.payment.service.PaymentTxService.AmountMismatch;
import com.team7.rental.payment.service.PaymentTxService.ConfirmDecision;
import com.team7.rental.payment.service.PaymentTxService.ConfirmOutcome;
import com.team7.rental.payment.service.PaymentTxService.OrderResult;
import com.team7.rental.payment.service.PaymentTxService.Proceed;
import com.team7.rental.rental.service.RentalExtensionService;
import com.team7.rental.rental.service.RentalNotificationPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 대여료·보증금 결제 (REQ-024). 트랜잭션을 열지 않고 흐름을 잇는다: [TX1] 시도 기록 → PG 호출 → [TX2] 결과 반영 (docs/domains/payment.md 원칙 3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

	/** 연장료 주문명 접미사 (토스 결제창 표시) */
	static final String EXTENSION_ORDER_NAME_SUFFIX = " 대여 연장";

	private final PaymentTxService paymentTxService;
	private final PaymentRefundService paymentRefundService;
	private final PaymentGateway paymentGateway;
	private final ItemQueryService itemQueryService;
	private final ExtensionPaymentTxService extensionPaymentTxService;
	private final RentalNotificationPort rentalNotificationPort;

	/**
	 * 주문 생성. extensionId가 있으면 연장료 주문(REQ-038), 없으면 대여료 + 보증금 주문이다.
	 *
	 * @throws BusinessException RENTAL_NOT_FOUND, PAYMENT_RENTER_ONLY, PAYMENT_EXPIRED, RENTAL_INVALID_TRANSITION,
	 *     RENTAL_EXTENSION_NOT_FOUND
	 */
	public PaymentOrderResponse createOrder(Long rentalId, Long extensionId, Long memberId) {
		OrderResult order = extensionId == null
			? paymentTxService.createOrder(rentalId, memberId)
			: extensionPaymentTxService.createOrder(rentalId, extensionId, memberId);
		String itemName = itemQueryService.getSummary(order.itemId()).name();
		String orderName = extensionId == null ? itemName : itemName + EXTENSION_ORDER_NAME_SUFFIX;
		return new PaymentOrderResponse(order.orderId(), order.amount(), orderName, customerKeyOf(memberId));
	}

	/**
	 * 결제 승인. 이미 승인된 orderId는 기존 결과를 돌려준다(멱등).
	 *
	 * @throws BusinessException PAYMENT_NOT_FOUND, PAYMENT_RENTER_ONLY, PAYMENT_INVALID_STATUS, PAYMENT_EXPIRED,
	 *     PAYMENT_AMOUNT_MISMATCH, PAYMENT_APPROVAL_FAILED, PAYMENT_RESULT_UNKNOWN, PAYMENT_CANCELED_AFTER_EXPIRY
	 */
	public PaymentConfirmResponse confirm(Long memberId, PaymentConfirmRequest request) {
		PaymentRef ref = paymentTxService.getRef(request.orderId());
		ConfirmDecision decision = ref.type() == PaymentType.EXTENSION
			? extensionPaymentTxService.prepareConfirm(ref.rentalId(), ref.paymentId(), memberId, request.paymentKey(),
				request.amount())
			: paymentTxService.prepareConfirm(ref.rentalId(), memberId, request.orderId(), request.paymentKey(),
				request.amount());
		return switch (decision) {
			case AlreadyProcessed processed -> processed.response();
			case AmountMismatch mismatch -> throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
			case Proceed proceed -> callPg(request, ref, proceed);
		};
	}

	private PaymentConfirmResponse callPg(PaymentConfirmRequest request, PaymentRef ref, Proceed proceed) {
		PgResult result = paymentGateway.confirm(request.paymentKey(), request.orderId(), proceed.amount(),
			proceed.idempotencyKey());
		if (result instanceof PgResult.Unknown) {
			result = lookUpAfterUnknown(request.orderId());
		}
		return switch (result) {
			case PgResult.Success success -> ref.type() == PaymentType.EXTENSION
				? applyExtensionApproved(ref, proceed, success)
				: applyApproved(request, proceed, success);
			case PgResult.Failure failure -> {
				log.warn("Payment approval failed: orderId={}, code={}", request.orderId(), failure.code());
				paymentTxService.applyFailed(proceed.rentalId(), request.orderId(), proceed.transactionId(), failure);
				throw new BusinessException(ErrorCode.PAYMENT_APPROVAL_FAILED);
			}
			// 시도는 PENDING, 결제는 READY로 남는다. 재요청은 같은 키라 안전하고, 응답이 없으면 조정 배치가 처리한다
			case PgResult.Unknown unknown -> throw new BusinessException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
		};
	}

	// 응답을 못 받았으면 한 번 조회한다. 승인이 보이면 그 결과를 쓰고, 안 보이면 처리 중일 수 있어 알 수 없음으로 둔다(없다고 실패 처리하지 않는다)
	private PgResult lookUpAfterUnknown(String orderId) {
		PgResult lookup = paymentGateway.findByOrderId(orderId);
		if (lookup instanceof PgResult.Success success && success.payment().isApproved()) {
			return lookup;
		}
		return new PgResult.Unknown("approval not visible");
	}

	private PaymentConfirmResponse applyApproved(PaymentConfirmRequest request, Proceed proceed,
		PgResult.Success success) {
		ConfirmOutcome outcome = paymentTxService.applyApproved(proceed.rentalId(), request.orderId(),
			proceed.transactionId(), success);
		if (outcome.refund() != null) {
			log.warn("Payment approved after rental expired, refunding: orderId={}", request.orderId());
			paymentRefundService.execute(outcome.refund());
		}
		if (outcome.canceledAfterExpiry()) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCELED_AFTER_EXPIRY);
		}
		return outcome.response();
	}

	// 연장 결제(REQ-038): 확정했으면 커밋 뒤 양측 알림, 확정할 수 없었으면 전액 환불 후 PAYMENT_CANCELED_AFTER_EXPIRY
	private PaymentConfirmResponse applyExtensionApproved(PaymentRef ref, Proceed proceed, PgResult.Success success) {
		ExtensionConfirmOutcome outcome = extensionPaymentTxService.applyApproved(ref.rentalId(), ref.paymentId(),
			proceed.transactionId(), success);
		if (outcome.extended() != null) {
			RentalExtensionService.notifyExtended(rentalNotificationPort, outcome.extended());
		}
		if (outcome.refund() != null) {
			log.warn("Extension payment approved but extension not applicable, refunding: paymentId={}",
				ref.paymentId());
			paymentRefundService.execute(outcome.refund());
		}
		if (outcome.canceledAfterExpiry()) {
			throw new BusinessException(ErrorCode.PAYMENT_CANCELED_AFTER_EXPIRY);
		}
		return outcome.response();
	}

	// 회원 ID를 그대로 내보내지 않으려고 UUID로 바꾼다. 저장하지 않아도 회원마다 항상 같은 값이 나온다
	private String customerKeyOf(Long memberId) {
		return UUID.nameUUIDFromBytes(("customer:" + memberId).getBytes(StandardCharsets.UTF_8)).toString();
	}
}
