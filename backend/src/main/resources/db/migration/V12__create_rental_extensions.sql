-- 대여 연장 (docs/erd.md rental_extensions, REQ-038)
-- PENDING_PAYMENT이고 expires_at > now인 행의 기간 (prev_end_date + 1) ~ new_end_date는 예약 겹침·캘린더 점유에 들어간다
CREATE TABLE rental_extensions (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    rental_id      BIGINT      NOT NULL,
    prev_end_date  DATE        NOT NULL COMMENT '연장 요청 시점의 종료일',
    new_end_date   DATE        NOT NULL COMMENT '연장 후 종료일',
    additional_fee INT         NOT NULL COMMENT '연장료 = 스냅샷 price_per_day × 연장 일수',
    status         VARCHAR(30) NOT NULL COMMENT 'ExtensionStatus',
    expires_at     DATETIME(6) NOT NULL COMMENT '결제 대기 만료 (payment.expire-minutes)',
    payment_id     BIGINT      NULL COMMENT '승인된 EXTENSION 결제. 0원 연장·결제 기능 꺼짐은 NULL',
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_rental_extensions_status_expires_at (status, expires_at),
    INDEX idx_rental_extensions_rental_id_status (rental_id, status),
    CONSTRAINT fk_rental_extensions_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_rental_extensions_payments FOREIGN KEY (payment_id) REFERENCES payments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
