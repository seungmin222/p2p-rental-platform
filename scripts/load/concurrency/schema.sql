-- 락 전략 비교(docs/experiments/concurrency.md) 전용 스키마. 부하 DB(rental_load)에만 한 번 적용한다. 운영 Flyway 마이그레이션이 아니다.
--   OPTIMISTIC: items.version (물품 단위 낙관적 락)
--   UNIQUE·REDISSON_UNIQUE: rental_dates (날짜별 점유, docs/erd.md "rental_dates")
-- 실행: docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental rental_load < scripts/load/concurrency/schema.sql
-- 엔티티에 매핑하지 않으므로 앱의 ddl-auto validate와 무관하다. 다시 적용하면 ALTER가 "Duplicate column"으로 멈춘다(이미 적용됨).
DELIMITER //
CREATE PROCEDURE assert_load_schema()
BEGIN
    IF DATABASE() IS NULL OR DATABASE() <> 'rental_load' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'concurrency/schema.sql은 rental_load 스키마에서만 실행한다';
    END IF;
END //
DELIMITER ;
CALL assert_load_schema();
DROP PROCEDURE assert_load_schema;

ALTER TABLE items ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '락 전략 비교 실험 전용(낙관적 락)';

CREATE TABLE rental_dates (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    item_id     BIGINT NOT NULL,
    rented_date DATE   NOT NULL,
    rental_id   BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rental_dates_item_id_rented_date (item_id, rented_date),
    INDEX idx_rental_dates_rental_id (rental_id),
    CONSTRAINT fk_rental_dates_rentals FOREIGN KEY (rental_id) REFERENCES rentals (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
