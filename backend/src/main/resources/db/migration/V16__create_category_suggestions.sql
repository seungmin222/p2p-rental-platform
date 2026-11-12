-- 카테고리 후보 (docs/erd.md category_suggestions, REQ-067)
-- AI 카테고리 추천이 목록에 없는 물품이라 ETC를 고르면 후보 이름을 PENDING으로 남긴다. 추천은 등록 전이라 item_id가 없다(open-issues #52)
-- 검토(APPROVED·REJECTED, reviewed_by)는 관리자 기능(REQ-057)
CREATE TABLE category_suggestions (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    item_id        BIGINT      NULL COMMENT '연결된 물품. AI 추천 시점에는 없다',
    member_id      BIGINT      NOT NULL COMMENT '추천을 요청한 회원',
    suggested_name VARCHAR(30) NOT NULL COMMENT 'AI가 낸 후보 이름. categories.name과 같은 길이',
    status         VARCHAR(20) NOT NULL COMMENT 'CategorySuggestionStatus: PENDING·APPROVED·REJECTED',
    reviewed_by    BIGINT      NULL COMMENT '검토한 관리자',
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_category_suggestions_status_id (status, id),
    CONSTRAINT fk_category_suggestions_items FOREIGN KEY (item_id) REFERENCES items (id),
    CONSTRAINT fk_category_suggestions_members FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_category_suggestions_reviewers FOREIGN KEY (reviewed_by) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
