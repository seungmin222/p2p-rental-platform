# 부하 테스트 도구 (k6)

`docs/architecture.md` §7 목표 성능을 로컬 docker에서 재는 도구다. 결과와 해석은 `docs/experiments/load-test.md`.

| 파일 | 내용 |
| --- | --- |
| `../../infra/docker-compose.load.yml` | 부하 전용 구성(프로젝트 `rental-load`): MySQL(`rental_load`)·Redis·RabbitMQ·앱 2개·Nginx, k6(프로필 `k6`) |
| `nginx.conf` | 앱 2개 라운드로빈 프록시(`/api/`만) |
| `seed.sql` | 더미 데이터: 회원 1만·물품 3만·대여 10만 (CRC32 기반이라 매번 같다) |
| `lib.js` | 공통: 로그인(setup 전용), 시드 규칙(소유자·상태), 결과 요약 |
| `read-mix.js` | 시나리오 A: 피드 50%·검색 30%·상세 20%, 회원·비회원 반씩 |
| `reserve-distinct.js` | 시나리오 B: 서로 다른 물품 예약 |
| `reserve-contention.js` | 시나리오 C: 같은 물품·같은 기간 동시 예약(라운드 반복) |
| `check-duplicates.sql` | 기간이 겹치는 활성 대여(중복 예약) 수, 시나리오 C 물품별 예약 수 |
| `results/` | k6 결과 JSON(커밋하지 않음) |

## 안전장치

- DB는 이 compose 안의 MySQL 컨테이너(`rental_load` 스키마, 호스트 포트 13306)뿐이다. 개발 DB(`rental`, 3306)·운영 RDS 접속 정보는 어디에도 없다.
- `seed.sql`은 `DATABASE()`가 `rental_load`가 아니거나(스키마를 고르지 않은 경우 포함) 회원이 이미 있으면 SIGNAL로 멈춘다. mysql 클라이언트를 `--force` 없이 실행할 때만 뒤 문장이 막히므로 `--force`를 붙이지 않는다.
- 더미 회원 비밀번호는 모두 `loadtest123!`(부하 테스트 전용 값), JWT 비밀값도 compose에 적힌 테스트 전용 값이다.

## 실행 (저장소 루트에서)

아래 블록은 bash(Git Bash) 기준이다. `grep`·`<` 리디렉션은 PowerShell에서 쓸 수 없으니 그 줄은 블록 아래 "PowerShell" 명령으로 바꿔 쓴다.

```bash
# 1) 빌드·기동 (앱 2개가 Flyway로 스키마를 만든다. 로그에 "Started RentalApplication"이 두 번 나오면 준비 끝)
docker compose -f infra/docker-compose.load.yml up -d --build
docker compose -f infra/docker-compose.load.yml logs backend1 backend2 | grep "Started RentalApplication"

# 2) 더미 데이터 (약 5초)
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental --default-character-set=utf8mb4 rental_load < scripts/load/seed.sql

# 3) 시나리오 (기본: 램프업 2분 → VUS명 5분 유지 → 30초 램프다운)
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/read-mix.js
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/reserve-distinct.js
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 /scripts/reserve-contention.js

# 4) 예약 정합성 (overlapping_pairs = 0, 시나리오 C 물품마다 1건)
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental rental_load < scripts/load/check-duplicates.sql

# 5) 정리 (부하용 볼륨까지 지운다. 개발용 compose와 무관)
docker compose -f infra/docker-compose.load.yml down -v
```

PowerShell(1·2·4단계와 요청 분배, 나머지 줄은 위와 같다). SQL 파일은 `cmd /c`로 넘긴다. `Get-Content ... |` 파이프는 PowerShell 5.1에서 한글이 깨지므로 쓰지 않는다.

```powershell
docker compose -f infra/docker-compose.load.yml logs backend1 backend2 | Select-String "Started RentalApplication"
cmd /c "docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental --default-character-set=utf8mb4 rental_load < scripts\load\seed.sql"
cmd /c "docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental rental_load < scripts\load\check-duplicates.sql"
docker compose -f infra/docker-compose.load.yml logs nginx | Select-String -Pattern "up=\S+" -AllMatches | ForEach-Object { $_.Matches.Value } | Group-Object | Select-Object Count, Name
```

- Git Bash에서는 `/scripts/...` 경로가 바뀌지 않게 앞에 `MSYS_NO_PATHCONV=1`을 붙인다. PowerShell·cmd에서는 `/scripts/...` 경로를 그대로 쓴다(k6 컨테이너 안 경로).
- 환경 변수: `VUS`(가상 사용자), `RAMP`·`HOLD`(단계 길이, 예 `30s`), `THINK`(요청 사이 평균 쉬는 시간 초, 기본 1, 0이면 쉬지 않음), `LOGIN_USERS`(A에서 미리 로그인할 회원 수, 기본 200), C 전용 `ROUNDS`(기본 20)·`GAP_MS`(라운드 간격, 기본 5000)·`ITEM_FROM`(대상 물품 시작 id, 기본 30000에서 내려감).
- 로그인은 각 스크립트 `setup()`에서 미리 한다(BCrypt 비용이 측정에 섞이지 않게). 액세스 토큰 TTL이 30분이라 한 번 실행은 30분 안에 끝나야 한다.
- 시나리오를 다시 돌릴 때 부하로 생긴 예약만 지우려면: `DELETE FROM rental_status_histories WHERE rental_id > 100000; DELETE FROM rentals WHERE id > 100000;` (시드 대여 id는 1..100000).
- 요청 분배 확인(bash 전용, PowerShell은 위 블록): `docker compose -f infra/docker-compose.load.yml logs nginx | grep -o "up=[^ ]*" | sort | uniq -c`. Nginx 로그는 컨테이너가 뜬 뒤 모든 실행이 쌓이므로 한 실행만 보려면 `logs --since <시작 시각> --until <끝 시각> nginx`로 구간을 자른다.

## 시나리오 날짜 구간 (서로 겹치지 않게)

| 대상 | 기간 |
| --- | --- |
| 시드 진행 중 대여(RENTING, 물품 1..3000) | 어제 ~ 오늘+1~5일 |
| 시나리오 B | 시작 오늘+1~150일, 1~3일 |
| 시나리오 C | 오늘+160 ~ +162일 |
| 시드 미래 예약(RESERVED, 물품 6의 배수) | 오늘+200 ~ +228일 |
