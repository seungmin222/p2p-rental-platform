package com.team7.rental.payment.entity;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;

class PaymentTest {

	private static final int MAX_RETRY = 5;

	private Payment approved(int rentalFee, int deposit) {
		Payment payment = Payment.createForRental(1L, "order-1", "key-1", rentalFee, deposit);
		payment.approve("pay-key");
		return payment;
	}

	@Test
	@DisplayName("결제 금액이 0원이면 만들 수 없다")
	void zeroAmountRejected() {
		assertThatThrownBy(() -> Payment.createForRental(1L, "order-1", "key-1", 0, 0))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("승인하면 보증금이 있으면 FROZEN, 0원이면 NONE이다")
	void approveDepositStatus() {
		assertThat(approved(10_000, 5_000).getDepositStatus()).isEqualTo(DepositStatus.FROZEN);
		assertThat(approved(10_000, 0).getDepositStatus()).isEqualTo(DepositStatus.NONE);
	}

	@Test
	@DisplayName("이미 승인된 결제는 다시 승인·실패 처리할 수 없다")
	void approveTwiceRejected() {
		Payment payment = approved(10_000, 5_000);

		assertThatThrownBy(() -> payment.approve("pay-key")).isInstanceOf(BusinessException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_INVALID_STATUS);
		assertThatThrownBy(payment::fail).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("누적 취소가 결제 금액에 닿으면 CANCELED, 아니면 PARTIAL_CANCELED이고 보증금은 REFUNDED다")
	void applyRefundStatus() {
		// given
		Payment full = approved(10_000, 5_000);
		Payment partial = approved(10_000, 5_000);
		// 대여료 0원 + 보증금 전액 반환도 전액 취소다 (open-issues #32)
		Payment depositOnly = approved(0, 5_000);

		// when
		full.requestRefund(15_000);
		full.applyRefund();
		partial.requestRefund(5_000);
		partial.applyRefund();
		depositOnly.requestRefund(5_000);
		depositOnly.applyRefund();

		// then
		assertThat(full.getStatus()).isEqualTo(PaymentStatus.CANCELED);
		assertThat(partial.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
		assertThat(partial.getRefundedAmount()).isEqualTo(5_000);
		assertThat(partial.getRefundPendingAmount()).isNull();
		assertThat(partial.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
		assertThat(depositOnly.getStatus()).isEqualTo(PaymentStatus.CANCELED);
	}

	@Test
	@DisplayName("보증금 0원 결제는 환불이 실패·성공해도 NONE을 유지하고, 환불액은 실패 동안 남아 있다")
	void noDepositStaysNone() {
		// given
		Payment payment = approved(10_000, 0);
		payment.requestRefund(10_000);

		// when
		payment.markRefundFailed();

		// then
		assertThat(payment.getDepositStatus()).isEqualTo(DepositStatus.NONE);
		assertThat(payment.getRefundPendingAmount()).isEqualTo(10_000);

		// when
		payment.applyRefund();

		// then
		assertThat(payment.getDepositStatus()).isEqualTo(DepositStatus.NONE);
		assertThat(payment.getRefundPendingAmount()).isNull();
	}

	@Test
	@DisplayName("환불 실패 후 성공하면 REFUND_FAILED → REFUNDED다")
	void refundFailedThenRefunded() {
		// given
		Payment payment = approved(10_000, 5_000);
		payment.requestRefund(5_000);

		// when
		payment.markRefundFailed();

		// then
		assertThat(payment.getDepositStatus()).isEqualTo(DepositStatus.REFUND_FAILED);

		// when
		payment.applyRefund();

		// then
		assertThat(payment.getDepositStatus()).isEqualTo(DepositStatus.REFUNDED);
	}

	@Test
	@DisplayName("진행 중인 환불이 있으면 또 시작할 수 없고, 남은 금액을 넘거나 0원인 환불은 거부한다")
	void requestRefundGuards() {
		Payment payment = approved(10_000, 5_000);

		assertThatThrownBy(() -> payment.requestRefund(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> payment.requestRefund(15_001)).isInstanceOf(IllegalArgumentException.class);

		payment.requestRefund(5_000);
		assertThatThrownBy(() -> payment.requestRefund(5_000)).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("재시도 실패가 최대 횟수에 닿으면 관리자 확인 대상이 된다")
	void retryLimit() {
		Payment payment = approved(10_000, 5_000);

		for (int i = 0; i < MAX_RETRY - 1; i++) {
			payment.countRefundRetryFailure(MAX_RETRY);
		}
		assertThat(payment.isNeedsAdminCheck()).isFalse();

		payment.countRefundRetryFailure(MAX_RETRY);
		assertThat(payment.isNeedsAdminCheck()).isTrue();
	}

	@Test
	@DisplayName("보증금 전액 차감은 FROZEN에서만 된다")
	void forfeit() {
		Payment payment = approved(10_000, 5_000);

		payment.forfeitDeposit();

		assertThat(payment.getDepositStatus()).isEqualTo(DepositStatus.FORFEITED);
		assertThatThrownBy(payment::forfeitDeposit).isInstanceOf(BusinessException.class);
	}
}
