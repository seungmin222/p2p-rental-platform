-- 거래 후기 (docs/erd.md reviews·review_compliments, REQ-034)
-- (대여, 작성자)당 1건. 후기는 수정·삭제하지 않아 신뢰 점수(REQ-054)를 나중에 이 데이터로 다시 계산할 수 있다
CREATE TABLE reviews (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    rental_id    BIGINT       NOT NULL,
    writer_id    BIGINT       NOT NULL,
    target_id    BIGINT       NOT NULL COMMENT '후기를 받는 상대 당사자',
    satisfaction VARCHAR(10)  NOT NULL COMMENT 'Satisfaction: BAD·GOOD·BEST',
    content      VARCHAR(300) NULL COMMENT 'review.content.max. 없으면 NULL',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_rental_id_writer_id (rental_id, writer_id),
    INDEX idx_reviews_target_id_id (target_id, id),
    CONSTRAINT fk_reviews_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id),
    CONSTRAINT fk_reviews_members_writer FOREIGN KEY (writer_id) REFERENCES members (id),
    CONSTRAINT fk_reviews_members_target FOREIGN KEY (target_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 후기의 칭찬 항목(값 컬렉션). 후기와 함께 한 번만 저장하고 바꾸지 않아 시각 컬럼을 두지 않는다
CREATE TABLE review_compliments (
    review_id BIGINT      NOT NULL,
    type      VARCHAR(20) NOT NULL COMMENT 'ComplimentType: ON_TIME·AS_DESCRIBED·CLEAN_USE',
    PRIMARY KEY (review_id, type),
    CONSTRAINT fk_review_compliments_reviews FOREIGN KEY (review_id) REFERENCES reviews (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
