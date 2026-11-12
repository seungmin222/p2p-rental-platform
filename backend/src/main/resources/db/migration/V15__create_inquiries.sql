-- 문의·분쟁 접수 (docs/erd.md inquiries, REQ-063)
-- 분쟁(DISPUTE)은 대여에 연결하고, 접수 시 그 대여의 정산을 보류(HELD)한다. 처리(IN_PROGRESS·RESOLVED)는 관리자 기능(REQ-057)
CREATE TABLE inquiries (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    member_id  BIGINT        NOT NULL COMMENT '접수한 회원',
    rental_id  BIGINT        NULL COMMENT '연결한 대여. 분쟁이면 필수',
    type       VARCHAR(20)   NOT NULL COMMENT 'InquiryType: GENERAL·DISPUTE',
    content    VARCHAR(1000) NOT NULL COMMENT 'inquiry.content-max',
    status     VARCHAR(20)   NOT NULL COMMENT 'InquiryStatus: RECEIVED·IN_PROGRESS·RESOLVED',
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_inquiries_member_id_id (member_id, id),
    INDEX idx_inquiries_rental_id_status (rental_id, status),
    CONSTRAINT fk_inquiries_members FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_inquiries_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
