# chore/concurrency-lock-experiment 결정 기록

## 요약
- API 변경 없음. 새 파일: `global/lock/experiment/`(ExperimentLockMode·ExperimentRentalLockStrategy, `app.experiment.rental-lock.mode`일 때만), 테스트 `ExperimentRentalLockStrategyIntegrationTest`, `infra/docker-compose.load-lock.yml`, `scripts/load/concurrency/`(schema·reset·check SQL, run.sh·run-all.sh), 결과 `docs/experiments/concurrency.md`. `reserve-contention.js`에 `PATTERN`, B·C에 `LABEL`.
- 결과: 기본 경로(Redisson + READ COMMITTED) 중복 0·교착 0·기타 실패 0 → 1순위 완료 판정 근거. 락 없음·RC 범위 잠금은 중복, 낙관적·RR 범위 잠금·RR UK는 교착·잘못된 거절.
- 물품 행 `FOR UPDATE`(P1)는 정확하고 경합 p95 약 40% 짧았다. 기본 전략은 유지(사용자 결정), P1은 후보로만 남겼다(대기 중 커넥션 점유 위험은 미측정).
- 남은 일: 장애 주입(§8-2)에서 `rental_dates` UK 2차 방어 판단(채택 시 V23 + 취소·만료 시 삭제), P1로 바꾼다면 인기 물품 경합 + 다른 API 혼합 부하로 커넥션 풀 영향 측정.

## 결정
- **D-concurrency-lock-experiment-01** [사용자] 구성은 격리 수준 RC·RR × {N0 락 없음, O 낙관적, P1 물품 행 FOR UPDATE, P2 활성 대여 범위 FOR UPDATE, R Redisson, U rental_dates UK, RU Redisson+UK} 14개와 낙관적 락 재시도 2회(O2) 2개로 하고, 반복은 C1 3회·나머지 1회, 동시 100명으로 한다. 사용자가 P2 포함·O2는 C3에서만·반복 횟수를 골랐다.
  - RC×R은 실험 래퍼 없이 실제 기본 빈으로 쟀다(운영과 같은 경로가 기준선). C3만 40명: 오늘+200일부터 시드 예약과 겹쳐서.
- **D-concurrency-lock-experiment-02** [에이전트] 격리 수준은 실험 전략이 TransactionTemplate(격리 수준 지정)으로 바깥 트랜잭션을 먼저 열어 바꾸고, 안쪽 `RentalReservationTxService.create`가 REQUIRED로 합류하게 한다. 안쪽이 READ_COMMITTED로 고정돼 Hikari·DB 전역 설정은 덮이기 때문이다.
  - 합류 시 바깥 격리가 쓰이는 것은 통합 테스트(`@@transaction_isolation` = REPEATABLE-READ)로 확인했다.
- **D-concurrency-lock-experiment-03** [사용자] 실험 코드는 main `global/lock/experiment/`에 두고 `app.experiment.rental-lock.mode`가 있을 때만 빈을 만들어 @Primary로 기본 전략을 대신한다. 부하용 이미지가 main에서 빌드되므로 재현하려면 main에 있어야 하고, 기본 경로 코드는 바꾸지 않는다.
  - 값이 빈 문자열이어도 ConditionalOnProperty가 켜지므로 compose 덮어쓰기(`docker-compose.load-lock.yml`)에서 `LOCK_MODE`를 필수(`:?`)로 했다.
- **D-concurrency-lock-experiment-04** [에이전트] 실험 스키마(items.version, rental_dates)는 부하 DB에만 `scripts/load/concurrency/schema.sql`로 만들고 운영 Flyway·엔티티에 넣지 않는다. 채택 전 실험용이고, 엔티티에 매핑하지 않으면 ddl validate와 무관하다. 쓰기는 JdbcTemplate.
- **D-concurrency-lock-experiment-05** [에이전트] U의 rental_dates INSERT는 실험 전략 안에서 한다(task 결과가 Rental이면 날짜별 INSERT, UK 위반은 409 RENTAL_PERIOD_CONFLICT). 예약 트랜잭션 서비스를 고치지 않으려는 것이다.
- **D-concurrency-lock-experiment-06** [에이전트] P1은 물품 행을 트랜잭션 첫 문장으로 잠그고, P2는 겹침 검사가 읽는 범위(그 물품의 활성 대여)를 날짜 조건 없이 FOR UPDATE한다. RR의 스냅샷은 첫 일반 SELECT 때 생기므로 P1은 락 뒤에 만들어져야 앞 커밋이 보이고, P2는 가장 단순한 "읽는 행 잠그기"를 보이려는 것이다.
- **D-concurrency-lock-experiment-07** [에이전트] 비관적 락 대기는 rental.lock-wait(3초)를 `SET SESSION innodb_lock_wait_timeout`으로 맞추고, 1205는 409 RENTAL_LOCK_TIMEOUT, 교착(1213)은 409 COMMON_CONCURRENT_UPDATE로 바꾼다. 정책값을 새로 만들지 않고 기존 에러 코드를 쓰기 위해서다.
  - 교착 수는 `INNODB_METRICS lock_deadlocks` 전후 차이로 센다(PROCESS 권한이라 root로 조회).
- **D-concurrency-lock-experiment-08** [에이전트] O의 version은 물품 행에 두고 충돌은 409 COMMON_CONCURRENT_UPDATE(기존 코드)로 돌려준다. 겹침 판단 단위가 물품이라서다.
  - rentals INSERT의 FK 검사가 물품 행에 공유 락을 걸어, 동시에 같은 물품 행을 UPDATE하면 교착이 난다. 낙관적 락에서는 교착도 충돌로 보고 같은 재시도 규칙을 따른다.
- **D-concurrency-lock-experiment-09** [에이전트] 부하 도구는 JMeter 대신 k6를 쓰고 architecture.md 스택 표·§8-1을 k6로 고친다. load-test 환경·시드·경합 스크립트를 그대로 쓰고 docker만으로 돌기 때문이다.
- **D-concurrency-lock-experiment-10** [에이전트] k6 C 스크립트는 새 파일 대신 `reserve-contention.js`에 `PATTERN`(same·partial·disjoint)을 더한다. 기본값이 기존 동작 그대로라 load-test 재현 명령이 바뀌지 않는다.
- **D-concurrency-lock-experiment-11** [사용자] 기본 전략은 Redisson + READ COMMITTED로 유지하고, P1(물품 행 FOR UPDATE)은 결과 문서에 후보로만 남긴다. P1이 경합 지연·처리량에서 앞섰지만 대기 중 DB 커넥션을 쥐는 영향을 재지 않았고, 측정이 한 PC라 Redis 왕복 비용이 작게 나왔다. 커넥션 풀 영향 측정은 남은 일로 둔다.
- **D-concurrency-lock-experiment-12** [에이전트] 부하 DB는 query-index 실험 뒤 상태(물품 30만·대여 10만은 물품 1..30000·버퍼 풀 128MB)를 그대로 쓴다. 예약 경로는 물품 1..30000만 건드려 다시 시드할 이유가 없었다.
