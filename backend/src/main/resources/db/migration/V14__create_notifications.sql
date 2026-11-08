-- 거래 활동 알림 (docs/erd.md notifications, REQ-032·031)
-- 중복 방지는 (member_id, event_id) 유니크다. OVERDUE·AI_COMPARE_DONE처럼 양측에 같은 event_id를 보내는 알림이 있어 event_id만으로는 막을 수 없다(open-issues #49)
CREATE TABLE notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    member_id   BIGINT       NOT NULL COMMENT '받는 회원',
    type        VARCHAR(40)  NOT NULL COMMENT 'NotificationType',
    content     VARCHAR(200) NOT NULL COMMENT '표시 문구 (type별 템플릿)',
    target_type VARCHAR(20)  NOT NULL COMMENT 'RENTAL·ITEM·CHAT_ROOM·REVIEW',
    target_id   BIGINT       NOT NULL COMMENT '클릭 시 이동할 대상 ID',
    event_id    VARCHAR(100) NOT NULL COMMENT '{TYPE}:{targetId}[:{추가키}]',
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notifications_member_id_event_id (member_id, event_id),
    INDEX idx_notifications_member_id_id (member_id, id),
    INDEX idx_notifications_member_id_is_read (member_id, is_read),
    CONSTRAINT fk_notifications_members FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
