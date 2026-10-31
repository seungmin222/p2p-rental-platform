package com.team7.rental.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentType;

import jakarta.persistence.LockModeType;

/**
 * 쓰기 트랜잭션은 대여 행 락 → 결제 행 락 순서로 잡는다(교착 방지). 락을 기다린 뒤에도 최신 값을 읽도록 *ForUpdate로 읽는다.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

	@Query("select p.rentalId from Payment p where p.orderId = :orderId")
	Optional<Long> findRentalIdByOrderId(@Param("orderId")
	String orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.orderId = :orderId")
	Optional<Payment> findByOrderIdForUpdate(@Param("orderId")
	String orderId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :id")
	Optional<Payment> findByIdForUpdate(@Param("id")
	Long id);

	/** 대여의 결제 행들, 최신 순. 같은 대여에 FAILED 뒤 새 주문이 있을 수 있다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.rentalId = :rentalId and p.type = :type order by p.id desc")
	List<Payment> findAllByRentalIdForUpdate(@Param("rentalId")
	Long rentalId, @Param("type")
	PaymentType type);
}
