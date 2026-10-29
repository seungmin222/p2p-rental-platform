# chore/member-item-api-contract 결정 기록

회원·물품 1순위 API(REQ-001~011, 020)의 **계약만**: 컨트롤러 + 요청·응답 DTO + Swagger + ErrorCode + 서비스 껍데기. 로직은 담당 백엔드가 `feat/member-auth`·`feat/member-region`·`feat/item-crud`·`feat/item-feed-search`에서 채운다(백엔드끼리 합의함). 목적은 프론트엔드가 `docs/openapi.json`으로 목을 만드는 것.

## 범위
- `GET /api/items/{itemId}/calendar`(REQ-012)는 이미 있어서 뺐다. `GET /api/items/{itemId}/rentals`는 없어서 넣었다(rental 도메인 `ItemRentalController`, Rental 태그).
- 2·3순위 엔드포인트·필드(약관 동의, 신뢰 점수, 관심, 숨기기·끌어올리기 API, 범위 설정 API)는 만들지 않았다. HIDDEN 탭·`range` 파라미터·`isWished`는 1순위 경로의 입력·응답이라 받되 "REQ-044/042/022 전에는 빈 목록/NORMAL/false"로 설명에 적었다.

## 서비스 껍데기 이름 (채울 브랜치)
| 서비스 | 메서드 | 브랜치 |
| --- | --- | --- |
| `AuthService` (새) | login · logout · reissue | feat/member-auth |
| `MemberService` (새) | signup / updateProfile · changePassword | feat/member-auth / feat/member-region |
| `MemberQueryService` (기존) | getMe | feat/member-region |
| `RegionService` (새) | search · getByCoord | feat/member-region |
| `MemberRegionService` (새) / `MemberRegionQueryService` (기존) | add · verify · changePrimary · delete / getMyRegions | feat/member-region |
| `CategoryService` (새) | getActiveCategories | feat/item-crud |
| `ItemService` (새) | create · update · delete | feat/item-crud |
| `ItemQueryService` (기존) | getDepositGuide · getDetail · getEditView / getFeed · search · getMyItems | feat/item-crud / feat/item-feed-search |
| `RentalHistoryService` (기존) | getItemRentals | feat/item-feed-search |

- **기존 `ItemQueryService`·`MemberQueryService`·`MemberRegionQueryService`(대여가 쓰는 조회 창구)에 메서드를 더했다.** conventions §2의 읽기 서비스 이름이 이것과 같아서, 같은 이름의 클래스를 하나 더 만들지 않았다. 클래스 주석을 "화면 조회 + 다른 도메인 창구"로 고쳤다.
- `AuthService`는 RT 쿠키를 다루려고 `HttpServletResponse`를 받는다. 로직 브랜치에서 쿠키 처리를 컨트롤러로 옮겨도 API는 바뀌지 않는다.
- **외부 호출이 들어갈 메서드는 트랜잭션을 걸지 않는다**(리뷰 반영): `MemberService.updateProfile`(이미지 업로드)·`MemberRegionService.verify`·`RegionService.getByCoord`(카카오 로컬)는 `@Transactional(propagation = NOT_SUPPORTED)`로 클래스의 readOnly를 끊고 "외부 호출 후 별도 트랜잭션에서 저장" 주석을 달았다. `ItemService`는 세 메서드 모두 업로드나 물품 락을 먼저 거쳐서 클래스 레벨 `@Transactional`을 뺐다(처음에는 create도 readOnly를 물려받고 있었다).

## 검증
- **수치 검증은 대여 선례(`ValidInspectionText`)와 같은 방식**: `PolicyProperties`에 `member`·`item` 섹션을 추가하고 yml `app.policy.member.*`·`app.policy.item.*`에 값을 두고, 요청 타입마다 클래스 수준 검증기(`ValidMemberText`·`ValidItemInput`)가 읽는다. 그래서 Swagger에는 maxLength 같은 수치가 안 나오고 `@Schema` 설명에 policies 키를 적었다. 수치가 아닌 규칙(@NotBlank·@Email·비밀번호 구성 @Pattern·좌표 범위)은 필드 어노테이션.
- PolicyProperties에는 **지금 검증기가 쓰는 값만** 넣었다(region.max-per-member, profile.image, item.images, 페이지 크기 키는 로직 브랜치에서 쓸 때 추가).
- 비밀번호: 공백 없는 ASCII 출력 문자(`!`~`~`)만, 영문·숫자·ASCII 기호를 각각 1개 이상(open-issues #26, 리뷰 반영. 처음에는 "영문·숫자·공백이 아닌 문자"를 기호로 봐서 한글·공백이 통과했다).
- 검증기는 위반을 모두 모아 details로 돌려준다(대여 검증기는 첫 위반에서 멈춤). 물품 폼은 필드가 많아 한 번에 보여 주는 편이 나아서.
- 물품 등록의 기간 시작일 ≥ 오늘은 `Clock`을 주입받아 검사한다. **수정 DTO 검증에는 이 검사를 하지 않는다**: 진행 중 대여를 덮는 구간(과거 시작일)을 유지해야 `ITEM_PERIOD_CONFLICT`가 안 나기 때문. 대신 기존 구간과 비교가 필요해 서비스에서 한다(아래 "로직 브랜치 할 일").
- 검색 기간은 **둘 다 보내거나 둘 다 생략**(한쪽만이면 400). "그 기간 전체가 가능 기간에 포함"은 한쪽만으로 정의되지 않아서. minPrice > maxPrice도 400(details minPrice), keyword 길이 제한 없음(open-issues #28).
- **요청 바디의 id가 가리키는 대상이 없으면 400, 경로 id가 없을 때만 404**(리뷰 반영): 비활성·없는 카테고리 → 400(details categoryId), 동네 추가의 없는 regionId → 400(details regionId, 처음엔 404였다). 경로의 남의·없는 내 동네 id는 404 `COMMON_NOT_FOUND`(존재를 드러내지 않음).
- 물품 수정은 위도·경도 중 하나만 보내면 400. 수정 응답은 수정 화면 조회와 같은 `ItemEditResponse`.
- 물품 수정·삭제는 예약과 같은 락을 쓰므로 `RENTAL_LOCK_TIMEOUT`(기존 코드)도 선언했다(삭제는 open-issues #27).
- 물품 소유자가 아니면 `ITEM_NOT_OWNER`로 통일(예약 현황 포함, open-issues #25). `RENTAL_OWNER_ONLY`는 대여 쪽에서 계속 쓴다.
- 로그인·재발급·로그아웃 응답에 Swagger `Set-Cookie` 헤더를 선언했다(속성, 로그아웃은 Max-Age=0).

## 명세 빈칸 결정 (사용자 확인, open-issues #16~22)
- 수정 화면 조회 `GET /api/members/me/items/{itemId}` 신설(api.md 1순위 행 추가).
- 409 목록은 `error.details` 재사용, 형식은 각 @Operation description에. **`BusinessException`에 details를 싣는 기능은 아직 없다** — `feat/item-crud`에서 추가해야 한다(ErrorResponse 형태는 그대로).
- 이미지 오류는 기존 `IMAGE_*`, item.md 수정. 이미지 수정은 `imageOrder`.
- 예약 현황은 PageResponse + 프론트 제안 정렬. 내 물품·예약 현황 페이지 크기 정책 키 `my-items.page`·`item-rentals.page`(20) 추가.
- 피드·검색 regionId 생략 = 로그인 회원 대표 동네 → 피드 인증 X → 옵션.
- `REGION_ALREADY_ADDED` 409 추가.
- 리뷰 후 결정(open-issues #23~28): damage_policies를 1순위로 당김(생성·시드 `feat/item-crud`), AT에 familyId claim 추가(구현 `feat/member-auth`), #25~28은 위 검증 항목.

## 그 외 정한 것
- **재발급 실패 코드 `AUTH_REFRESH_TOKEN_INVALID`(401)**. 프론트 `shared/api`는 401 상태만 보고 코드는 보지 않아서 맞출 값이 없었다. 쿠키 없음·서명 오류·만료·family 없음·재사용을 하나로 묶었다(프론트 처리는 모두 "로그인 화면으로"라 구분할 이유가 없음).
- **RT 쿠키 이름 `refreshToken`**(policies.md auth.refresh-cookie에 추가). HttpOnly라 프론트는 이름을 쓰지 않는다. Swagger에는 쿠키 파라미터를 숨겼다(브라우저가 자동으로 보냄).
- 로그아웃은 실패가 없어 `@ApiErrorCodes({})`.
- 응답 id 이름은 대여 API처럼 `itemId`·`memberId`·`rentalId`·`memberRegionId`(id 단독 아님). 카테고리도 `categoryId`.
- 물품 상세는 `regionName`(물품 동네)과 `owner.regionName`(제공자 대표 동네)을 둘 다 준다. 명세 "제공자 요약(동네명)"과 카드의 "동네명"이 서로 다른 값일 수 있어서.
- 이미지 응답은 `{ imageId, url, thumbnailUrl }`.
- 내 동네 응답의 인증 상태는 `isVerified`(유효기간 반영) + `verifiedAt` + `verifyExpiresAt`. 내 정보의 대표 동네 요약도 같다.
- 메시지에 수치를 넣지 않았다(`REGION_LIMIT_EXCEEDED` "더 이상 동네를 추가할 수 없습니다"): 정책 값이 바뀌면 메시지가 틀려지므로.

## 프론트 가정 반영
| 프론트 가정 (fe-rental-pages·fe-my-pages notes) | 결과 |
| --- | --- |
| `GET /api/items/{id}` `{ itemId, name, images[{imageId,url}], pricePerDay, deposit, owner{memberId,nickname,regionName}, isOwner }` | 그대로. 필드 추가만 있음 |
| `POST /api/auth/reissue` `{ accessToken }` | 그대로(만료 정보는 프론트가 안 써서 넣지 않음). 로그인도 같은 DTO |
| `GET /api/members/me/items?tab=&page=` PageResponse, 항목 `itemId, name, thumbnailUrl, pricePerDay, deposit, viewCount, wishCount, chatCount, upcomingRentalCount`, 기본 탭 AVAILABLE, itemId 내림차순, 20건, 만료된 PENDING_PAYMENT 미포함 | 그대로. 추가 필드 `regionName, status, isRenting, bumpedAt` |
| `GET /api/items/{itemId}/rentals?page=` PageResponse, 항목 `rentalId, startDate, endDate, status, renter{memberId,nickname}`, 403 `RENTAL_OWNER_ONLY`, 404 `ITEM_NOT_FOUND`, CANCELED 포함 | 그대로 |
| 예약 현황 정렬(목은 시작일 오름차순) | **다름**: 프론트 제안대로 다가오는·진행 중 오름차순 먼저, 완료·취소 내림차순 뒤에. 목 `mocks/pageList.js` `listItemRentals`를 바꿔야 함 |
| 예약 현황 남의 물품 403 `RENTAL_OWNER_ONLY` | **다름**: `ITEM_NOT_OWNER`(open-issues #25) |

- fe-rental-pages notes의 "대여 상세 `depositStatus`·차감 정보"는 2순위(REQ-025·049)라 이 브랜치 범위 밖이다.

## 로직 브랜치 할 일
- `feat/item-crud`: 물품 수정에서 **새로 추가하거나 앞으로 늘린 구간만** 시작일 ≥ 오늘을 검사한다(기존 구간과 비교해야 해서 DTO가 아니라 서비스).
- `feat/item-crud`: `BusinessException`에 details를 싣는 기능을 추가해 `ITEM_PERIOD_CONFLICT`·`ITEM_HAS_ACTIVE_RENTAL` 목록을 `error.details`로 내려 준다(`ErrorResponse` 형태는 그대로).
- `feat/item-crud`: `damage_policies` 테이블·시드(open-issues #23), 물품 삭제를 `lock:item` 안에서.
- `feat/member-auth`: AT claim `familyId`(open-issues #24).

## PR을 나누지 않은 이유
변경 파일이 30개를 넘지만(git-workflow §4) 하나로 올린다. 대부분 로직 없는 DTO·컨트롤러 껍데기라 회원·물품으로 나눠도 리뷰가 쉬워지지 않고, 프론트엔드는 두 계약이 모두 있어야 목을 맞출 수 있다. 리뷰는 커밋 단위(global → member → item → docs, 그 뒤 리뷰 반영 fix)로 보면 된다.

## 확인
- `./gradlew test` 통과(새 통합 테스트: 회원·물품 요청 검증 400 + details, 비로그인 401, `/v3/api-docs` 경로·REQ ID).
- `./gradlew generateOpenApiDocs`로 openapi.json 재생성. 기존 경로·스키마는 의미상 바뀌지 않음(파이썬으로 이전 파일과 비교). itemPrice는 `ItemCreateRequest`·`ItemUpdateRequest`·`ItemEditResponse`에만 있음.
