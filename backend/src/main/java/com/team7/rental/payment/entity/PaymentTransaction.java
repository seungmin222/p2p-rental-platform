package com.team7.rental.payment.entity;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.team7.rental.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PG 호출 시도 하나. PG를 부르기 전에 PENDING으로 저장하고(TX1) 결과를 받으면 바꾼다(TX2). 환불 재시도는 이 행의 금액·키를 그대로 다시 쓴다.
 */
@Getter
@Entity
@Table(name = "payment_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long paymentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentTransactionAction action;

	@Column(nullable = false)
	private int amount;

	@Column(nullable = false, length = 100)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PaymentTransactionResult result;

	/** PG 응답 중 필요한 필드만 (backend/CLAUDE.md 로그 규칙) */
	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Object> pgResponse;

	public static PaymentTransaction pending(Long paymentId, PaymentTransactionAction action, int amount,
		String idempotencyKey) {
		PaymentTransaction transaction = new PaymentTransaction();
		transaction.paymentId = paymentId;
		transaction.action = action;
		transaction.amount = amount;
		transaction.idempotencyKey = idempotencyKey;
		transaction.result = PaymentTransactionResult.PENDING;
		return transaction;
	}

	public void succeed(Map<String, Object> pgResponse) {
		this.result = PaymentTransactionResult.SUCCESS;
		this.pgResponse = pgResponse;
	}

	public void fail(Map<String, Object> pgResponse) {
		this.result = PaymentTransactionResult.FAIL;
		this.pgResponse = pgResponse;
	}
}
