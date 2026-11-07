-- 보증금 보류·차감 (docs/erd.md payments, deduction_proposals, REQ-049)
-- payments의 damage_grade·deduction_amount·deduction_reason은 확정된 결과만 담고 확정 때 한 번만 쓴다. 협의 이력은 deduction_proposals
ALTER TABLE payments
    ADD COLUMN held_at          DATETIME(6)  NULL COMMENT '보증금 보류 시작. deposit.hold-agreement-days 관리자 확정 기준' AFTER deposit_status,
    ADD COLUMN damage_grade     VARCHAR(30)  NULL COMMENT 'DamageGrade. 확정된 차감만' AFTER needs_admin_check,
    ADD COLUMN deduction_amount INT          NULL COMMENT '확정된 손상 차감액' AFTER damage_grade,
    ADD COLUMN deduction_reason VARCHAR(500) NULL COMMENT '확정된 차감 사유 (deduction.reason.max 500자)' AFTER deduction_amount,
    ADD INDEX idx_payments_deposit_status_held_at (deposit_status, held_at);

CREATE TABLE deduction_proposals (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id     BIGINT       NOT NULL,
    proposed_by   BIGINT       NOT NULL COMMENT '제안한 제공자. ADMIN_DECIDED면 확정한 관리자',
    grade         VARCHAR(30)  NOT NULL COMMENT 'DamageGrade (B/C/D)',
    amount        INT          NOT NULL,
    reason        VARCHAR(500) NOT NULL COMMENT 'deduction.reason.max 500자',
    evidence_urls JSON         NOT NULL COMMENT '손상 증빙 사진 URL. 미반납 신고 건·관리자 확정은 빈 배열일 수 있다',
    receipt_urls  JSON         NULL COMMENT '견적서·영수증·구매 내역 사진 URL. 정책 deduction_type ACTUAL이면 필수',
    status        VARCHAR(30)  NOT NULL COMMENT 'DeductionProposalStatus',
    responded_at  DATETIME(6)  NULL COMMENT '수락·거절·자동 수락·관리자 확정 시각',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_deduction_proposals_rental_id_id (rental_id, id),
    INDEX idx_deduction_proposals_status_created_at (status, created_at),
    CONSTRAINT fk_deduction_proposals_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_deduction_proposals_members FOREIGN KEY (proposed_by) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
