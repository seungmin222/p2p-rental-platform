-- 부하 테스트 더미 데이터 (docs/architecture.md §7: 회원 1만·물품 3만·대여 10만). 사용법: scripts/load/README.md
-- 대상은 infra/docker-compose.load.yml의 rental_load 스키마뿐이다. 다른 DB이거나 이미 회원이 있으면 아무것도 넣지 않고 멈춘다.
-- 앱(Flyway)이 한 번 기동해 테이블·카테고리·행정동을 만든 뒤 실행한다. 난수 대신 id의 CRC32를 써서 매번 같은 데이터가 나온다.
-- k6 스크립트(lib.js)가 아래 규칙을 그대로 따라 쓴다. 규칙을 바꾸면 lib.js도 같이 바꾼다:
--   회원 id 1..10000, 이메일 load{id}@load.test, 비밀번호 loadtest123! (테스트 전용), 대표 동네 인증 완료
--   물품 id 1..30000, 소유자 = (id*7 % 10000) + 1, 상태 DELETED(id%50=0)·HIDDEN(id%50=1)·그 밖 AVAILABLE
--   대여 가능 기간 = 오늘-400일 ~ 오늘+240일 (물품당 1개)

DROP PROCEDURE IF EXISTS load_seed_guard;
DELIMITER //
CREATE PROCEDURE load_seed_guard()
BEGIN
    IF DATABASE() <> 'rental_load' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'seed.sql은 rental_load 스키마에서만 실행한다';
    END IF;
    IF (SELECT COUNT(*) FROM members) > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '회원이 이미 있다. docker compose -f infra/docker-compose.load.yml down -v 후 다시 시작한다';
    END IF;
    IF (SELECT COUNT(*) FROM regions WHERE sido = '서울특별시') = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '행정동이 없다. 앱을 먼저 기동해 Flyway를 끝낸다';
    END IF;
END //
DELIMITER ;
CALL load_seed_guard();
DROP PROCEDURE load_seed_guard;

SET @now = NOW(6);
SET @today = CURDATE();
-- 모든 더미 회원이 같은 BCrypt(강도 10) 해시를 쓴다. 평문 loadtest123! (부하 테스트 전용 값)
SET @pw = '$2a$10$IUw.iMd7g266226X4p2sZOxKpvbWUQRqfvYwnxbJ7BE0GmoPqqN/u';

-- 1..100000 숫자 테이블
DROP TEMPORARY TABLE IF EXISTS seq;
CREATE TEMPORARY TABLE seq (n INT PRIMARY KEY);
INSERT INTO seq (n)
SELECT a.d + b.d * 10 + c.d * 100 + e.d * 1000 + f.d * 10000 + 1
FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e,
     (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f;

-- 서울 행정동에 번호(rn)를 붙인다. rn 1..10은 회원 20%가 몰리는 밀집 동네(종로구 일대, id 순 앞쪽)
DROP TEMPORARY TABLE IF EXISTS seed_regions;
CREATE TEMPORARY TABLE seed_regions (rn INT PRIMARY KEY, region_id BIGINT, lat DOUBLE, lng DOUBLE);
INSERT INTO seed_regions
SELECT ROW_NUMBER() OVER (ORDER BY id), id, center_lat, center_lng FROM regions WHERE sido = '서울특별시';
SET @region_count = (SELECT COUNT(*) FROM seed_regions);

-- 회원 1만
INSERT INTO members (id, email, password, nickname, profile_image_url, provider, trust_score, role, status, created_at, updated_at)
SELECT n, CONCAT('load', n, '@load.test'), @pw, CONCAT('load', n), NULL, 'LOCAL', 50.0, 'USER', 'ACTIVE',
       @now - INTERVAL 120 DAY, @now - INTERVAL 120 DAY
FROM seq WHERE n <= 10000;

-- 대표 동네(인증 완료, 유효 30일 안)
DROP TEMPORARY TABLE IF EXISTS member_rn;
CREATE TEMPORARY TABLE member_rn (member_id BIGINT PRIMARY KEY, rn INT);
INSERT INTO member_rn
SELECT n, IF(n % 5 = 0, 1 + (n DIV 5) % 10, 1 + CRC32(CONCAT('r', n)) % @region_count) FROM seq WHERE n <= 10000;

INSERT INTO member_regions (member_id, region_id, is_primary, verified_at, region_range, created_at, updated_at)
SELECT m.member_id, r.region_id, TRUE, @now - INTERVAL 1 DAY, 'NORMAL', @now - INTERVAL 120 DAY, @now - INTERVAL 1 DAY
FROM member_rn m JOIN seed_regions r ON r.rn = m.rn;

-- 약관 동의는 넣지 않는다(로그인 응답의 requiresTermsAgreement=true일 뿐 이번 대상 API를 막지 않는다)

-- 물품 3만: 소유자 대표 동네 중심에서 위도 ±약 1km·경도 ±약 1km 안에 흩뿌린다
INSERT INTO items (id, owner_id, region_id, category_id, name, description, item_condition, price_per_day, deposit,
                   item_price, address, latitude, longitude, status, view_count, wish_count, chat_count, bumped_at,
                   created_at, updated_at)
SELECT s.n,
       o.member_id,
       r.region_id,
       c.id,
       CONCAT(ELT((s.n % 8) * 5 + (s.n DIV 8) % 5 + 1,
                  '텐트', '캠핑의자', '타프', '랜턴', '버너',
                  '전동드릴', '사다리', '공구세트', '그라인더', '수평계',
                  '미러리스 카메라', '삼각대', '액션캠', '짐벌', '촬영 조명',
                  '유모차', '카시트', '아기띠', '보행기', '바운서',
                  '풍선 세트', '블루투스 스피커', '빔프로젝터', '파티 텐트', '접이식 테이블',
                  '보드게임', '스키', '스노보드', '낚싯대', '자전거',
                  '제습기', '공기청정기', '스팀다리미', '무선청소기', '서큘레이터',
                  '캐리어', '한복', '정장', '여행 가방', '접이식 카트'), ' ', s.n),
       '깨끗하게 사용한 물품입니다. 동네에서 직거래로 빌려 드려요. 사용 후 상태 그대로 반납 부탁드립니다.',
       ELT(1 + s.n % 3, 'LIKE_NEW', 'LIGHTLY_USED', 'USED'),
       (3 + CRC32(CONCAT('p', s.n)) % 20) * 1000,
       (3 + CRC32(CONCAT('p', s.n)) % 20) * 10000,
       (3 + CRC32(CONCAT('p', s.n)) % 20) * 40000,
       '서울특별시 거래 장소',
       r.lat + (CAST(CRC32(CONCAT('a', s.n)) % 2001 AS SIGNED) - 1000) / 1000 * 0.009,
       r.lng + (CAST(CRC32(CONCAT('o', s.n)) % 2001 AS SIGNED) - 1000) / 1000 * 0.0113,
       CASE s.n % 50 WHEN 0 THEN 'DELETED' WHEN 1 THEN 'HIDDEN' ELSE 'AVAILABLE' END,
       CRC32(CONCAT('v', s.n)) % 300, 0, 0,
       @now - INTERVAL (CRC32(CONCAT('b', s.n)) % (90 * 24 * 60)) MINUTE,
       @now - INTERVAL 100 DAY, @now - INTERVAL 100 DAY
FROM seq s
JOIN member_rn o ON o.member_id = (s.n * 7) % 10000 + 1
JOIN seed_regions r ON r.rn = o.rn
JOIN categories c ON c.sort_order = s.n % 8 + 1
WHERE s.n <= 30000;

INSERT INTO item_images (item_id, url, thumbnail_url, sort_order, created_at, updated_at)
SELECT n, '/files/load/item.jpg', '/files/load/item-thumb.jpg', 0, @now - INTERVAL 100 DAY, @now - INTERVAL 100 DAY
FROM seq WHERE n <= 30000;

INSERT INTO item_available_periods (item_id, start_date, end_date, created_at, updated_at)
SELECT n, @today - INTERVAL 400 DAY, @today + INTERVAL 240 DAY, @now - INTERVAL 100 DAY, @now - INTERVAL 100 DAY
FROM seq WHERE n <= 30000;

-- 대여 10만. k = 1..100000, 물품 = (k-1) % 30000 + 1, 같은 물품 안에서 겹치지 않게 80일 간격 슬롯을 쓴다
--   k  1..90000 : 과거(오늘-30일보다 앞) COMPLETED, k%12=0이면 CANCELED
--   k 90001..93000 : 진행 중 RENTING (물품 1..3000, 어제 ~ 오늘+1~5일) — 피드 "대여 중" 배지·기간 검색 점유
--   k 93001..95000 : 과거 COMPLETED (물품 3001..5000)
--   k 95001..100000 : 미래 RESERVED (물품 6,12,..,30000, 오늘+200~229일) — 시나리오 B·C 날짜(오늘+1~170일)와 겹치지 않는다
DROP TEMPORARY TABLE IF EXISTS rental_plan;
CREATE TEMPORARY TABLE rental_plan (k INT PRIMARY KEY, item_id BIGINT, start_date DATE, days INT, status VARCHAR(30));
INSERT INTO rental_plan
SELECT n,
       IF(n > 95000, (n - 95000) * 6, (n - 1) % 30000 + 1),
       CASE
           WHEN n > 95000 THEN @today + INTERVAL (200 + ((n - 95000) * 6) % 30) DAY
           WHEN n BETWEEN 90001 AND 93000 THEN @today - INTERVAL 1 DAY
           ELSE @today - INTERVAL (30 + ((n - 1) DIV 30000) * 80 + ((n - 1) % 30000 + 1) % 30 + 5) DAY
       END,
       1 + n % 5,
       CASE
           WHEN n > 95000 THEN 'RESERVED'
           WHEN n BETWEEN 90001 AND 93000 THEN 'RENTING'
           WHEN n % 12 = 0 THEN 'CANCELED'
           ELSE 'COMPLETED'
       END
FROM seq;

INSERT INTO rentals (id, item_id, owner_id, renter_id, start_date, end_date, rental_fee, deposit, price_per_day, status,
                     payment_expires_at, handed_over_at, returned_at, completed_at, canceled_at, cancel_type,
                     cancel_reason, cancel_reason_text, canceled_by, cancel_fee, overdue_fee, extension_count, version,
                     created_at, updated_at)
SELECT p.k, p.item_id, i.owner_id,
       (i.owner_id - 1 + 1 + p.k % 9999) % 10000 + 1,
       p.start_date,
       IF(p.status = 'RENTING', @today + INTERVAL p.days DAY, p.start_date + INTERVAL (p.days - 1) DAY),
       i.price_per_day * IF(p.status = 'RENTING', p.days + 2, p.days), i.deposit, i.price_per_day, p.status,
       NULL,
       IF(p.status IN ('RENTING', 'COMPLETED'), TIMESTAMP(p.start_date, '10:00:00'), NULL),
       IF(p.status = 'COMPLETED', TIMESTAMP(p.start_date + INTERVAL (p.days - 1) DAY, '18:00:00'), NULL),
       IF(p.status = 'COMPLETED', TIMESTAMP(p.start_date + INTERVAL p.days DAY, '12:00:00'), NULL),
       IF(p.status = 'CANCELED', TIMESTAMP(p.start_date - INTERVAL 3 DAY, '09:00:00'), NULL),
       NULL, NULL, NULL, NULL, 0, 0, 0, 0,
       LEAST(TIMESTAMP(p.start_date - INTERVAL 7 DAY, '09:00:00'), @now - INTERVAL 1 DAY),
       LEAST(TIMESTAMP(p.start_date - INTERVAL 7 DAY, '09:00:00'), @now - INTERVAL 1 DAY)
FROM rental_plan p JOIN items i ON i.id = p.item_id;

-- 대여 생성 이력 한 줄씩(대여 상세·내역 화면과 같은 모양을 유지하려는 것. 이번 시나리오는 읽지 않는다)
INSERT INTO rental_status_histories (rental_id, from_status, to_status, changed_by, reason, changed_at, created_at, updated_at)
SELECT id, NULL, status, NULL, 'load-seed', created_at, created_at, created_at FROM rentals;

DROP TEMPORARY TABLE seq;
DROP TEMPORARY TABLE seed_regions;
DROP TEMPORARY TABLE member_rn;
DROP TEMPORARY TABLE rental_plan;

ANALYZE TABLE members, member_regions, items, item_images, item_available_periods, rentals, rental_status_histories;

SELECT (SELECT COUNT(*) FROM members) AS members, (SELECT COUNT(*) FROM items) AS items,
       (SELECT COUNT(*) FROM rentals) AS rentals,
       (SELECT COUNT(*) FROM rentals WHERE status IN ('PENDING_PAYMENT', 'RESERVED', 'RENTING', 'OVERDUE')) AS active_rentals;
