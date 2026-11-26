-- 쓰기 비용: 물품 1만 건 INSERT(기존 1..10000 복사) 시간과 items 인덱스 크기. 트랜잭션을 ROLLBACK해 데이터는 남기지 않는다.
-- rental_load 스키마에서만 실행한다. 사용: mysql ... rental_load < scripts/load/query-index/write-cost.sql
-- 다른 스키마면 서브쿼리가 두 행을 돌려 오류로 멈춘다(mysql 클라이언트를 --force 없이 실행할 때)
SET @guard = (SELECT IF(IFNULL(DATABASE(), '') = 'rental_load', 1, (SELECT 1 UNION SELECT 2)));
SET @base = (SELECT MAX(id) FROM items) + 1000000;
START TRANSACTION;
SET @t0 = NOW(6);
INSERT INTO items (id, owner_id, region_id, category_id, name, description, item_condition, price_per_day, deposit,
                   item_price, address, latitude, longitude, status, view_count, wish_count, chat_count, bumped_at,
                   created_at, updated_at)
SELECT @base + id, owner_id, region_id, category_id, name, description, item_condition, price_per_day, deposit,
       item_price, address, latitude, longitude, status, view_count, wish_count, chat_count, bumped_at, created_at,
       updated_at
FROM items WHERE id <= 10000;
SELECT TIMESTAMPDIFF(MICROSECOND, @t0, NOW(6)) / 1000 AS insert_10k_ms;
ROLLBACK;
SELECT index_name, ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS size_mb
FROM mysql.innodb_index_stats
WHERE database_name = 'rental_load' AND table_name = 'items' AND stat_name = 'size'
ORDER BY index_name;
