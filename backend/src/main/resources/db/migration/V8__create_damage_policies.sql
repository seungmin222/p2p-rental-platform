-- docs/policies.md §4. 추천 비율은 등급별 동일하게 저장한다.
CREATE TABLE damage_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    grade VARCHAR(30) NOT NULL,
    deduction_type VARCHAR(30) NOT NULL,
    fixed_amount INT NULL,
    cap_rate DECIMAL(5,4) NULL,
    recommended_deposit_min_rate DECIMAL(5,4) NOT NULL,
    recommended_deposit_max_rate DECIMAL(5,4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_damage_policies_category_grade UNIQUE (category_id, grade),
    CONSTRAINT fk_damage_policies_categories FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_damage_policies_rates CHECK (
        recommended_deposit_min_rate >= 0 AND recommended_deposit_max_rate <= 1
        AND recommended_deposit_min_rate <= recommended_deposit_max_rate),
    CONSTRAINT ck_damage_policies_cap CHECK (cap_rate IS NULL OR cap_rate BETWEEN 0 AND 1),
    CONSTRAINT ck_damage_policies_fixed CHECK (fixed_amount IS NULL OR fixed_amount >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'CAMPING';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'FIXED', 10000, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'CAMPING';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .50, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'CAMPING';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'CAMPING';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'TOOL';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'ACTUAL', NULL, NULL, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'TOOL';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .50, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'TOOL';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'TOOL';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .50, .70, NOW(6), NOW(6) FROM categories WHERE code = 'CAMERA';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'FIXED', 10000, NULL, .50, .70, NOW(6), NOW(6) FROM categories WHERE code = 'CAMERA';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .70, .50, .70, NOW(6), NOW(6) FROM categories WHERE code = 'CAMERA';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .50, .70, NOW(6), NOW(6) FROM categories WHERE code = 'CAMERA';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'BABY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'FIXED', 20000, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'BABY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .50, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'BABY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'BABY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'PARTY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'FIXED', 10000, NULL, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'PARTY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .50, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'PARTY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .20, .30, NOW(6), NOW(6) FROM categories WHERE code = 'PARTY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'HOBBY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'FIXED', 10000, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'HOBBY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .50, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'HOBBY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .30, .50, NOW(6), NOW(6) FROM categories WHERE code = 'HOBBY';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'APPLIANCE';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'FIXED', 10000, NULL, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'APPLIANCE';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .50, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'APPLIANCE';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'APPLIANCE';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'A', 'NONE', 0, NULL, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'ETC';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'B', 'FIXED', 10000, NULL, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'ETC';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'C', 'ACTUAL', NULL, .50, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'ETC';
INSERT INTO damage_policies (category_id, grade, deduction_type, fixed_amount, cap_rate, recommended_deposit_min_rate, recommended_deposit_max_rate, created_at, updated_at)
SELECT id, 'D', 'FULL', NULL, NULL, .30, .30, NOW(6), NOW(6) FROM categories WHERE code = 'ETC';
