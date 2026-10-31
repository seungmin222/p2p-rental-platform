-- 결제·승인·취소 시도 이력 (docs/erd.md payments, payment_transactions, REQ-024·025)
-- held_at, damage_grade, deduction_amount, deduction_reason은 REQ-049에서 컬럼 추가 마이그레이션으로 넣는다
CREATE TABLE payments (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id             BIGINT       NOT NULL,
    type                  VARCHAR(30)  NOT NULL COMMENT 'PaymentType',
    order_id              VARCHAR(64)  NOT NULL COMMENT '토스 orderId (6~64자)',
    payment_key           VARCHAR(200) NULL COMMENT '토스 paymentKey. 승인 요청 때 받는다',
    amount                INT          NOT NULL,
    rental_fee_amount     INT          NOT NULL,
    deposit_amount        INT          NOT NULL,
    status                VARCHAR(30)  NOT NULL COMMENT 'PaymentStatus',
    deposit_status        VARCHAR(30)  NULL COMMENT 'DepositStatus. type=RENTAL이고 승인된 뒤에만',
    refunded_amount       INT          NOT NULL DEFAULT 0 COMMENT '누적 취소 금액',
    refund_pending_amount INT          NULL COMMENT '아직 끝나지 않은 환불액. NULL이 아니면 재시도 배치 대상',
    refund_retry_count    INT          NOT NULL DEFAULT 0,
    needs_admin_check     BOOLEAN      NOT NULL DEFAULT FALSE,
    idempotency_key       VARCHAR(100) NOT NULL COMMENT '승인 API 멱등성 키',
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_payments_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    INDEX idx_payments_rental_id_type (rental_id, type),
    INDEX idx_payments_refund_pending_amount (refund_pending_amount),
    INDEX idx_payments_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payment_transactions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id      BIGINT       NOT NULL,
    action          VARCHAR(30)  NOT NULL COMMENT 'PaymentTransactionAction',
    amount          INT          NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL COMMENT '재시도마다 행이 생기므로 유니크가 아니다',
    result          VARCHAR(30)  NOT NULL COMMENT 'PaymentTransactionResult. 타임아웃이면 PENDING으로 남는다',
    pg_response     JSON         NULL COMMENT 'PG 응답 중 필요한 필드만',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_payment_transactions_payments FOREIGN KEY (payment_id) REFERENCES payments (id),
    INDEX idx_payment_transactions_payment_id_action_result (payment_id, action, result)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
