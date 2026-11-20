-- 약관 동의 이력 (docs/erd.md terms_agreements, REQ-037)
-- 동의한 약관 버전을 스냅샷으로 남긴다(conventions.md 스냅샷). 약관이 개정되면 같은 종류의 새 버전 행이 추가된다
CREATE TABLE terms_agreements (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    member_id     BIGINT      NOT NULL,
    terms_type    VARCHAR(30) NOT NULL COMMENT 'TermsAgreementType: TERMS_OF_SERVICE·PRIVACY_COLLECTION·AGE_OVER_14',
    terms_version VARCHAR(20) NOT NULL COMMENT '동의한 버전 (application.yml app.terms.*-version)',
    agreed_at     DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_terms_agreements_member_id_terms_type_terms_version (member_id, terms_type, terms_version),
    CONSTRAINT fk_terms_agreements_members FOREIGN KEY (member_id) REFERENCES members (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
