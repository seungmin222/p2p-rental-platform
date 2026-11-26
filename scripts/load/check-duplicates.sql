-- 예약 정합성 확인: 같은 물품에서 기간이 겹치는 활성 대여(ACTIVE_FOR_PERIOD) 쌍의 수. 목표 0
-- 실행: docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental rental_load < scripts/load/check-duplicates.sql
SELECT COUNT(*) AS overlapping_pairs
FROM rentals a
JOIN rentals b ON b.item_id = a.item_id AND b.id > a.id
    AND b.start_date <= a.end_date AND a.start_date <= b.end_date
WHERE a.status IN ('PENDING_PAYMENT', 'RESERVED', 'RENTING', 'OVERDUE')
  AND b.status IN ('PENDING_PAYMENT', 'RESERVED', 'RENTING', 'OVERDUE');

-- 부하 중 새로 생긴 예약 수(시드는 id 1..100000)
SELECT COUNT(*) AS created_by_load, COUNT(DISTINCT item_id) AS items FROM rentals WHERE id > 100000;

-- 시나리오 C 기간(오늘+160 ~ +162일)의 물품별 예약 수. 모두 1이어야 한다
SELECT COUNT(*) AS contention_items, SUM(cnt = 1) AS exactly_one, MAX(cnt) AS max_per_item
FROM (SELECT item_id, COUNT(*) AS cnt FROM rentals
      WHERE id > 100000 AND start_date = CURDATE() + INTERVAL 160 DAY
        AND status IN ('PENDING_PAYMENT', 'RESERVED', 'RENTING', 'OVERDUE')
      GROUP BY item_id) t;
