-- 구성 하나를 잰 뒤 정합성·교착 확인(락 전략 비교). overlapping_pairs = 0이 목표
-- 실행(교착 지표는 PROCESS 권한이 필요해 root로): docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -uroot -proot rental_load < scripts/load/concurrency/check.sql
SELECT COUNT(*) AS overlapping_pairs
FROM rentals a
JOIN rentals b ON b.item_id = a.item_id AND b.id > a.id
    AND b.start_date <= a.end_date AND a.start_date <= b.end_date
WHERE a.status IN ('PENDING_PAYMENT', 'RESERVED', 'RENTING', 'OVERDUE')
  AND b.status IN ('PENDING_PAYMENT', 'RESERVED', 'RENTING', 'OVERDUE');

-- 부하로 생긴 예약 수와, 시나리오 C 구간(오늘+160 ~ +199일)의 물품별 최대 예약 수
SELECT COUNT(*) AS created_by_load, COUNT(DISTINCT item_id) AS items,
       SUM(start_date >= CURDATE() + INTERVAL 160 DAY) AS contention_created
FROM rentals WHERE id > 100000;

SELECT COUNT AS deadlocks_after FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks';
