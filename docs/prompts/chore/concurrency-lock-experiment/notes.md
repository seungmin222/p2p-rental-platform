# chore/concurrency-lock-experiment 결정 기록

## 요약
- API 변경 없음. 새 파일: `global/lock/experiment/`(ExperimentLockMode·ExperimentRentalLockStrategy, `app.experiment.rental-lock.mode`일 때만), 테스트 `ExperimentRentalLockStrategyIntegrationTest`, `infra/docker-compose.load-lock.yml`, `scripts/load/concurrency/`(schema·reset·check SQL, run.sh·run-all.sh), 결과 `docs/experiments/concurrency.md`. `reserve-contention.js`에 `PATTERN`, B·C에 `LABEL`.
- 결과: 기본 경로(Redisson + READ COMMITTED) 중복 0·교착 0·기타 실패 0 → 1순위 완료 판정 근거. 락 없음·RC 범위 잠금은 중복, 낙관적·RR 범위 잠금·RR UK는 교착·잘못된 거절.
- 물품 행 `FOR UPDATE`(P1)는 정확하고 경합 p95 약 40% 짧았다. 기본 전략은 유지(사용자 결정), P1은 후보로만 남겼다(대기 중 커넥션 점유 위험은 미측정).
- 남은 일: 장애 주입(§8-2)에서 `rental_dates` UK 2차 방어 판단(채택 시 V23 + 취소·만료 시 삭제), P1로 바꾼다면 인기 물품 경합 + 다른 API 혼합 부하로 커넥션 풀 영향 측정.

## 결정
- **구성 14개 + 낙관적 락 재시도 2회 2개(사용자 승인)**: 격리 수준 RC·RR × {N0 락 없음, O 낙관적, P1 물품 행 FOR UPDATE, P2 활성 대여 범위 FOR UPDATE, R Redisson, U rental_dates UK, RU Redisson+UK}. RC×R은 실험 래퍼 없이 실제 기본 빈으로 쟀다(운영과 같은 경로가 기준선). O2(재시도 2회)는 C3(겹치지 않는 기간)에서만 쟀다. 반복은 C1 3회, 나머지 1회, 동시 100명(C3만 40명: 오늘+200일부터 시드 예약과 겹쳐서).
- **격리 수준은 바깥 트랜잭션으로 바꾼다**: `RentalReservationTxService.create`가 READ_COMMITTED로 고정돼 Hikari·DB 전역 설정은 덮인다. 실험 전략이 TransactionTemplate(격리 수준 지정)으로 먼저 열고 안쪽이 REQUIRED로 합류한다. 합류 시 바깥 격리가 쓰이는 것은 통합 테스트(`@@transaction_isolation` = REPEATABLE-READ)로 확인했다.
- **실험 코드는 main `global/lock/experiment/`, `app.experiment.rental-lock.mode`가 있을 때만 빈 생성 + @Primary(사용자 승인)**: 부하용 이미지가 main에서 빌드되므로 재현하려면 main에 있어야 한다. 기본 경로 코드는 바꾸지 않았다. 값이 빈 문자열이어도 ConditionalOnProperty가 켜지므로 compose 덮어쓰기(`docker-compose.load-lock.yml`)에서 `LOCK_MODE`를 필수(`:?`)로 했다.
- **실험 스키마(items.version, rental_dates)는 부하 DB에만 `scripts/load/concurrency/schema.sql`**: 운영 Flyway에 넣지 않고 엔티티에도 매핑하지 않는다(ddl validate 무관). 쓰기는 JdbcTemplate.
- **U의 rental_dates INSERT는 실험 전략 안에서**: task 결과가 Rental이면 날짜별로 INSERT, UK 위반은 409 RENTAL_PERIOD_CONFLICT. 예약 트랜잭션 서비스를 고치지 않으려는 것.
- **P1은 물품 행을 트랜잭션 첫 문장으로 잠근다**: RR의 스냅샷은 첫 일반 SELECT 때 생기므로 락 뒤에 만들어져야 앞 커밋이 보인다. P2는 겹침 검사가 읽는 범위(그 물품의 활성 대여)를 FOR UPDATE — 날짜 조건은 넣지 않았다(가장 단순한 "읽는 행 잠그기").
- **비관적 락 대기는 rental.lock-wait(3초)를 `SET SESSION innodb_lock_wait_timeout`으로**: 1205는 409 RENTAL_LOCK_TIMEOUT. 교착(1213)은 409 COMMON_CONCURRENT_UPDATE로 바꾸고, 수는 `INNODB_METRICS lock_deadlocks` 전후 차이로 센다(PROCESS 권한이라 root로 조회).
- **O의 version은 물품 행, 충돌은 409 COMMON_CONCURRENT_UPDATE(기존 코드)**: 겹침 판단 단위가 물품이라서. rentals INSERT의 FK 검사가 물품 행에 공유 락을 걸어, 동시에 같은 물품 행을 UPDATE하면 교착이 난다. 낙관적 락에서는 교착도 충돌로 보고 같은 재시도 규칙을 따른다.
- **도구는 k6(JMeter 대신)**: load-test 환경·시드·경합 스크립트를 그대로 쓰고 docker만으로 돈다. architecture.md 스택 표·§8-1을 k6로 고쳤다.
- k6 C 스크립트는 새 파일 대신 `reserve-contention.js`에 `PATTERN`(same·partial·disjoint)을 더했다. 기본값은 기존 동작 그대로.
- **기본 전략 유지(사용자 결정)**: Redisson + READ COMMITTED. P1(물품 행 FOR UPDATE)이 경합 지연·처리량에서 앞섰지만, 대기 중 DB 커넥션을 쥐는 영향을 재지 않았고 측정이 한 PC라 Redis 왕복 비용이 작게 나왔다. P1은 후보로 결과 문서에 남기고, 커넥션 풀 영향 측정을 남은 일로 둔다.
- 부하 DB는 query-index 실험 뒤라 물품 30만(대여 10만은 물품 1..30000)·버퍼 풀 128MB 상태 그대로 썼다. 예약 경로는 물품 1..30000만 건드린다.
