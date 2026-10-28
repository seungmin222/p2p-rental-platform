-- 카테고리·물품 (docs/erd.md 물품). 엔티티·마이그레이션만 먼저 만든다(feat/member-item-entity). API는 REQ-006~011

CREATE TABLE categories (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(30) NOT NULL COMMENT 'AI 분류 선택지로도 쓴다',
    name       VARCHAR(30) NOT NULL,
    parent_id  BIGINT      NULL COMMENT '미사용(하위 카테고리 확장용)',
    sort_order INT         NOT NULL,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '삭제 대신 false',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_code UNIQUE (code),
    CONSTRAINT fk_categories_categories FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 위치(lat·lng)·키워드(FULLTEXT) 인덱스는 피드·검색 실험(도전 과제 ④)에서 추가한다
CREATE TABLE items (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id       BIGINT       NOT NULL,
    region_id      BIGINT       NOT NULL COMMENT '등록 시 대표 동네',
    category_id    BIGINT       NOT NULL,
    name           VARCHAR(50)  NOT NULL,
    description    TEXT         NOT NULL,
    item_condition VARCHAR(30)  NOT NULL COMMENT 'ItemCondition',
    price_per_day  INT          NOT NULL,
    deposit        INT          NOT NULL,
    item_price     INT          NOT NULL COMMENT '비공개. 권장 보증금·D 등급 판단용',
    address        VARCHAR(200) NOT NULL COMMENT '거래 장소',
    latitude       DOUBLE       NOT NULL,
    longitude      DOUBLE       NOT NULL,
    status         VARCHAR(30)  NOT NULL COMMENT 'ItemStatus',
    view_count     INT          NOT NULL DEFAULT 0,
    wish_count     INT          NOT NULL DEFAULT 0,
    chat_count     INT          NOT NULL DEFAULT 0,
    bumped_at      DATETIME(6)  NOT NULL COMMENT '피드 정렬 키. 등록 시 created_at과 같게',
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_items_status_bumped_at_id (status, bumped_at DESC, id DESC),
    INDEX idx_items_owner_id_status (owner_id, status),
    INDEX idx_items_category_id_status (category_id, status),
    CONSTRAINT fk_items_members FOREIGN KEY (owner_id) REFERENCES members (id),
    CONSTRAINT fk_items_regions FOREIGN KEY (region_id) REFERENCES regions (id),
    CONSTRAINT fk_items_categories FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE item_images (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    item_id       BIGINT       NOT NULL,
    url           VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    sort_order    INT          NOT NULL COMMENT '0이 대표 이미지',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_item_images_items FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 같은 물품 안의 구간 겹침 금지는 애플리케이션 검증(REQ-006·007)
CREATE TABLE item_available_periods (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    item_id    BIGINT      NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_item_available_periods_item_id_start_date (item_id, start_date),
    CONSTRAINT fk_item_available_periods_items FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
