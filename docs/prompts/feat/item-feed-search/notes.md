## 요약
- GET /api/items(필터링 검색, REQ-010) 구현. API 경로·DTO·에러 코드 변경 없음(openapi.json 그대로).
- 바꾼 파일: item/repository/ItemListingRepositoryImpl.java(search), 새 ItemSearchCondition.java, item/service/ItemQueryService.java(search·resolveRegionId·toSummaries를 피드와 공유), PolicyProperties에 search.page 바인딩 (backend/src/main/java/com/team7/rental 기준).
- 남은 일: 예약 생성·캘린더·기간 검색은 종료일이 지난 미반납 OVERDUE 물품도 오늘부터 받는다(open-issues #53, #7과 같은 뿌리). 결정 전까지 고치지 않았다.
- 남은 일: 최근·인기 검색어(046), 자동완성(064), 거리순(065)은 하지 않았다. 046은 ItemQueryService.search에서 keyword를 넘기는 자리에 붙이면 된다. 차단 사용자 제외(056)는 search의 조건 목록에 더한다.

## 결정
- 위치 조건은 피드와 같은 코드를 쓴다: 기준 동네 해석(ItemQueryService.resolveRegionId)과 거리 조건(ItemListingRepositoryImpl.withinRange). 범위는 REQ-042 전까지 피드처럼 NORMAL 고정.
- 기간 조건은 예약 생성(RentalReservationTxService.create)과 같은 판정을 QueryDSL 서브쿼리로 다시 쓴다: 가능 기간 하나가 [시작, 종료]를 덮고(exists), RentalOccupancyReader의 점유(ACTIVE_FOR_PERIOD 대여, 결제 대기는 만료 전 + 만료 전 결제 대기 연장)와 양 끝 포함으로 겹치지 않는다(not exists). item 패키지가 rental Repository를 부르지 않는 기존 원칙 때문이다. 점유 조건을 바꾸면 세 곳(findOccupying, findPendingOccupying, 검색)을 같이 바꾼다.
- availableOnly는 기간이 있으면 기간 조건 그대로다. 기간이 없으면 오늘 이후 가능 기간이 남아 있고, 오늘 하루가 점유되지 않았고, 대여 중(RENTING·OVERDUE, 피드 배지와 같은 조건)이 아닌 물품이다. 대여 중 제외는 #6 문구에 더한 것(사용자 결정 A안): 종료일이 지난 미반납 연체는 날짜로는 오늘을 점유하지 않아 "대여 가능"으로 보이기 때문이다. 기간 검색은 예약 판정과 다르게 만들지 않았다.
- 검색어는 앞뒤 공백을 지우고 비면 없는 것으로 본다. 문자열 전체를 한 덩어리로 이름·설명 LIKE 부분 일치(MySQL 기본 콜레이션이라 대소문자 무시). %·_는 '!'로 이스케이프한다(백슬래시는 MySQL 문자열 이스케이프와 겹친다).
- 정렬: ACCURACY = CASE(이름 일치 0, 설명만 일치 1) → bumpedAt desc → id desc, 검색어가 없으면 LATEST와 같다. LATEST = bumpedAt desc, id desc(피드와 같은 "최신" 기준). PRICE_ASC = pricePerDay asc, id desc. 마지막 키가 항상 id라 페이지 사이 순서가 고정된다.
- FULLTEXT를 쓰지 않았다: 한국어는 ngram 파서와 마이그레이션이 필요하고 부분 일치 결과가 달라진다. 명세가 "단순 규칙으로 시작"이라 LIKE + CASE로 두고, 성능은 실험 과제로 남긴다. 인덱스·마이그레이션 추가 없음(기간 서브쿼리는 기존 item_available_periods (item_id, start_date), rentals (item_id, status, start_date, end_date) 인덱스를 쓴다).
- search.page(20)를 PolicyProperties에 바인딩했다(policies.md에는 이미 있었고 설정 키만 없었다).

## 검증
- ItemSearchIntegrationTest(9개): 정확도순·검색어 없음 순서, %·_ 이스케이프, 최신순·가격순 동률, 카테고리·가격 범위, 기간(경계·가능 기간 밖·두 기간에 걸침·마지막 날 예약·취소/만료 결제 대기/완료 무시·결제 대기·결제 대기 연장), 기간 없는 availableOnly(기간 끝남·오늘 예약·미반납 연체·내일 예약), 범위 밖·숨김·삭제 제외와 대표 동네, 20건 페이지, 400 3종과 긴 검색어 200.
