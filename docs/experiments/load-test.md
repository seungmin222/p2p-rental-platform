# 부하 테스트 (14주차, `chore/load-test`)

`docs/architecture.md` §7 목표 성능 중 조회·예약 응답 시간, 동시 사용자, 예약 정합성을 로컬 docker 2인스턴스에서 k6로 쟀다. 도구와 재현 방법은 `scripts/load/README.md`. **아래 수치는 모두 2026-11-26에 실제로 돌린 값이다.**

## 요약

| 지표 | 목표 | 결과 | 판정 |
| --- | --- | --- | --- |
| 조회 API(피드·검색·상세) p95 | ≤ 500ms | 100명: 피드 9.7 · 검색 102.6 · 상세 9.4ms / 200명: 10.1 · 122.2 · 9.5ms | 충족 |
| 예약 API p95 | ≤ 1s | 100명: 23.3ms / 200명: 26.5ms, 단 p99 30.0s·실패 1.30% → **개선 후 200명: p95 17.4ms·p99 38.1ms·실패 0** | 개선 후 충족 |
| 동시 사용자 100명+에서 검색·예약 정상 | 정상 | 검색: 200명까지 오류 0 / 예약: 100명 오류 0, 200명에서 5분째 멈춤(아래 "병목") → **개선 후 200명 오류 0** | 개선 후 충족 |
| 동일 물품·기간 동시 예약 중복 | 0건 | 0건 (100명 × 20라운드, 200명 × 20라운드, 라운드마다 성공 정확히 1건) | 충족 |
| 피드 인덱스·캐시 전후 p95 50% 단축 | | 이번에는 "적용 전" 기준선만 (아래) | 미측정 |

## 환경

| 항목 | 값 |
| --- | --- |
| 호스트 | Windows 11, Ryzen 7 7800X3D(8코어 16스레드), RAM 31GB, Docker Desktop(엔진 29.2.1, WSL2) |
| 구성 | `infra/docker-compose.load.yml`: Nginx 1(라운드로빈) → 앱 2(Spring Boot, `local` 프로필 + SQL 로그 끔) → MySQL 8.4.11 · Redis 7 · RabbitMQ 4 각 1 |
| 앱 자원 | 컨테이너당 CPU 2 · 메모리 1200m · 힙 768m(운영 t3.small·운영 compose 기준). Hikari 풀·Tomcat 스레드는 기본값(풀 10, 스레드 200) |
| 부하 발생기 | k6 2.4.0 컨테이너(같은 호스트, 같은 docker 네트워크) |
| 데이터 | 회원 10,000(대표 동네 인증 완료) · 물품 30,000(AVAILABLE 28,800 · HIDDEN 600 · DELETED 600) · 대여 100,000(완료·취소 92,000 · 진행 중 3,000 · 미래 예약 5,000). 서울 행정동 427곳, 회원 20%는 10개 동에 집중, 물품은 소유자 동네 중심 ±약 1km |
| 결제 | 플래그 끔(예약은 바로 RESERVED). 결제·AI 호출 없음 |

한계: MySQL·k6가 앱과 같은 PC에 있고 네트워크 지연이 없어 **절대 수치는 운영(EC2 t3.small 2대 + RDS)보다 좋게 나온다.** 운영에서는 짧은 확인이 따로 필요하다(제안: 조회만, 20명·5분, 오류율 5% 또는 메모리 경보 시 중단).

## 시나리오

| 시나리오 | 스크립트 | 내용 |
| --- | --- | --- |
| A 조회 혼합 | `read-mix.js` | 피드 50% · 검색 30%(키워드 60%·카테고리 30%·기간+대여 가능만 30%, 정렬 무작위, 0~2쪽) · 상세 20%(직전 목록의 물품 80%). 짝수 VU는 회원(대표 동네), 홀수 VU는 비회원(`regionId`) |
| B 예약, 다른 물품 | `reserve-distinct.js` | VU마다 자기 회원으로 무작위 물품·기간(오늘+1~150일 시작, 1~3일). 409 `RENTAL_PERIOD_CONFLICT`는 정상 거절로 센다 |
| C 예약, 같은 물품 경합 | `reserve-contention.js` | 20라운드 × VUS명이 같은 시각에 같은 물품·같은 기간(오늘+160~162일). 라운드 간격 5초 |

- 단계: 램프업 2분 → VUS명 5분 유지 → 30초 램프다운(A·B). 요청 사이 0.5~1.5초 쉼(평균 1초, `THINK=1`).
- 로그인은 setup에서만 하고 지표는 API 태그별로만 집계했다(로그인 제외).
- 중복 확인: `check-duplicates.sql`(같은 물품에서 기간이 겹치는 활성 대여 쌍 수).

## 결과

응답 시간 단위 ms. TPS는 해당 API 요청 수 ÷ 그 실행의 전체 시간(램프업·램프다운 포함)이라 유지 구간 TPS보다 낮다. 실행 시간: A 100명 454.3초 · A 200명 453.4초 · A 100명 쉼 없음 182.8초 · B 100명 451.8초 · B 200명 462.5초.

### A 조회 혼합

| VUS | 쉼 | API | 요청 수 | TPS | p50 | p95 | p99 | 최대 | 오류율 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100 | 1초 | 피드 | 18,416 | 40.5 | 4.8 | 9.7 | 13.1 | 230 | 0% |
| 100 | 1초 | 검색 | 10,854 | 23.9 | 48.3 | 102.6 | 123.8 | 254 | 0% |
| 100 | 1초 | 상세 | 7,377 | 16.2 | 5.0 | 9.4 | 12.1 | 54 | 0% |
| 200 | 1초 | 피드 | 36,640 | 80.8 | 4.7 | 10.1 | 14.5 | 71 | 0% |
| 200 | 1초 | 검색 | 21,984 | 48.5 | 57.2 | 122.2 | 154.5 | 302 | 0% |
| 200 | 1초 | 상세 | 14,692 | 32.4 | 5.2 | 9.5 | 12.5 | 59 | 0% |
| 100 | 0(추가) | 피드 | 38,519 | 210.7 | 119.2 | 387.8 | 626.6 | 1,485 | 0% |
| 100 | 0(추가) | 검색 | 23,293 | 127.4 | 254.0 | **559.2** | 776.0 | 1,590 | 0% |
| 100 | 0(추가) | 상세 | 15,594 | 85.3 | 130.1 | 482.6 | 731.7 | 1,804 | 0% |

- 추가 행은 처리량 한계를 보려고 쉬지 않고 보낸 것이다(램프업 30초 → 2분 유지, 실행 183초, 합계 약 425 req/s). 이 부하에서 검색 p95가 목표 500ms를 넘었다.
- 100명·쉼 1초 유지 구간의 자원(docker stats 한 번): MySQL CPU 약 64%(코어 1개 기준), 앱 각 약 20%, 앱 메모리 약 590~630MiB / 1200MiB.
- Nginx 분배: 두 앱 18,466 : 18,471. A 100명 실행 구간(08:01~08:09 UTC)의 Nginx 로그만 센 값이다(k6 요청 36,907건 + 앞뒤 수동 확인 요청 약 30건). 고르게 나뉜다.

### B 예약, 다른 물품

| VUS | 요청 수 | TPS | 성공(201) | 기간 충돌(409) | 실패 | p50 | p95 | p99 | 최대 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100 | 36,679 | 81.2 | 36,149 | 530 | 0 (0%) | 19.1 | 23.3 | 28.0 | 211 |
| 200 | 46,760 | 101.1 | 44,252 | 1,901 | **607 (1.30%)** | 20.1 | 26.5 | **29,997.7** | 59,996 |
| 200 (개선 후) | 73,872 | 162.0 | 71,884 | 1,988 | 0 (0%) | 12.8 | 17.4 | 38.1 | 229 |
| 100 쉼 0 (개선 후, 3회) | 65,499 · 66,766 · 67,732 | 646 · 658 · 668 | 60,727 · 59,263 · 57,487 | 4,772 · 7,503 · 10,245 | 0 (0%) | 76.9 · 74.5 · 74.7 | 362.3 · 347.9 · 343.5 | 489.6 · 475.8 · 476.1 | 1,079 · 898 · 1,157 |

- 200명은 유지 구간 약 3분째(실행 약 5분째)부터 두 앱이 같이 30초씩 멈췄다. 멈춘 동안 요청은 Hikari 대기 30초 초과로 500(`CannotCreateTransactionException`), 또는 k6 60초 시간 초과로 끝났다. 멈추기 전까지는 초당 약 200건을 p95 30ms 안에 처리했다.
- 뒤이어 같은 조건으로 쉬지 않고 보내자(100명, `THINK=0`, 70초) **수 초 만에 같은 현상이 재현됐다.** 3회 돌려 실패율 25.5%(660건 중 168건, p95 56.9초) · 7.4%(1,143건 중 85건) · 23.7%(746건 중 177건).
- 중복 예약: B 실행 뒤 모두 0쌍.
- 개선 후 행은 `fix/notification-async-listener` 코드로 앱 이미지를 다시 빌드하고, 앞선 부하 예약(id > 100000)을 지운 뒤 같은 명령으로 잰 값이다. 200명은 실행 456.1초, 쉼 0은 `VUS=100 THINK=0 RAMP=10s HOLD=60s`로 각 101초. 두 경우 모두 Hikari 대기 초과·멈춤이 없었고 중복 예약 0쌍이다. 쉼 0의 기간 충돌(409)이 회차마다 늘어난 것은 앞 회차 예약이 남아 있어서다.
- 개선 후 알림: 200명은 예약 71,884건에 알림 143,768건(양측 2건씩)으로 빠짐없이 저장됐다. **쉼 0에서는 나와야 할 알림 354,954건 중 231,397건(약 65%)을 실행기 큐 포화로 버렸다**(아래 "병목" 개선 후).

### C 예약, 같은 물품 경합

| VUS × 라운드 | 요청 수 | 성공 | 기간 충돌 | 락 대기 초과 | 실패 | p50 | p95 | p99 | 최대 | 중복 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 100 × 20 | 2,000 | 20 | 1,980 | 0 | 0 | 167.5 | 299.4 | 314.6 | 347 | 0 |
| 200 × 20 | 4,000 | 20 | 3,980 | 0 | 0 | 328.7 | 590.5 | 618.3 | 646 | 0 |

- 라운드마다 정확히 1건만 성공했고(`check-duplicates.sql`: 대상 물품 40개 모두 1건), DB의 기간 겹침은 0쌍이다.
- 락 대기(3초) 초과는 없었다. 경합 요청은 Redisson 락을 차례로 얻어 점유 검사에서 409로 끝나므로 응답 시간이 VUS에 비례해 늘었다(100명 p95 0.3초, 200명 0.6초).
- 200명 C의 첫 시도는 B 200명 직후의 멈춤 꼬리에 걸려 setup 로그인 6건이 실패해 시작하지 못했다. 앱이 회복된 뒤 다시 돌린 값이다.

## 병목: 예약 뒤 알림 저장의 커넥션 풀 교착 (`fix/notification-async-listener`에서 후보 1로 고쳤다)

`chore/load-test`에서 원인을 확인하고, `fix/notification-async-listener`에서 후보 1로 고쳤다. 결과는 아래 "개선 후"와 B 표의 개선 후 행.

**현상**: 예약 동시 처리가 한 앱에서 약 10건(Hikari 기본 풀 크기)을 넘으면 그 앱의 예약·조회·로그인이 모두 30초씩 멈춘다. 멈춘 동안 MySQL은 열린 트랜잭션·락 대기가 0이고 앱 커넥션은 모두 쉬는 상태(Sleep)였다. Hikari 로그는 `total=10, active=10, idle=0, waiting=99`.

**원인(스레드 덤프로 확인)**: 멈춘 순간 두 앱의 스레드 덤프 12장에서 요청 스레드가 아래 경로로 커넥션을 기다리고 있었다.

```
RentalController.createRental → RentalService.create → RedissonRentalLockStrategy.executeWithItemLock
  → RentalReservationTxService.create (커밋 후 AFTER_COMMIT 콜백)
  → RentalNotificationEventListener.onNotification → RentalNotificationAdapter.send → NotificationService.send
  → NotificationTxService.insertIfAbsent (REQUIRES_NEW) → HikariPool.getConnection  ← 대기
```

- `@TransactionalEventListener(AFTER_COMMIT)`은 커밋 직후·트랜잭션 정리 전에 같은 스레드에서 돈다. 이때 예약 트랜잭션의 커넥션은 아직 풀에 돌아가지 않았다. 리스너가 REQUIRES_NEW로 **두 번째 커넥션**을 요청한다(예약 확정 알림은 양측 2건).
- 그래서 예약 요청 하나가 잠깐 커넥션 2개를 쓴다. 동시에 10건이 첫 커넥션을 잡은 채 두 번째를 기다리면 아무도 돌려주지 않아 30초(Hikari `connection-timeout` 기본) 뒤에야 풀린다. 같은 풀을 쓰는 다른 API도 같이 멈춘다.
- 이 대기는 Redisson 물품 락 안에서 일어난다(`executeWithItemLock`이 바깥). 그래서 멈춘 동안 같은 물품 락도 붙잡혀 있다.
- 시나리오 C는 라운드당 성공이 1건이라 알림이 거의 없어서 걸리지 않았다. A(조회)는 이 경로를 타지 않는다.

**후보 개선(fix 브랜치에서 고른다)** — 위에서부터 효과가 크다고 본다.

1. 알림 리스너를 요청 스레드 밖으로 뺀다: `@Async`(전용 실행기)로 `AFTER_COMMIT` 리스너를 돌려, 예약 커넥션·락이 풀린 뒤 알림 저장이 새 커넥션을 잡게 한다. 실시간 전달(STOMP)도 요청 응답 시간에서 빠진다. 다른 AFTER_COMMIT → `NotificationTxService` 경로(취소·인계·반납·결제·연체 알림)에도 같은 문제가 있으므로 함께 바뀐다. 실행기 큐가 넘칠 때·앱 종료 때 알림 유실을 어떻게 볼지 정해야 한다.
2. 또는 알림 발행을 락·트랜잭션이 모두 끝난 뒤로 옮긴다(예: `RentalService.create`가 `executeWithItemLock` 반환 뒤에 보냄). 발행 지점마다 고쳐야 하고, 현재 규칙("상태 전이 트랜잭션 안에서 이벤트만 남기고 AFTER_COMMIT 리스너가 보낸다", `docs/prompts/feat/notification/notes.md`)과 다르다.
3. 보조: Hikari `maximum-pool-size`를 늘리고(예: 20~30, MySQL `max_connections`·RDS 크기 확인 필요) `connection-timeout`을 짧게(예: 3초) 둔다. 교착이 생기는 동시 수를 높이고 멈춤을 짧게 할 뿐 원인은 그대로다.

**개선 후(후보 1 채택)**: 알림 저장 AFTER_COMMIT 리스너(`RentalNotificationEventListener`·`ReviewNotificationListener`)의 본문을 전용 실행기 `NotificationListenerExecutor`(스레드 4·큐 1000, 큐가 넘치면 버리고 경고 로그, 종료 때 최대 10초 대기)에 맡긴다. 요청 스레드는 커밋 뒤 바로 커넥션과 물품 락을 돌려주고, 실행기 스레드는 커넥션을 하나씩만 잡는다. 후보 3(풀 크기·timeout)은 바꾸지 않았다.

- B 200명: 실패 1.30% → 0, p99 30.0s → 38.1ms, 처리량 101 → 162 TPS. 알림 버림 0건.
- B 100명 쉼 0: 실패 7.4~25.5%·p95 56.9s → 실패 0·p95 약 350ms, 처리량 약 650 TPS(3회). 멈춤 없음.
- **받아들인 한계: 쉼 0에서 알림 약 65%를 버렸다.** 앱 하나에 알림이 초당 약 650건 들어오는데(예약 1건당 2건), MySQL이 예약 처리로 바빠 알림 저장 한 건에 약 20ms가 걸려 스레드 4개가 앱당 초당 약 200건만 저장했다. 들어오는 속도가 계속 더 빨라 큐 1000칸이 몇 초 만에 찬다(큐를 늘려도 버리는 시점만 늦어진다). 정한 규칙대로 예약 처리를 지키고 알림을 버린 것이며, 교착은 다시 생기지 않았다. 사람처럼 쓰는 부하(200명, 쉼 1초)에서는 버림이 없었다.
- 큐가 찼을 때 요청 스레드를 기다리게 하는 방식은 쓰지 않았다: 요청 스레드가 커넥션을 쥔 채 기다리면 실행기 스레드가 커넥션을 받지 못해 짧은 교착이 다시 생길 수 있다.
- 남은 일: 알림 묶어 저장(배치 INSERT)으로 저장 처리량 올리기, 운영에서 `Notification listener task dropped` 경고 로그 경보.

## 피드 기준선 (§8-4 "적용 전")

- 현재 피드는 `idx_items_status_bumped_at_id` + 위경도 bounding box 선필터 + `ST_Distance_Sphere`이고, 공간 인덱스·Geohash·캐시는 없다(`docs/erd.md` items 인덱스).
- 이번 데이터(물품 3만, 서울 집중)에서 피드는 쉼 1초일 때 p95 약 10ms, 쉬지 않을 때 p95 387.8ms였다. 쉼 1초 부하에서는 이미 매우 빨라 "p95 50% 단축"을 보이기 어렵다. 그래서 이 브랜치는 "적용 전" 기준선만 남긴다(인덱스·캐시 추가는 코드 변경이기도 하다). 전후 비교는 쉬지 않는 부하(`THINK=0`)나 더 큰 물품 수에서 하는 것이 맞아 보이며, 개선 브랜치에서 정한다.
- 같은 부하에서 검색이 가장 느리다(p95 559.2ms, 이름·설명 `LIKE '%키워드%'` + 개수 쿼리). 원인 추정만 적는다. 측정은 하지 않았다.

## 재현

```bash
docker compose -f infra/docker-compose.load.yml up -d --build
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental --default-character-set=utf8mb4 rental_load < scripts/load/seed.sql  # PowerShell은 아래
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/read-mix.js
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/reserve-distinct.js
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/reserve-contention.js
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental rental_load < scripts/load/check-duplicates.sql
# 교착 재현: run --rm k6 run -e VUS=100 -e THINK=0 -e RAMP=10s -e HOLD=60s /scripts/reserve-distinct.js
# 200명은 VUS=200. C를 다시 돌릴 때는 -e ITEM_FROM=29000처럼 다른 물품을 쓴다
```

위는 bash 기준이다. PowerShell에서는 `<` 리디렉션을 쓸 수 없어 SQL 두 줄을 `cmd /c`로 돌린다(`Get-Content` 파이프는 PowerShell 5.1에서 한글이 깨진다). 나머지 줄은 그대로다.

```powershell
cmd /c "docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental --default-character-set=utf8mb4 rental_load < scripts\load\seed.sql"
cmd /c "docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental rental_load < scripts\load\check-duplicates.sql"
```

## 범위 밖

장애·결제 지연 주입(§8-2), 락 전략 비교(§8-1, 별도 브랜치), AI 호출(비용), 운영 환경 측정, 피드 인덱스·캐시 적용 후 측정.
