-- 양측 동의 취소 요청 (docs/erd.md rental_cancel_agreements, REQ-015)
-- 한 대여에 진행 중(REQUESTED이고 expires_at > now)인 요청은 1건만 — 대여 행 락 안에서 애플리케이션이 검증한다
CREATE TABLE rental_cancel_agreements (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id    BIGINT       NOT NULL,
    requested_by BIGINT       NOT NULL,
    reason       VARCHAR(30)  NOT NULL COMMENT 'CancelReason',
    reason_text  VARCHAR(200) NULL COMMENT 'policies.md cancel.reason-text-max 200자. reason OTHER면 필수',
    status       VARCHAR(30)  NOT NULL COMMENT 'CancelAgreementStatus',
    expires_at   DATETIME(6)  NOT NULL COMMENT '요청 시각 + cancel-agreement.expire-hours',
    responded_at DATETIME(6)  NULL COMMENT '수락·거절 시각. 만료면 NULL',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_rental_cancel_agreements_rental_id_status (rental_id, status),
    INDEX idx_rental_cancel_agreements_status_expires_at (status, expires_at),
    CONSTRAINT fk_rental_cancel_agreements_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_rental_cancel_agreements_members FOREIGN KEY (requested_by) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
