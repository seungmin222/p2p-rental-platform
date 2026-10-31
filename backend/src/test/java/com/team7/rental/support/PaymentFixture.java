package com.team7.rental.support;

import org.springframework.boot.test.context.TestComponent;

import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.entity.Rental;

/** 대여에 승인된 결제를 붙인다. PG(Fake)에도 같은 결제를 넣어 취소·조회가 동작한다 */
@TestComponent
public class PaymentFixture {

	private final PaymentRepository paymentRepository;
	private final FakePaymentGateway paymentGateway;

	PaymentFixture(PaymentRepository paymentRepository, FakePaymentGateway paymentGateway) {
		this.paymentRepository = paymentRepository;
		this.paymentGateway = paymentGateway;
	}

	public Payment createApproved(Rental rental) {
		String orderId = "order-" + rental.getId();
		String paymentKey = "pay-" + rental.getId();
		Payment payment = Payment.createForRental(rental.getId(), orderId, "key-" + rental.getId(),
			rental.getRentalFee(), rental.getDeposit());
		payment.approve(paymentKey);
		paymentGateway.seedApproved(paymentKey, orderId, payment.getAmount());
		return paymentRepository.save(payment);
	}
}
