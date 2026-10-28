-- 초기 카테고리 (docs/policies.md §3). 한 번만 넣는다. 이후 변경은 관리자 기능 또는 새 마이그레이션으로
INSERT INTO categories (code, name, parent_id, sort_order, is_active, created_at, updated_at)
VALUES ('CAMPING', '캠핑·아웃도어', NULL, 1, TRUE, NOW(6), NOW(6)),
       ('TOOL', '공구·DIY', NULL, 2, TRUE, NOW(6), NOW(6)),
       ('CAMERA', '촬영·영상 장비', NULL, 3, TRUE, NOW(6), NOW(6)),
       ('BABY', '유아용품', NULL, 4, TRUE, NOW(6), NOW(6)),
       ('PARTY', '파티·행사용품', NULL, 5, TRUE, NOW(6), NOW(6)),
       ('HOBBY', '취미·시즌 용품', NULL, 6, TRUE, NOW(6), NOW(6)),
       ('APPLIANCE', '생활가전', NULL, 7, TRUE, NOW(6), NOW(6)),
       ('ETC', '기타', NULL, 8, TRUE, NOW(6), NOW(6));
