-- 최근 본 상품 (docs/erd.md item_view_logs, REQ-035)
-- (회원, 물품)당 한 행. 다시 보면 viewed_at만 갱신하고, 회원당 최신 recent-items.max(20)건만 남긴다
CREATE TABLE item_view_logs (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    item_id    BIGINT      NOT NULL,
    viewed_at  DATETIME(6) NOT NULL COMMENT '마지막으로 상세를 본 시각',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_view_logs_member_id_item_id (member_id, item_id),
    KEY idx_item_view_logs_member_id_viewed_at (member_id, viewed_at) COMMENT '최근 본 순 조회·20건 정리',
    KEY idx_item_view_logs_item_id (item_id),
    CONSTRAINT fk_item_view_logs_members FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_item_view_logs_items FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
