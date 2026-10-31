package com.team7.rental.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team7.rental.payment.entity.PaymentTransaction;
import com.team7.rental.payment.entity.PaymentTransactionAction;
import com.team7.rental.payment.entity.PaymentTransactionResult;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

	boolean existsByPaymentIdAndActionAndResult(Long paymentId, PaymentTransactionAction action,
		PaymentTransactionResult result);

	List<PaymentTransaction> findAllByPaymentIdAndActionAndResult(Long paymentId, PaymentTransactionAction action,
		PaymentTransactionResult result);

	/** 가장 최근 시도. 환불 재시도가 같은 금액·키를 다시 쓴다 */
	Optional<PaymentTransaction> findFirstByPaymentIdAndActionOrderByIdDesc(Long paymentId,
		PaymentTransactionAction action);
}
