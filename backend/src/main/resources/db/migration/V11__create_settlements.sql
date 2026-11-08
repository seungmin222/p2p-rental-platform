-- 제공자 정산 원장 (docs/erd.md settlements, REQ-039). 실제 송금은 하지 않고 상태만 바꾼다
-- 거래 완료 또는 수수료가 있는 취소와 같은 트랜잭션에서 대여당 한 번만 만든다(uk_settlements_rental_id)
CREATE TABLE settlements (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    rental_id        BIGINT      NOT NULL,
    owner_id         BIGINT      NOT NULL COMMENT '지급받을 제공자',
    source           VARCHAR(30) NOT NULL COMMENT 'SettlementSource (COMPLETED/CANCEL_FEE)',
    gross_amount     INT         NOT NULL COMMENT '대여료+연장료+연체료 또는 취소 수수료',
    fee_amount       INT         NOT NULL COMMENT 'floor(gross × settlement.fee-rate)',
    deduction_amount INT         NOT NULL COMMENT '손상 차감액. 수수료를 떼지 않는다',
    payout_amount    INT         NOT NULL COMMENT 'gross − fee + deduction',
    scheduled_date   DATE        NOT NULL COMMENT '완료(취소)일 + settlement.payout-delay 영업일',
    status           VARCHAR(30) NOT NULL COMMENT 'SettlementStatus',
    paid_at          DATETIME(6) NULL COMMENT '지급 배치가 PAID로 바꾼 시각',
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_settlements_rental_id UNIQUE (rental_id),
    CONSTRAINT fk_settlements_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_settlements_members FOREIGN KEY (owner_id) REFERENCES members (id),
    INDEX idx_settlements_status_scheduled_date (status, scheduled_date),
    INDEX idx_settlements_owner_id_id (owner_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
