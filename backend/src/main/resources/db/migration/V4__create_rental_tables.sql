-- 대여·상태 이력 (docs/erd.md 대여). 엔티티·마이그레이션만 먼저 만든다(feat/rental-entity). API는 REQ-012~019
-- rental_dates(실험용)·rental_cancel_agreements·item_inspections·rental_extensions는 각 REQ 브랜치에서 추가한다

-- members를 세 번 참조하므로 FK 이름에 역할명을 붙인다: fk_{테이블}_{참조테이블}_{역할}
CREATE TABLE rentals (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    item_id            BIGINT       NOT NULL,
    owner_id           BIGINT       NOT NULL COMMENT 'items.owner_id 비정규화(물품 소유자는 바뀌지 않음)',
    renter_id          BIGINT       NOT NULL,
    start_date         DATE         NOT NULL,
    end_date           DATE         NOT NULL COMMENT '연장 시 갱신',
    rental_fee         INT          NOT NULL COMMENT '최초 대여료 = price_per_day × 일수(양끝 포함). 연장료는 rental_extensions',
    deposit            INT          NOT NULL COMMENT '예약 시점 스냅샷',
    price_per_day      INT          NOT NULL COMMENT '예약 시점 스냅샷',
    status             VARCHAR(30)  NOT NULL COMMENT 'RentalStatus',
    payment_expires_at DATETIME(6)  NULL COMMENT 'PENDING_PAYMENT 만료 시각',
    handed_over_at     DATETIME(6)  NULL COMMENT '인계 확인 시각',
    returned_at        DATETIME(6)  NULL COMMENT '반납 요청(또는 미반납 신고) 시각',
    completed_at       DATETIME(6)  NULL COMMENT '거래 완료 시각',
    canceled_at        DATETIME(6)  NULL,
    cancel_type        VARCHAR(30)  NULL COMMENT 'CancelType',
    cancel_reason      VARCHAR(30)  NULL COMMENT 'CancelReason',
    cancel_reason_text VARCHAR(200) NULL COMMENT 'policies.md cancel.reason-text.max 200자',
    canceled_by        BIGINT       NULL COMMENT '시스템 취소면 NULL',
    cancel_fee         INT          NOT NULL DEFAULT 0,
    overdue_fee        INT          NOT NULL DEFAULT 0 COMMENT '반납 시점에 확정',
    extension_count    INT          NOT NULL DEFAULT 0,
    version            BIGINT       NOT NULL DEFAULT 0 COMMENT '낙관적 락(@Version)',
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_rentals_item_id_status_start_date_end_date (item_id, status, start_date, end_date),
    INDEX idx_rentals_renter_id_status (renter_id, status),
    INDEX idx_rentals_owner_id_status (owner_id, status),
    INDEX idx_rentals_status_end_date (status, end_date),
    INDEX idx_rentals_status_start_date (status, start_date),
    INDEX idx_rentals_status_returned_at (status, returned_at),
    CONSTRAINT fk_rentals_items FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_rentals_members_owner FOREIGN KEY (owner_id) REFERENCES members (id),
    CONSTRAINT fk_rentals_members_renter FOREIGN KEY (renter_id) REFERENCES members (id),
    CONSTRAINT fk_rentals_members_canceled_by FOREIGN KEY (canceled_by) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE rental_status_histories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id   BIGINT       NOT NULL,
    from_status VARCHAR(30)  NULL COMMENT 'RentalStatus. 생성 이력이면 NULL',
    to_status   VARCHAR(30)  NOT NULL COMMENT 'RentalStatus',
    changed_by  BIGINT       NULL COMMENT '시스템(배치)이면 NULL',
    reason      VARCHAR(255) NULL COMMENT '배치명 또는 사유(취소 사유 텍스트 200자 + 사유 코드)',
    changed_at  DATETIME(6)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_rental_status_histories_rental_id_changed_at (rental_id, changed_at),
    CONSTRAINT fk_rental_status_histories_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_rental_status_histories_members FOREIGN KEY (changed_by) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
