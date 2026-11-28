-- 구성 하나를 재기 전에 부하로 생긴 예약을 지운다(시드 대여 id는 1..100000). 교착 수는 이 출력과 check.sql 출력의 차이로 센다.
-- 실행(교착 지표는 PROCESS 권한이 필요해 root로): docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -uroot -proot rental_load < scripts/load/concurrency/reset.sql
DELETE FROM rental_dates;
DELETE FROM rental_status_histories WHERE rental_id > 100000;
DELETE FROM rentals WHERE id > 100000;
SELECT COUNT AS deadlocks_before FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks';
