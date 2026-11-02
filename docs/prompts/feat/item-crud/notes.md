## 요약
- POST /api/items, GET /api/categories, GET /api/items/deposit-guide, GET /api/items/{itemId}, GET /api/items/feed, GET /api/members/me/items, GET /api/items/{itemId}/rentals 구현.
- 주요 파일: item/service/ItemCreateWriter.java, ItemQueryService.java, item/repository/ItemListingRepositoryImpl.java, ItemViewRepository.java, payment/service/DamagePolicyQueryService.java (backend/src/main/java/com/team7/rental 기준).
- V8 보증금 정책 32개, V9 검증된 행정동 3,565개 적재. 기존 지역 ID·참조 보존. 좌표 미해결 출장소 65개 제외(open-issues #36).
- 사용자 Windows/Java 21에서 spotlessApply test 성공(14분 19초), generateOpenApiDocs 성공(30초). 실제 카카오 외부 API 테스트는 기본 test 제외.
- 남은 일: 출장소 65개 좌표 해결, 수정·삭제·검색(REQ-007/008/010) 및 후속 REQ 기능. 이번 PR은 데모 요청의 백엔드 006/009/011/020 범위이며 프론트 화면 완료를 뜻하지 않는다.

## 결정
- 기존 ItemCreateRequest 검증기를 서비스에서도 재사용한다. 이미지 개수·용량은 app.policy.item.images로 관리한다.
- 업로드 중 또는 저장 트랜잭션 커밋 중 예외가 나면 이번 요청에서 업로드한 파일을 삭제한다. 정리 실패는 로그로 남기고 원래 오류를 보존한다.
- 카테고리 목록은 메모리에 캐시하되 활성 여부 검사는 DB에서 한다. 관리자 카테고리 변경 구현 시 커밋 후 캐시 무효화가 필요하다.

## 검증
- ItemCreateIntegrationTest: 등록 저장, 미인증 회원, 사진 누락, 위조 이미지, 기간 경계 중복, 없는 카테고리 (6개).
- ItemCreateCleanupTest: 썸네일 업로드 실패·DB 저장 실패 시 정리와 원래 예외 보존 (2개).
- 사용자 Windows/Java 21 환경에서 `gradlew.bat spotlessApply test` 성공(2026-11-02, 14분 19초). 기본 test 작업 기준이며 external 태그 테스트는 제외된다. `generateOpenApiDocs`도 성공(30초).

## 상세 조회·조회수
- 공개 응답에 itemPrice, 이메일을 넣지 않는다. 신뢰점수·최근 본 상품은 기존 DTO/도메인 명세의 후속 REQ를 따른다.
- 회원은 ID, 비회원은 접속 IP의 SHA-256으로 구분한다. X-Forwarded-For를 직접 신뢰하지 않는다. 운영 프록시 뒤에서는 신뢰 가능한 프록시에서만 실제 주소를 전달하도록 서블릿 설정을 확인해야 한다.
- Redis Lua에서 24시간 중복 키와 카운터 증가를 원자 처리한다. viewcount는 증분 대신 DB 기준값 포함 누적값으로 유지한다. DB 반영은 큰 값으로만 갱신하여 커밋 후 ACK 전 장애에도 재시도가 멱등이다.
- 5분 배치에 ShedLock을 적용하고 Redis SSCAN으로 대기 목록을 순회한다. DB 커밋 후 Redis 값이 동일할 때만 대기 목록에서 제거한다. 중간에 새 조회가 오면 다음 배치가 처리한다.
- Redis 전체 데이터 소실 시 아직 DB에 반영되지 않은 조회수와 중복 키는 복원할 수 없다. 운영 Redis 영속화/백업 설정이 필요하다.
- ItemDetailIntegrationTest: 공개 응답, 숨김/삭제 권한, 회원/IP 중복, TTL, 동시 조회, DB 반영 재시도 및 배치 중 새 조회 총 8개. 사용자 PC 전체 테스트 실행에서 통과했다.

## 피드·내 물품·예약 현황
- REQ-042 전에는 range 입력과 무관하게 NORMAL 3km. 물품의 등록 동네 ID로 걸러내지 않고 거래 장소 좌표로 범위를 계산한다.
- 피드는 bounding box 선필터 + MySQL ST_Distance_Sphere 정확 필터, bumpedAt/id 내림차순 커서로 20개를 반환한다. DB 함수 결과는 Hibernate 타입 판정을 위해 double로 캐스팅한다.
- ItemListingRepositoryImpl은 물품과 대여의 조회 전용 상관 서브쿼리로 대여 중 여부/예약 건수를 계산한다. 다른 도메인 Repository 호출 없이 QueryDSL projection으로 받고, 동네 이름은 RegionService에서 일괄 조회해 N+1을 피한다.
- RENTING 탭은 RENTING/OVERDUE 존재, AVAILABLE은 그 외 공개 물품. HIDDEN은 기존 API 계약대로 REQ-044 구현 전 빈 목록.
- 예약 건수는 시작일 >= 오늘이고 RESERVED 또는 만료 전 PENDING_PAYMENT만 센다. 예약 현황은 취소 포함 전체를 반환하며 활성 그룹 시작일 오름차순 → 과거 그룹 시작일 내림차순, 동률은 ID 오름차순.
- 조회수는 내 물품 목록에서 DB 반영된 값을 표시하므로 상세와 최대 5분 차이 날 수 있다.
- ItemListingIntegrationTest 10개 추가: 커서 동률·반경 경계·대각선·카테고리·기본 동네·오류·상태 탭·예약 건수·소유권·예약 순서.

## 권장 보증금·정책 시드
- V8에 카테고리 8개 × A~D 4등급 = 32개 정책. 카테고리 ID 하드코딩 없이 code로 참조한다.
- payment의 DamagePolicyQueryService를 통해 물품 가격 × 비율을 계산한다. 비율은 DB DECIMAL, Java BigDecimal을 사용한다.
- 원 미만 절사. 안내 금액을 등록 보증금 상한(300만원)으로 자르지 않으며 등록 시 기존 금액 검증은 유지한다. 관련 결정을 policies.md와 open-issues.md에 기록했다.
- ItemDepositGuideIntegrationTest: 카테고리 8종, 0원/절사, 최대 물품가, 인증/입력 오류, 비활성 카테고리, 시드 등급/특수 정책 확인(총 13회). 사용자 PC 전체 테스트 실행에서 통과했다.
- 행정동 공식 자료: 행정안전부 2026-10-30 게시물의 jscode20261026.zip 확인. 사용자가 공식 ZIP을 제공했고 읍면동명 존재·말소일자 없음 기준 3,630개 코드를 추출했다. 해당 자료의 좌표는 카카오 H 코드 대조 후 V9에 반영했다. Kakao 키는 공유하지 않고 사용자 PC에서만 사용한다.

## 행정동 좌표 수집 준비
- scripts/regions에 공식 코드 CSV·출처 해시·Windows PowerShell 수집 스크립트 추가. 카카오 H 코드가 10자리 공식 코드와 일치하는 경우에만 H 좌표 저장. 법정동/이름만 일치하는 결과로 대체하지 않는다.
- API 키는 사용자 PC의 infra/.env에서 읽는다. 성공 즉시 체크포인트 저장, 재실행 시 성공 건 건너뜀. 실패/미조회 코드는 별도 보고하고 DB에는 쓰지 않는다.
- 원본 텍스트의 300바이트 고정 길이와 CP949 필드 경계, 추출 3,630건의 10자리 코드·유일성을 확인했다. PowerShell 실제 카카오 호출은 사용자 PC에서 완료했다. 미해결 출장소 65개가 있어 전국 코드 전체 지원으로 표시하지 않는다.

## 행정동 좌표 수집 1차 결과
- 사용자 PC 실제 호출: 3,630개 중 3,563개 H 코드 매칭. 업로드 결과를 공식 CSV와 대조하여 코드·이름·시도·시군구 일치, 코드 중복 없음, 좌표 범위, 성공/미해결 목록이 원본 전체를 정확히 분할함을 확인했다.
- 미해결 67개 = 출장소 65개 + 상일제1동/상일제2동. 지원하지 않는 코드인지 검색어 문제인지는 아직 확정하지 않았다. 검색어 제+숫자 정규화·주민센터·일반 키워드를 추가했고 코드 일치 조건은 유지한다. 재조회에서 상일제1·2동이 해결되어 아래 V9 적재 범위를 확정했다.

## V9 행정동 적재 확정
- 재조회 결과 상일제1·2동 해결. 공식 코드 3,630개 중 검증된 3,565개 적재, 남은 65개는 모두 출장소이며 미해결로 문서화. 전국 코드 100% 지원이라고 표시하지 않는다.
- V9는 code 기반 upsert, 기존 ID·created_at·회원/물품 참조 유지. 자료에 없는 기존 지역도 삭제하지 않는다. 좌표 임의 생성/상위 코드 대체 없음.
- 원시 결과·출처·제외 목록은 docs/data/regions, 검증/재현 생성기는 scripts/regions/build-seed.py.
- IntegrationTestSupport 데이터 정리를 시작 전으로 이동하여 첫 테스트도 운영 시드와 동일 코드 fixture가 충돌하지 않게 함. RegionSeedIntegrationTest 4개(시드, 검색, 재적재 ID/참조 보존, 미해결 제외) 추가. 사용자 PC 전체 테스트 실행에서 통과했다.
