package com.team7.rental.payment.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team7.rental.payment.entity.Payment;
import com.team7.rental.payment.entity.PaymentStatus;
import com.team7.rental.payment.entity.PaymentType;

import jakarta.persistence.LockModeType;

/**
 * 쓰기 트랜잭션은 대여 행 락 → 결제 행 락 순서로 잡는다(교착 방지). 락을 기다린 뒤에도 최신 값을 읽도록 *ForUpdate로 읽는다.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

	/** 환불 재시도 대상: 환불이 남았고 관리자 확인 전이며 lastRefundAttemptAt ≤ threshold. id 순 */
	@Query("""
		select new com.team7.rental.payment.repository.RefundRetryTarget(p.id, p.rentalId) from Payment p
		where p.refundPendingAmount is not null
			and p.needsAdminCheck = false
			and p.lastRefundAttemptAt <= :threshold
		order by p.id
		""")
	List<RefundRetryTarget> findRefundRetryTargets(@Param("threshold")
	LocalDateTime threshold);

	/**
	 * 조정 대상: createdAt ≥ since(최근 결제)이고 updatedAt ≤ settledBefore(진행 중인 요청과 겹치지 않게), 관리자 확인 전. id 순
	 */
	@Query("""
		select new com.team7.rental.payment.repository.ReconcileTarget(p.id, p.rentalId, p.orderId) from Payment p
		where p.createdAt >= :since
			and p.updatedAt <= :settledBefore
			and p.needsAdminCheck = false
		order by p.id
		""")
	List<ReconcileTarget> findReconcileTargets(@Param("since")
	LocalDateTime since, @Param("settledBefore")
	LocalDateTime settledBefore);

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

	/**
	 * 주문 생성용 일반 읽기. 대여 행 락을 잡은 뒤 부른다(같은 대여의 요청은 이미 한 줄로 서 있다). 잠금 읽기로 바꾸지 않는다. 행이 없으면 FOR UPDATE가 인덱스 갭 락을 잡아,
	 * 다른 대여의 주문 INSERT와 교착된다.
	 */
	Optional<Payment> findFirstByRentalIdAndTypeAndStatusOrderByIdDesc(Long rentalId, PaymentType type,
		PaymentStatus status);

	/** 대여의 결제 행들, 최신 순. 같은 대여에 FAILED 뒤 새 주문이 있을 수 있다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.rentalId = :rentalId and p.type = :type order by p.id desc")
	List<Payment> findAllByRentalIdForUpdate(@Param("rentalId")
	Long rentalId, @Param("type")
	PaymentType type);
}
