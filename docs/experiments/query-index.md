# 조회 인덱스 비교 (14주차, `chore/query-index-experiment`)

도전 과제 ④ "동네 피드 성능"(`docs/architecture.md` §8)의 인덱스 부분과, 부하 테스트(`load-test.md`)에서 가장 느렸던 검색(REQ-010)의 인덱스를 비교했다. Redis 캐시·Geohash는 범위 밖이다.

## 요약

| 대상 | 채택 | 근거 (동시 100명·쉼 없음 p95) |
| --- | --- | --- |
| 피드(REQ-009) | **F3** `(status, bumped_at DESC, id DESC, latitude, longitude)`로 기존 F2를 바꾼다 | 30만·버퍼 풀 128MB: 306~653 → 158~166ms(−48~75%), 처리량 2~4배. 3만이나 버퍼 풀 768MB에서는 F2와 같다(±3%). 인덱스 크기·쓰기 비용은 F2와 비슷하다 |
| 검색(REQ-010) | **S2** FULLTEXT(ngram) `(name, description)` + `MATCH ... AGAINST` 구문 검색. 한글·숫자 2~100글자 검색어만, 그 밖은 LIKE | 3만: 978 → 589ms(−40%), 처리량 2배. 30만·768MB: 9.0s → 2.4s(−73%, S2+F3), 처리량 4.8배. LIKE와 결과 id 집합·개수가 같다(한 글자 제외, 그건 앱이 LIKE로 보낸다) |
| 기각 | F1 `(status)`, F4 공간 인덱스, S1 필터 복합 B-tree | F1은 인덱스 없음(F0)보다 느리다. F4는 옵티마이저가 피드에서 거의 고르지 않고, 검색에서 고르면 3배 느려지며 INSERT가 약 10배 느려진다. S1은 옵티마이저가 고르지 않는다(LIKE '%키워드%'에 B-tree 무효) |

- 마이그레이션: `V22__add_listing_indexes_to_items.sql`. API 변경 없음.
- **남은 문제**: 30만 건에서는 S2+F3로도 검색 p95가 목표 500ms를 크게 넘는다(768MB에서 2.4s). EXPLAIN상 남은 비용은 키워드 없는 검색의 개수 쿼리(범위 안 행 전부)와, 시드 키워드가 전국 물품의 5%씩 걸려 FULLTEXT 후보가 많은 점이다(아래 "해석"). 개수 쿼리 상한·캐시는 다음 브랜치 후보다.
- 피드 "인덱스·캐시 전후 p95 50% 단축"(`load-test.md` 요약)은 인덱스만으로는 데이터가 버퍼 풀을 넘는 경우(30만·128MB)에만 충족했다. 메모리에 다 들어가면 피드는 이미 앱(CPU 2개×2)이 병목이라 인덱스로 더 줄지 않는다.

## 환경

`load-test.md` "환경"과 같다(Nginx 1 → 앱 2(CPU 2·힙 768m) → MySQL 8.4.11, k6 2.4.0, 같은 PC). 다른 점만 적는다.

| 항목 | 값 |
| --- | --- |
| 부하 | `read-mix.js`에 `ONLY=feed` 또는 `ONLY=search`, `VUS=100 THINK=0 RAMP=30s HOLD=2m`(램프다운 30초 포함 약 3분) |
| 데이터 | 3만: 기존 시드(물품 30,000, AVAILABLE 28,800). 30만: `seed.sql` `@item_count=300000`(AVAILABLE 288,000, 같은 규칙). 회원 1만·대여 10만(물품 1..30000)은 같다. 측정 전 앞선 예약 시나리오가 남긴 대여를 지웠다 |
| MySQL 버퍼 풀 | 기본 128MB(컨테이너 기본값). 30만에서 `items` PRIMARY가 약 100MB라 데이터가 버퍼 풀을 넘는다. 그래서 30만은 `SET GLOBAL innodb_buffer_pool_size`로 768MB(운영 RDS 기본 공식 `DBInstanceClassMemory*3/4`의 1GB 인스턴스 값)에서도 쟀다 |
| 인덱스 변경 | 부하 DB에서만 `ALTER TABLE`. 구성마다 `ANALYZE TABLE` → 대표 쿼리 한 번 예열 → EXPLAIN ANALYZE → k6 |
| 쿼리 변경이 필요한 구성 | S2는 앱 코드를 바꾼 이미지로, S0은 develop 이미지로 쟀다. F4는 앱 쿼리를 바꾸지 않고 DB에서 EXPLAIN만 비교했다 |

한계: k6 한 번씩 잰 값이다. 30만·128MB의 F2는 두 번 재서 306·653ms로 크게 흔들렸다(버퍼 풀 교체 상태에 따라 디스크 읽기가 달라진다). 그 밖의 반복(F3 158·166ms)은 5% 안이었다.

## 비교한 구성

| 이름 | 내용 |
| --- | --- |
| F0 | 피드 인덱스 없음(`idx_items_status_bumped_at_id` 삭제) |
| F1 | `(status)` 단일 |
| F2 | `(status, bumped_at DESC, id DESC)` — V2의 기존 구성 |
| F3 | `(status, bumped_at DESC, id DESC, latitude, longitude)` — 정렬 순서를 지키면서 위경도 조건을 인덱스 안에서 거른다(ICP) |
| F3' | `(status, latitude, longitude)` — 위도 범위 후 filesort. 검색용 S1과 같아 S1로 쟀다 |
| F4 | F2 + 생성 컬럼 `location POINT SRID 4326`(STORED, `ST_SRID(POINT(longitude, latitude), 4326)`) + SPATIAL, 박스 조건을 `MBRContains(박스 폴리곤, location)`으로 |
| S0 | 현재: 이름·설명 `LIKE '%키워드%'` + 개수 쿼리 |
| S1 | F2 + `(status, latitude, longitude)` |
| S2 | F2 + FULLTEXT `(name, description) WITH PARSER ngram`, 키워드 조건을 `MATCH(name, description) AGAINST('"키워드"' IN BOOLEAN MODE)`로 |

## 결과: k6 (ms, 동시 100명·쉼 없음)

### 피드

| 데이터 | 버퍼 풀 | 구성 | 요청 수 | 처리량(/s) | p50 | p95 | p99 | 오류 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 3만 | 128MB | F0 | 73,595 | 402.7 | 198.2 | 404.5 | 497.8 | 0 |
| 3만 | 128MB | F1 | 40,456 | 221.4 | 327.6 | 741.8 | 844.2 | 0 |
| 3만 | 128MB | F2 | 308,786 | 1,686.4 | 13.6 | 182.0 | 261.0 | 0 |
| 3만 | 128MB | **F3** | 317,071 | 1,735.1 | 14.1 | 179.4 | 254.9 | 0 |
| 30만 | 128MB | F0 | 8,058 | 44.0 | 1,964.5 | 2,880.3 | 3,400.7 | 0 |
| 30만 | 128MB | F2 (1차, 시드 직후) | 127,058 | 689.5 | 95.2 | 306.4 | 447.2 | 0 |
| 30만 | 128MB | F2 (2차) | 54,458 | 297.8 | 247.0 | 652.8 | 912.0 | 0 |
| 30만 | 128MB | **F3** (1차) | 250,532 | 1,370.0 | 23.1 | 157.9 | 251.3 | 0 |
| 30만 | 128MB | **F3** (2차) | 223,771 | 1,224.3 | 28.2 | 165.5 | 270.8 | 0 |
| 30만 | 128MB | F3 + S2 | 211,730 | 1,156.8 | 29.0 | 174.0 | 283.1 | 0 |
| 30만 | 768MB | F2 | 309,182 | 1,691.0 | 17.0 | 175.5 | 251.4 | 0 |
| 30만 | 768MB | **F3** + S2 | 306,874 | 1,677.8 | 15.6 | 180.4 | 260.8 | 0 |

### 검색

| 데이터 | 버퍼 풀 | 구성 | 요청 수 | 처리량(/s) | p50 | p95 | p99 | 오류 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 3만 | 128MB | S0 | 27,047 | 148.0 | 562.7 | 977.5 | 1,168.1 | 0 |
| 3만 | 128MB | S1 | 28,040 | 153.4 | 412.6 | 1,147.9 | 1,289.1 | 0 |
| 3만 | 128MB | **S2** | 56,575 | 307.5 | 236.6 | 589.2 | 782.6 | 0 |
| 30만 | 128MB | S0 | 601 | 3.1 | 24,599 | 35,915 | 41,078 | 2.0%(타임아웃) |
| 30만 | 128MB | S2 | 1,452 | 7.8 | 10,998 | 14,590 | 21,579 | 0 |
| 30만 | 128MB | S2 + F3 | 1,189 | 6.4 | 13,382 | 18,215 | 19,569 | 0 |
| 30만 | 768MB | S0 | 2,360 | 12.7 | 6,796 | 8,998 | 9,797 | 0 |
| 30만 | 768MB | **S2 + F3** | 11,122 | 60.7 | 1,365 | 2,394 | 2,908 | 0 |

검색 요청은 키워드 60%·카테고리 30%·기간+대여 가능 30%가 겹치고 정렬 무작위, 0~2쪽이다(`load-test.md` 시나리오 A). 키워드가 없는 40%는 S2의 영향을 받지 않는다.

## 결과: EXPLAIN ANALYZE (대표 쿼리, ms)

`scripts/load/query-index/gen-queries.mjs`가 만든 쿼리(앱이 보내는 SQL을 general_log로 떠서 동네만 바꾼 것)다. 밀집 동네 = 종로구 일대(regions.id 8, 3km 안 AVAILABLE 약 7,500/3만), 드문 동네 = 내곡동(359, 약 170/3만). 괄호 안은 items에서 읽은 행 수.

| 쿼리 | 3만 F0 | 3만 F2 | 3만 F3 | 3만 F4 | 30만·768MB F2 | 30만·768MB F3 |
| --- | --- | --- | --- | --- | --- | --- |
| 피드 밀집 3km | 26.7 (전체 30,000) | 0.69 (99) | 0.56 (ICP 후 21) | 1.13 (F2 선택) | — | 0.58 |
| 피드 드문 3km | 17.3 | 10.9 (5,958) | 11.5 (ICP 후 21) | **1.72** (SPATIAL 178) | 8.2 (128MB 예열) | 9.3 |
| 피드 밀집 6km | 31.6 | 0.16 | 0.34 | 0.36 | — | 0.41 |
| 피드 커서(30일 전부터) | 26.2 | 0.37 | 0.65 | 0.65 | — | 0.55 |

| 쿼리 | 3만 S0 | 3만 S1 | 3만 S2 | 30만·768MB S0 | 30만·768MB S2+F3 |
| --- | --- | --- | --- | --- | --- |
| 키워드 목록('텐트') | 52.6 (28,800) | 33.2 (전체 훑기) | **2.97** (FULLTEXT 1,500) | 643 (288,000) | **35.8** (FULLTEXT 15,000) |
| 키워드 개수 | 46.1 | 30.8 | **2.6** | 585 | **35.6** |
| 카테고리+가격 개수 | 4.4 | 4.4 | 4.8 | 54.4 | 64 |
| 기간+대여 가능 목록 | 0.41 | 0.44 | 0.49 | 24.2 | 27 |

- F2의 피드는 정렬 순서대로 인덱스를 읽고 행마다 위경도를 확인한다. 동네가 드물면 범위 밖 행을 많이 읽는다(3만에서 5,958행). F3는 같은 순서로 읽되 위경도를 인덱스 안에서 걸러 범위 밖 행의 본문을 읽지 않는다. 데이터가 버퍼 풀에 다 들어가면 본문 읽기도 메모리라 차이가 없고, 넘치면(30만·128MB) 이 차이가 디스크 읽기로 나타나 k6에서 2~4배가 된다.
- F4: 3만의 드문 동네에서만 옵티마이저가 SPATIAL을 골랐다(10.9 → 1.7ms). 30만에서는 같은 쿼리에도 F2를 골랐다(246ms, 128MB). 검색에서는 박스 안 행(30만 밀집 76,000)을 SPATIAL로 모은 뒤 정렬·개수를 해 F2보다 3배 느렸다(키워드 2,189ms·카테고리 개수 1,787ms, 128MB). 결과 행은 박스 쿼리와 같았다(3만, 피드·검색 대표 쿼리 8개의 결과 전체 비교).
- S1: 옵티마이저가 `(status, latitude, longitude)`를 고르지 않고 전체를 훑었다(박스가 AVAILABLE의 약 25%라 선택도가 낮다). 키워드 `LIKE '%키워드%'`는 앞이 와일드카드라 어떤 B-tree로도 범위를 좁힐 수 없다.

## 결과: 쓰기 비용

`scripts/load/query-index/write-cost.sql`: 물품 1만 건 INSERT(트랜잭션 후 ROLLBACK). 3~5번 잰 중앙값(ms). 크기는 `mysql.innodb_index_stats`(MB).

| 구성 | 3만 INSERT 1만 | 30만 INSERT 1만 | 피드 인덱스 크기(30만) |
| --- | --- | --- | --- |
| F0 | 100.8 | 117.7 | — |
| F1 | 111.7 | — | — |
| F2 | 119.5 | 188.5 | 17.6 |
| F3 | 119.3 | 217.9 | 16.6 |
| F4 (F2 + SPATIAL) | 1,445.4 | 1,872.1 | SPATIAL 크기는 통계에 없음 |
| S1 (F2 + 위경도 인덱스) | 137.0 | — | — |
| S2 (F2 + FULLTEXT) | 144.5 | 188.1 | FTS 보조 테이블 별도 |
| S2 + F3 | — | 189.1 | |

- 30만 값은 첫 회가 300~800ms로 튀어(버퍼 풀 교체) 중앙값으로 적었다. F3의 +16%는 이 흔들림 안이다.
- FULLTEXT는 InnoDB가 커밋 때 보조 색인에 반영해 이 INSERT(롤백) 시간에는 일부만 잡힌다. 물품 등록은 하루 수십~수백 건 규모라 영향이 작다고 판단했다.

## S2 결과 동일성 (채택 조건)

`scripts/load/query-index/fulltext-equality.mjs`: KEYWORDS 16개 + 경계 사례 5개(`미러리스 카메라`·`212`·`직거래`·`LED`·`텐`) × 동네 3곳(8·210·359) × 범위 3개(1.5·3·6km) = 189조합에서 LIKE와 MATCH의 결과를 비교했다.

| 데이터 | 조합 | LIKE에만 / MATCH에만 있는 행이 있는 조합 |
| --- | --- | --- |
| 3만 | 189 | 8 — 모두 한 글자 `텐`(MATCH 0건) |
| 30만 | 189 | 8 — 모두 한 글자 `텐` |

- 한 글자는 ngram 토큰(2글자)보다 짧아 FULLTEXT로 찾을 수 없다. 앱은 한 글자를 LIKE로 보낸다.
- 시드 설명이 모두 같아 "2-gram은 있지만 이어지지 않는" 경우(`가나가` vs `가나나가`, `212` vs `2112`)는 통합 테스트(`ItemSearchIntegrationTest.fulltextKeywordMatchesLikeSemantics`)로 확인했다. 구문 검색은 2-gram이 이어져야 일치해 LIKE와 같다.
- **영문은 제외했다**: InnoDB 기본 불용어에 `a`·`i`·`is`·`to` 같은 한두 글자 단어가 있고, ngram 파서는 불용어가 든 토큰을 색인하지 않는다(`led`는 되지만 `ab`·`ia`는 안 된다). 인덱스를 만드는 세션에서 불용어를 끄면 피할 수 있지만, `TRUNCATE`·테이블 재생성 때 서버 기본값으로 다시 만들어지는 것을 부하 DB에서 확인했다. 서버 파라미터(운영 RDS 기본 파라미터 그룹)는 바꾸지 않기로 해, 앱이 영문·기호·공백이 든 검색어를 LIKE로 보낸다.
- 100글자 상한: 구문 검색은 2-gram이 약 128개를 넘으면 MySQL 오류 191(Too many words in a FTS phrase)을 낸다(129글자 통과, 140글자 실패). 그보다 긴 검색어는 LIKE로 보낸다.

## 해석

- **피드**: 지금 데이터 규모와 운영 메모리에서는 F2로 충분하고, 병목은 앱이다(쉼 없음에서 앱 2대 합계 약 1,700 req/s). F3는 같은 비용으로 "데이터가 버퍼 풀을 넘는 경우"의 낭떠러지를 없애므로 F2 대신 둔다. F1처럼 선택도가 낮은 단일 인덱스는 정렬을 못 해 전체 훑기보다 느리다.
- **공간 인덱스(F4)**: 반경 3~6km는 동네 피드에서 넓은 편이라 MBR 후보가 많고, "최신순 20건"은 정렬 인덱스가 더 잘 맞는다. 드문 동네에서만 이득이 있고 쓰기 비용이 크며, 쿼리를 바꾸면 검색에서 옵티마이저가 잘못 고를 위험이 있어 기각했다. 물품이 훨씬 많아지거나 반경이 1km 이하로 좁아지면 다시 볼 만하다.
- **검색**: 키워드 조건이 전체 훑기에서 FULLTEXT로 바뀌어 키워드 쿼리는 15~18배 빨라졌다. 30만에서 남은 시간은 ① 키워드 없는 검색의 개수 쿼리(범위 안 수만 행을 센다), ② 시드 키워드의 낮은 선택도(이름 40종이 반복돼 키워드마다 전국 5% = 15,000행 후보)다. 실제 데이터는 키워드 선택도가 더 높을 것으로 보이지만 확인하지 않았다. 개수 쿼리는 "다음 쪽 있음" 방식이나 상한(예: 1,000건 이상은 "1,000+")으로 바꾸는 것이 다음 후보다(API·화면 변경이라 이 브랜치 범위 밖).

## 재현

```bash
# 3만(기존 시드) 또는 30만
docker compose -f infra/docker-compose.load.yml up -d --build
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -urental -prental --default-character-set=utf8mb4 --init-command="SET @item_count=300000" rental_load < scripts/load/seed.sql
# (선택) 운영과 비슷한 버퍼 풀
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -uroot -proot -e "SET GLOBAL innodb_buffer_pool_size = 805306368"

# 구성 바꾸기(예: F2로 되돌리기) → ANALYZE TABLE items → 아래 측정
node scripts/load/query-index/gen-queries.mjs box 37.580096806849 126.984784487479 37.44936579281 127.058247079805 > q.sql   # F4는 mbr, S2는 fulltext
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -uroot -proot rental_load < q.sql > explain.txt
node scripts/load/query-index/summarize-explain.mjs < explain.txt
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -uroot -proot rental_load < scripts/load/query-index/write-cost.sql
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 -e THINK=0 -e RAMP=30s -e HOLD=2m -e ONLY=feed /scripts/read-mix.js
docker compose -f infra/docker-compose.load.yml run --rm k6 run -e VUS=100 -e THINK=0 -e RAMP=30s -e HOLD=2m -e ONLY=search /scripts/read-mix.js
# S2 결과 동일성
node scripts/load/query-index/fulltext-equality.mjs > eq.sql
docker compose -f infra/docker-compose.load.yml exec -T mysql mysql -uroot -proot rental_load < eq.sql
```

PowerShell에서는 `<`·`>` 리디렉션 줄을 `cmd /c "..."`로 감싼다(`scripts/load/README.md`). 이 브랜치의 앱은 V22로 F3·FULLTEXT가 이미 있으므로, F0~F2·S0을 다시 재려면 해당 인덱스를 지우거나(S0은 develop 이미지가 필요) 구성을 ALTER로 바꾼다.
