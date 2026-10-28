-- 회원·동네 (docs/erd.md 회원). 엔티티·마이그레이션만 먼저 만든다(feat/member-item-entity). API는 REQ-001~005

CREATE TABLE members (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email             VARCHAR(100) NOT NULL,
    password          VARCHAR(100) NULL COMMENT 'BCrypt. 소셜 전용 계정이면 NULL',
    nickname          VARCHAR(10)  NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    provider          VARCHAR(30)  NOT NULL COMMENT 'AuthProvider',
    trust_score       DECIMAL(4,1) NOT NULL DEFAULT 50.0 COMMENT '집계 캐시(REQ-054 구현 시 trust_score_events에서 갱신)',
    role              VARCHAR(30)  NOT NULL COMMENT 'MemberRole',
    status            VARCHAR(30)  NOT NULL COMMENT 'MemberStatus',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_members_email UNIQUE (email),
    CONSTRAINT uk_members_nickname UNIQUE (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 행정동 마스터. 데이터 적재는 REQ-004에서 별도 마이그레이션으로 한다
CREATE TABLE regions (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    code       VARCHAR(10) NOT NULL COMMENT '행정동 코드(10자리)',
    name       VARCHAR(30) NOT NULL,
    sido       VARCHAR(20) NOT NULL,
    sigungu    VARCHAR(30) NOT NULL,
    center_lat DOUBLE      NOT NULL,
    center_lng DOUBLE      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_regions_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 회원당 최대 2행·대표 1행은 애플리케이션 검증(REQ-004)
CREATE TABLE member_regions (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    member_id    BIGINT      NOT NULL,
    region_id    BIGINT      NOT NULL,
    is_primary   BOOLEAN     NOT NULL,
    verified_at  DATETIME(6) NULL COMMENT '인증 유효 = verified_at + region.verify-valid-days > now',
    region_range VARCHAR(30) NOT NULL DEFAULT 'NORMAL' COMMENT 'RegionRange',
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_regions_member_id_region_id UNIQUE (member_id, region_id),
    CONSTRAINT fk_member_regions_members FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_member_regions_regions FOREIGN KEY (region_id) REFERENCES regions (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
