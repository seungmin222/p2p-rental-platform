# chore/query-index-experiment 결정 기록

## 요약
- API 변경 없음. 마이그레이션 `V22__add_listing_indexes_to_items.sql`: 피드 인덱스를 `idx_items_status_bumped_at_id_latitude_longitude`로 대체, FULLTEXT ngram `idx_items_name_description` 추가. 결과 `docs/experiments/query-index.md`.
- 코드: `ItemListingRepositoryImpl.keywordMatches`가 한글·숫자 2~100글자 검색어를 `MATCH ... AGAINST`(구문)로, 그 밖은 LIKE로 찾는다. `global/config/MySqlFunctionContributor`(`match_against` 등록, `META-INF/services`). 테스트 `ItemSearchIntegrationTest.fulltextKeywordMatchesLikeSemantics`.
- 도구: `read-mix.js` `ONLY=feed|search`, `seed.sql` `@item_count`(`--init-command`), `lib.js` `ITEM_COUNT`, `scripts/load/query-index/`(쿼리 생성·EXPLAIN 요약·쓰기 비용·LIKE/FULLTEXT 비교).
- 남은 일: ① 30만에서 검색 p95 2.4s(768MB) — 개수 쿼리 상한·"다음 쪽 있음" 또는 캐시(API·화면 변경) ② 피드 Redis 캐시 ③ 운영 데이터 키워드 선택도 확인 ④ 운영 RDS 버퍼 풀 크기 확인(128MB 수준이면 F3 효과가 커진다).

## 결정

- **피드·검색 분리 측정**: `read-mix.js`에 `ONLY=feed|search`를 추가했다(기본은 기존 혼합). 분리하면 기존 혼합 수치(387.8/559.2ms)와 조건이 달라 F2·S0을 분리 상태로 다시 재서 새 기준선으로 썼다.
- **데이터 크기**: 3만으로 전 구성을 잰 뒤 `seed.sql`에 `@item_count`(기본 30000, `--init-command`로 지정)를 넣어 30만으로 주요 구성을 다시 쟀다. 대여 10만은 물품 1..30000에만 둔다(점유 조건이 크기와 무관하게 같도록).
- **잔여 예약 정리**: 앞선 예약 시나리오가 남긴 대여(id > 100000)를 지운 뒤 쟀다(README의 정리 명령). 검색 점유 서브쿼리 비용이 실행마다 달라지지 않게.
- **FULLTEXT는 한글·숫자 2~100글자 검색어만**: 영문은 기본 불용어('a'·'i'·'is' 등)가 든 2-gram이 색인에서 빠진다. 마이그레이션 세션에서 불용어를 꺼도 TRUNCATE(테스트 DataCleaner)·테이블 재생성 때 서버 기본값으로 다시 만들어져(부하 DB에서 확인) 결과가 LIKE와 달라질 수 있다. 서버 파라미터(RDS 기본 파라미터 그룹 제약)는 바꾸지 않고 앱이 영문·기호·공백·한 글자 검색어를 LIKE로 보낸다. 100글자 상한은 구문 검색 단어 수 한도(130글자 안팎에서 오류 191)보다 작게 잡았다.
- **S2 채택 조건 확인 방식**: `fulltext-equality.mjs`로 KEYWORDS 16개 + 경계 사례 5개 × 동네 3곳 × 범위 3개의 LIKE/MATCH id 집합을 비교했다. 시드 설명이 모두 같아 ngram 특유의 경우(비인접 2-gram 등)는 통합 테스트(`fulltextKeywordMatchesLikeSemantics`)로 따로 확인했다.
- **F4(공간 인덱스)는 쿼리 변경 없이 DB에서만 비교**: 생성 컬럼 `location POINT SRID 4326`(STORED) + SPATIAL을 부하 DB에 걸고 대표 쿼리를 `MBRContains`로 바꿔 EXPLAIN만 쟀다(결과가 박스 쿼리와 같은지 확인). SRID 4326의 `ST_SRID(POINT(x, y))`는 x를 경도로, WKT 폴리곤은 (위도 경도) 순서로 읽는다.
- **F3 채택(F2 대체)**: 3만·버퍼 풀 768MB에서는 F2와 같고, 데이터가 버퍼 풀을 넘는 30만·128MB에서만 p95 −48~75%였다. 크기·쓰기 비용이 F2와 같아 손해 없이 낭떠러지를 없앤다고 보고 바꿨다. 앞 세 컬럼이 같아 F2는 지운다.
- **버퍼 풀 768MB 추가 측정**: 30만에서 PRIMARY(약 100MB)가 컨테이너 기본 버퍼 풀 128MB를 넘어 결과가 디스크 읽기에 좌우됐다. 운영 RDS 기본 공식(메모리×3/4)의 1GB 인스턴스 값으로도 재서 두 조건을 함께 적었다.
- **S0(30만·768MB)은 develop 이미지로**: 앱 쿼리가 바뀌어 같은 이미지로는 LIKE를 잴 수 없다. 부하 DB에서만 V22 인덱스와 flyway_schema_history 행을 지우고 develop 이미지를 띄워 쟀다.
- **F4 기각**: 옵티마이저가 피드에서 거의 고르지 않고(3만 드문 동네만), 검색에서 고르면 3배 느려지며 INSERT가 약 10배 느려진다. 앱 쿼리 변경 없이 DB에서만 비교했다.
- **인덱스 이름**: FULLTEXT도 conventions §4 `idx_{테이블}_{컬럼들}`을 따라 `idx_items_name_description`.
