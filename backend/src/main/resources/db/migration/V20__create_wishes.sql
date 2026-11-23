-- 관심(찜) (docs/erd.md wishes, REQ-022)
-- (회원, 물품)당 한 행. 물품의 관심 수는 items.wish_count 비정규화 카운터로 함께 갱신한다
CREATE TABLE wishes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    item_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wishes_member_id_item_id (member_id, item_id),
    KEY idx_wishes_member_id_id (member_id, id) COMMENT '내 관심 목록 최근 추가순',
    KEY idx_wishes_item_id (item_id),
    CONSTRAINT fk_wishes_members FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_wishes_items FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
