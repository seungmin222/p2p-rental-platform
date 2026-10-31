package com.team7.rental.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import com.team7.rental.payment.client.PaymentGateway;
import com.team7.rental.payment.client.PgResult;
import com.team7.rental.payment.client.PgResult.PgPayment;

/**
 * 토스 대신 쓰는 PG. 멱등성 키를 토스처럼 처리하고(같은 키는 첫 결과) 장애를 주입한다. 각 테스트가 끝나면 {@link IntegrationTestSupport}가 비운다.
 * <ul>
 *   <li>{@link Fault#ERROR}: PG 오류 응답, 처리 안 됨</li>
 *   <li>{@link Fault#NOT_APPLIED}: 결과 모름, 실제로 처리 안 됨</li>
 *   <li>{@link Fault#LOST_RESPONSE}: 결과 모름, 실제로는 처리됨(응답 유실)</li>
 * </ul>
 */
@Primary
@TestComponent
public class FakePaymentGateway implements PaymentGateway {

	public enum Fault {
		ERROR, NOT_APPLIED, LOST_RESPONSE
	}

	private final Map<String, PgPayment> paymentsByOrderId = new HashMap<>();
	private final Map<String, PgResult> resultsByKey = new HashMap<>();
	private final Deque<Fault> confirmFaults = new ArrayDeque<>();
	private final Deque<Fault> cancelFaults = new ArrayDeque<>();
	private final List<Integer> appliedCancels = new ArrayList<>();
	private Runnable beforeConfirm = () -> {};
	private int confirmCalls;
	private int cancelCalls;
	private int appliedConfirms;
	private int lookups;

	public synchronized void failNextConfirm(Fault fault) {
		confirmFaults.add(fault);
	}

	public synchronized void failNextCancel(Fault fault) {
		cancelFaults.add(fault);
	}

	/** 다음 승인이 PG에 닿기 직전에 실행한다(경합 재현) */
	public synchronized void beforeNextConfirm(Runnable action) {
		this.beforeConfirm = action;
	}

	/** PG에 승인된 결제가 이미 있는 상태를 만든다 */
	public synchronized void seedApproved(String paymentKey, String orderId, int amount) {
		paymentsByOrderId.put(orderId, new PgPayment(paymentKey, orderId, "DONE", amount, amount));
	}

	/** 받은 승인 호출 수(재요청 포함) */
	public synchronized int confirmCalls() {
		return confirmCalls;
	}

	/** 실제로 처리된 승인 수. 중복 승인이 없으면 결제당 1 */
	public synchronized int appliedConfirms() {
		return appliedConfirms;
	}

	/** 받은 취소 호출 수(재요청 포함) */
	public synchronized int cancelCalls() {
		return cancelCalls;
	}

	/** 실제로 처리된 취소 금액들. 중복 환불이 없으면 건당 한 번 */
	public synchronized List<Integer> appliedCancels() {
		return List.copyOf(appliedCancels);
	}

	public synchronized int lookups() {
		return lookups;
	}

	public synchronized PgPayment paymentOf(String orderId) {
		return paymentsByOrderId.get(orderId);
	}

	public synchronized void reset() {
		paymentsByOrderId.clear();
		resultsByKey.clear();
		confirmFaults.clear();
		cancelFaults.clear();
		appliedCancels.clear();
		beforeConfirm = () -> {};
		confirmCalls = 0;
		cancelCalls = 0;
		appliedConfirms = 0;
		lookups = 0;
	}

	@Override
	public PgResult confirm(String paymentKey, String orderId, int amount, String idempotencyKey) {
		Runnable action;
		synchronized (this) {
			action = beforeConfirm;
			beforeConfirm = () -> {};
		}
		action.run();
		synchronized (this) {
			confirmCalls++;
			Fault fault = confirmFaults.poll();
			if (fault == Fault.ERROR) {
				return new PgResult.Failure("PROVIDER_ERROR", "일시적인 오류");
			}
			if (fault == Fault.NOT_APPLIED) {
				return new PgResult.Unknown("timeout");
			}
			PgResult result = resultsByKey.computeIfAbsent(idempotencyKey, key -> {
				appliedConfirms++;
				PgPayment payment = new PgPayment(paymentKey, orderId, "DONE", amount, amount);
				paymentsByOrderId.put(orderId, payment);
				return new PgResult.Success(payment);
			});
			return fault == Fault.LOST_RESPONSE ? new PgResult.Unknown("timeout") : result;
		}
	}

	@Override
	public synchronized PgResult cancel(String paymentKey, int cancelAmount, String cancelReason,
		String idempotencyKey) {
		cancelCalls++;
		Fault fault = cancelFaults.poll();
		if (fault == Fault.ERROR) {
			return new PgResult.Failure("PROVIDER_ERROR", "일시적인 오류");
		}
		if (fault == Fault.NOT_APPLIED) {
			return new PgResult.Unknown("timeout");
		}
		PgResult result = resultsByKey.get(idempotencyKey);
		if (result == null) {
			PgPayment current = paymentsByOrderId.values().stream()
				.filter(payment -> payment.paymentKey().equals(paymentKey))
				.findFirst()
				.orElse(null);
			if (current == null) {
				return new PgResult.Failure(PgResult.NOT_FOUND, "결제 없음");
			}
			int balance = current.balanceAmount() - cancelAmount;
			if (balance < 0) {
				return new PgResult.Failure("EXCEED_CANCEL_AMOUNT", "잔액 초과");
			}
			PgPayment canceled = new PgPayment(paymentKey, current.orderId(),
				balance == 0 ? "CANCELED" : "PARTIAL_CANCELED", current.totalAmount(), balance);
			paymentsByOrderId.put(current.orderId(), canceled);
			appliedCancels.add(cancelAmount);
			result = new PgResult.Success(canceled);
			resultsByKey.put(idempotencyKey, result);
		}
		return fault == Fault.LOST_RESPONSE ? new PgResult.Unknown("timeout") : result;
	}

	@Override
	public synchronized PgResult findByOrderId(String orderId) {
		lookups++;
		PgPayment payment = paymentsByOrderId.get(orderId);
		return payment == null ? new PgResult.Failure(PgResult.NOT_FOUND, "결제 없음") : new PgResult.Success(payment);
	}
}
