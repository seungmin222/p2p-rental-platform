# chore/load-test 결정 기록

## 요약
- API 변경 없음. 새 파일: `infra/docker-compose.load.yml`, `scripts/load/`(seed.sql·check-duplicates.sql·nginx.conf·lib.js·read-mix.js·reserve-distinct.js·reserve-contention.js·README.md), 결과 `docs/experiments/load-test.md`.
- 결과: 조회 p95 ≤ 500ms·예약 중복 0건은 200명까지 충족. 예약은 100명 충족, 200명(또는 100명 쉼 없음)에서 멈춤.
- 남은 일: 예약 뒤 AFTER_COMMIT 알림 리스너가 REQUIRES_NEW로 두 번째 커넥션을 잡아 Hikari 풀이 교착된다(스레드 덤프로 확인, 코드 미수정). 개선 방향(리스너 @Async 등)은 사용자 결정 대기. 고친 뒤 같은 명령으로 "개선 후" 행을 더한다.
- 미실행: 운영 환경 짧은 확인, 피드 인덱스·캐시 전후 비교(§8-4, 이번 피드 수치가 "적용 전" 기준선), 장애 주입·락 전략 비교.

## 결정
- **대상 환경은 로컬 docker**(사용자 승인). 운영 t3.small 2대는 학교 공용 계정·RDS에 더미 데이터를 넣어야 해서 이번에 쓰지 않았다. 앱 컨테이너는 운영 compose와 같은 메모리 1200m·힙 768m에 CPU 2개로 묶어 t3.small(2 vCPU)에 가깝게 했다. MySQL·Nginx·k6는 같은 PC라 절대 수치는 운영보다 좋게 나온다(결과 문서에 한계로 적음).
- **DB 분리 방식: 계획의 "개발 MySQL 안 별도 스키마" 대신 부하 전용 compose 프로젝트(`rental-load`)에 MySQL 컨테이너를 따로 뒀다.** 개발 DB 볼륨을 건드릴 일이 아예 없고 `down -v`로 깨끗이 지울 수 있어서. 스키마 이름 `rental_load` 검사는 그대로 seed.sql 첫머리에 둔다.
- 앱 프로필은 `local` + 환경 변수(접속 정보·테스트 JWT 비밀값). `prod`는 S3·운영 비밀값을 요구해서. local의 SQL 디버그 로그는 `LOGGING_LEVEL_ORG_HIBERNATE_SQL=info`로 껐다(로그 출력이 응답 시간을 왜곡하므로).
- **도구는 k6**(사용자 승인). §8-1 락 전략 비교의 JMeter는 `chore/concurrency-lock-experiment` 몫으로 남긴다. k6는 docker 이미지(grafana/k6:2.4.0)로만 돌려 PC 설치가 없다.
- 더미 데이터는 난수 대신 id의 CRC32로 만든다: 매번 같은 데이터가 나오고, k6가 소유자·숨김 규칙을 계산으로 알 수 있다(lib.js `ownerOf`·`isListed`).
- 위치 분포: 서울 행정동 427곳에 회원을 나누고 20%는 앞쪽 10개 동(종로구 일대)에 몰았다. 물품은 소유자 대표 동네 중심 ±약 1km. 3km(NORMAL) 반경 안에 물품이 수백~수천 개 들어온다.
- 대여 10만은 대부분 과거 완료·취소, 진행 중 3,000건(물품 1..3000), 미래 예약 5,000건(오늘+200일~). 시나리오 B(오늘+1~152일)·C(오늘+160~162일)와 날짜가 겹치지 않게 나눴다. 결제·정산 행은 만들지 않았다(결제 플래그 끔 → 예약은 바로 RESERVED).
- 약관 동의 행은 넣지 않았다. 로그인 응답에 `requiresTermsAgreement=true`가 올 뿐 대상 API를 막지 않는다.
- 로그인은 setup()에서만 한다(BCrypt 비용이 측정에 섞이지 않게). 요약 지표는 `api` 태그별로만 뽑는다(setup 로그인 제외).
- 예상된 거절은 오류로 세지 않는다: B는 409 `RENTAL_PERIOD_CONFLICT`, C는 409 `RENTAL_PERIOD_CONFLICT`·`RENTAL_LOCK_TIMEOUT`. 그 밖은 `*_failed`로 센다.
- C의 동시성: k6에 장벽(barrier)이 없어 setup이 정한 시각 t0 + 라운드×5초까지 각 VU가 기다렸다 보낸다. 라운드 간격 5초는 락 대기 3초보다 길게 둔 것.
- 요청 사이 쉬는 시간(think time) 평균 1초를 기본으로 했다. "동시 사용자 100명"을 사람처럼 쓰는 사용자 100명으로 읽었기 때문. 처리량 한계를 보려고 A를 쉼 없이(100명, 2분 유지) 한 번 더 돌렸고, 결과 문서에 "추가"로 표시했다.
- B 200명 멈춤의 원인은 코드를 고치지 않고 확인만 했다(프롬프트 지시): MySQL processlist·innodb_trx(열린 트랜잭션 0), Hikari 예외 메시지(active=10·waiting=99), `kill -3`으로 받은 두 앱 스레드 덤프 12장. 재현은 B를 `THINK=0`으로 짧게 돌려 했다(이때 생긴 예약도 중복 검사에 포함, 0쌍).
- C 200명은 다른 물품(`ITEM_FROM=29000`)으로 돌렸다. 100명 때 쓴 물품(30000부터)은 이미 그 기간이 예약돼 있어서.
- k6 요청에 `name` 태그를 붙였다. 쿼리 문자열·물품 id마다 시계열이 생겨 10만 개 경고가 나서(첫 A 100명 실행에서 발생, 수치에는 영향 없음).
