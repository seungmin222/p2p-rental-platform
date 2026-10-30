# feat/fe-item-pages 결정 기록

SCR-03 홈(동네 피드, REQ-009) · SCR-04 검색(REQ-010) · SCR-05 물품 상세(REQ-011)와 그 공통(`features/item/` api·queries·상수·목, 공개 API). 백엔드 물품 로직(`feat/item-crud`·`feat/item-feed-search`)은 아직 미구현이라 `docs/openapi.json` 형태 그대로 MSW 목으로 개발했다. fe-region·fe-rental-pages·fe-auth-region notes의 결정을 그대로 따랐고, 아래는 이 브랜치에서 새로 정했거나 구체화한 것만 적는다.

## 범위·분할
- **SCR-06 등록·수정·삭제(REQ-006~008)와 `/me/items` 삭제 메뉴는 `feat/fe-item-form`으로 분리**(사용자 결정). 변경 파일이 약 44개로 30개를 넘어서. 이 브랜치는 공통 + SCR-03·04·05(`origin/develop` 대비 **50개**: 코드 29개·테스트 10개·목 8개·문서 3개 — 계획의 약 30개를 넘었다. 검색 필터를 REQ-010 범위 전체로 넓힌 것(`SearchFilterSheet`·`searchParams`·`filterCalendar`와 테스트)과 목 저장소 분리(`rentalStore.js`)가 늘린 몫. 나눈 파일은 서로 의존해 더 쪼개지 않았다). git-workflow.md §2에 `feat/fe-item-form` 행을 더했다.
- 분리된 동안 상세에서 내 물품이면 예약하기 대신 **"내 물품 관리"(`/me/items`) 링크만** 둔다(수정·삭제 버튼은 폼 브랜치에서). 빈 껍데기인 `/items/:id/edit`로 가는 버튼을 새로 만들지 않으려고.
- 홈의 "+ 물품 등록" FAB은 시안대로 두었다. 등록 화면(`/items/new`)은 폼 브랜치 전까지 자리표시 제목만 있다(`/me/items`의 "수정"이 이미 같은 상태).
- 만들지 않은 것: REQ-046·060·064·065는 모두 **3순위**(requirements v1.0·item.md·api.md가 일치), REQ-022(관심)·030(지도)·채팅은 2순위.

| 시안에서 뺀 것 | 이유 |
| --- | --- |
| "이런 물품은 어때요?" | REQ-060, 3순위 |
| 알림 벨 | 알림 화면이 2순위(라우터에 자리표시만) |
| 관심 하트·관심 수, 카드의 "관심 n · 채팅 n" | REQ-022·채팅 2순위(필드는 openapi에 있으나 옮기지 않음) |
| 신뢰 점수·거래 횟수 | REQ-054 3순위, `owner` 응답에도 없음 |
| "채팅하기" | 채팅 화면이 라우터에 없음 |
| "끌어올림 n 전" | REQ-045 3순위 → "동네 · n 전"(`bumpedAt`) |
| 거래 장소 지도 | REQ-030 2순위 → 주소 텍스트 |
| 범위 선택·"범위 넓히기" 안내 | REQ-042 3순위(`range`는 보내지 않음, 서버가 NORMAL) |
| 최근·인기 검색어, 자동완성, 거리순 | REQ-046·064·065 3순위 |

## 구조
- `features/item/`: `api.js`(+test, `toItemCard`·`toItemDetail` 추가) · `queries.js`(`useCategories`·`useFeed`·`useSearch`·`useItemDetail`) · `constants.js` · `errorMessages.js` · `homeRegion.js`(+test) · `useFeedRegion.js` · `searchParams.js`(+test) · `filterCalendar.js`(+test) · `itemDisplay.js`(+test) · `components/`(`ItemCard`·`ItemListView`·`CategoryChips`·`RegionGate`·`SearchForm`·`SearchFilterSheet`·`ItemImageCarousel`·`ItemInfo`).
- 홈 피드와 검색 결과가 **`ItemListView`·`ItemCard`를 같이 쓴다**(스켈레톤·빈 상태·오류·무한 스크롤 끝이 같음). 처음 불러오기 실패만 전체 ErrorState, 이미 목록이 있으면 재조회 실패는 토스트(`useRefetchErrorToast`).
- `shared/api/client.js`: 공개 GET에 `/items/feed`·`/items`·`/categories` 추가. **`GET /items/{itemId}`는 넣지 않았다**(사용자 결정): 토큰이 있어야 `isOwner`가 로그인 사용자 기준이다. 피드·검색은 `regionId`를 항상 보내므로 토큰이 필요 없다(`client.test.js`에 헤더 확인).
- `shared/ui/Calendar`에 **`readOnly` prop**(날짜를 고를 수 없고 버튼이 아닌 칸, 안내 문구 없음)을 더했다 — 상세의 대여 캘린더가 "기존 Calendar 재사용"이라서. 같은 컴포넌트에서 **범례는 라벨이 있는 상태만** 그린다(검색 필터의 기간 선택에는 "예약됨"이 없어서 라벨을 안 넘기면 빠짐).
- `app/layout/BackButton`을 `PageHeader`에서 꺼냈다(이미지 위에 얹는 상세의 뒤로 가기가 같은 동작 — 앱 첫 화면이면 홈으로 — 을 써야 해서).
- 목 저장소를 `mocks/rentalStore.js`(db·기간 점유 판정)로 옮겼다. 검색 목이 "기간이 예약과 겹치지 않는 물품"을 계산하려면 대여 db를 읽어야 하는데, `item.js`가 `rental.js`를 import하면 순환(`rentalSeed`→`item.js`)이라 둘 다 `rentalStore.js`를 보게 했다. 동작은 그대로(`rental.js`는 import만 바뀜).

## 결정
- **홈 동네 결정(`resolveFeedRegion`, 순수 함수)**: 로그인(AT 있음)이면 `/members/me`의 `primaryRegion`(인증이 만료·미인증이어도 피드는 보여 줌 — item.md REQ-009에 인증 요건이 없어 같은 기준), 비회원이면 `rental.guestRegion`. 로그인한 사용자에게는 임시 동네를 쓰지 않는다(fe-region 결정 유지). 둘 다 없으면(대표 동네 없는 회원 포함) 피드 대신 "동네를 설정하면 이웃의 물품을 볼 수 있어요" + `/regions` 버튼. **자동으로 옮기지 않는다** — 홈 → `/regions` → 뒤로 가기가 홈으로 돌아와 다시 옮겨지는 루프가 생기므로.
- **로그인·로그아웃 캐시(사용자 요청 확인)**: `useLogin`·`clearLocalSession`이 이미 `queryClient.clear()`를 부르므로 `['items', id]`(isOwner)가 이전 사용자 기준으로 남지 않는다. 별도 무효화는 추가하지 않았다. 헤드리스: 비회원으로 본 103번(예약하기) → 로그인 → 같은 화면이 "내 물품 관리"(재요청 발생), 로그아웃 후 다시 "예약하기".
- **로그아웃 직후 홈이 401로 로그인 화면에 가던 문제(헤드리스에서 발견)**: 로그아웃은 홈으로 먼저 이동한 뒤 AT를 지운다(fe-auth-region 결정). 홈이 내 정보(`GET /members/me`)를 다시 부르면 서버가 이미 세션을 끝낸 경우 401 → 재발급 실패 → `/login?redirect=/`로 가 버렸다(이전 notes의 "홈에 쿼리가 붙으면" 메모가 이 경우). `useMe({ enabled, refetchOnMount })`를 더해 홈·검색은 `enabled`를 로그인 여부로, `refetchOnMount`를 `refetchIfInvalidated`(`query.state.isInvalidated`)로 부른다. 처음에는 `false`였는데 리뷰에서 바꿨다: `/regions`에는 내 정보 구독자가 없어 동네 뮤테이션의 무효화가 표시만 남기는데 `false`는 그 표시도 무시해, 대표 동네를 바꿔도·동네를 처음 추가해도 홈이 이전 값을 보여 줬다. 로그아웃은 무효화를 거치지 않아 다시 부르지 않는다. 다시 받는 동안(`isMeOutdated`)은 이전 동네의 피드를 부르지 않도록 로딩으로 둔다. 또 `clear()`가 화면에 떠 있는 쿼리를 캐시에서만 떼어 로그아웃 직후 이전 내 정보가 남아 있을 수 있어서, `resolveFeedRegion`은 로그인 여부가 false면 `me`를 보지 않는다(테스트 있음).
- **로그아웃 틈(리뷰 반영)**: `/me`의 내 정보가 로딩·실패 상태면 받아 둔 데이터가 없어 위 `refetchOnMount`와 무관하게 홈이 처음 불러오기를 해, 401 → 로그인 화면으로 갔다. **요청을 내지 않는 쪽**으로 했다: 로그아웃 뮤테이션이 진행 중이면(`useIsLoggingOut`, onSuccess의 이동까지 pending) 홈·검색이 내 정보를 부르지 않고, 시작할 때 진행 중인 내 정보 요청을 `cancelQueries`로 끊는다(`fetchMe`가 signal을 받음. 로그아웃 실패면 다시 받음). "401이어도 리다이렉트 안 함"은 재발급 경로에 "로그아웃 중" 상태를 넣어야 하고 공통 인터셉터가 복잡해져서 택하지 않았다. 알려진 한계: 로그아웃 중 `/me`의 포커스 재조회와 이미 재발급 단계에 들어간 요청은 막지 못한다(시간 창이 매우 좁다).
- **재발급 실패도 캐시를 지운다(리뷰 반영)**: `setUnauthorizedHandler`(`app/router.jsx`)가 로그인 화면으로 보낸 뒤 `clearLocalSession`을 부른다. AT만 지우면 비회원으로 돌아온 상세에 이전 사용자의 `isOwner`("내 물품 관리")와 내 대여 캐시가 남기 때문이다. `['items']`·`['members','me']`만 고르지 않고 전부 지우는 것은 로그아웃과 같은 기준을 쓰고, 대여 목록처럼 사용자에 묶인 다른 키를 빠뜨리지 않으려고.
- **홈 카테고리는 `?category=`**(뒤로 가기 유지), 선택하면 새 queryKey라 처음 페이지부터 다시 받는다. 헤더 왼쪽 동네 이름 → `/regions`, 오른쪽 검색 아이콘 → `/search`. 카테고리를 못 받아도 "전체" 칩은 보인다.
- **피드는 커서, 검색은 page**(openapi 그대로). 커서용 `getNextCursorParam`(`shared/lib/listParams.js`)을 더했다: `hasNext`가 false이거나 `nextCursor`가 없으면 undefined → 더 부르지 않는다. 첫 페이지의 pageParam은 `null`(→ `cursor` 생략).
- **검색 조건은 전부 URL 쿼리**(`searchParams.js`): 읽을 때마다 걸러 틀린 값은 없는 것으로 보고(가격은 0 ~ 1,000,000 정수, 최소 > 최대면 둘 다 버림, 기간은 시작·종료가 함께일 때만, 없는 날짜 거름), 기본값은 주소에 넣지 않으며, 같은 값이 queryKey. 바꿀 때는 replace. **필터는 openapi `GET /api/items`의 파라미터를 전부 넣었다**(사용자 결정): 카테고리 칩 + "필터" 버튼 → BottomSheet 하나(정렬·일 대여료 최소/최대·대여 희망 기간(`Calendar`)·"대여 가능한 물품만"). openapi에 없는 필터는 만들지 않았다.
- **조회 조건**: 키워드가 있거나 카테고리·가격·기간·대여 가능만 중 하나라도 있을 때만 조회한다. 정렬만 바꾼 것은 조건이 아니고, 공백뿐인 키워드는 trim되어 없는 것이다(지시의 "공백뿐이면 조회 안 함" + 카테고리만으로도 조회 가능하게 확장). 가격 입력은 시트 안에서 검사하고(최소 > 최대면 최대 칸 오류), 시작일만 고른 기간은 "적용"을 막는다(서버는 둘 다 보내거나 둘 다 생략).
- **필터 기간 Calendar**: 물품이 정해져 있지 않아 오늘부터는 모두 선택 가능, 지난 날짜는 선택 불가. 넘겨 볼 수 있는 달 전체를 합쳐 넘긴다(`buildFilterDaysInRange`, 리뷰 반영: 보이는 달만 넘기면 달을 넘긴 기간이 거부됐다). 가능 기간·예약과 맞는지는 서버가 거른다. 최대 일수는 `RENTAL_PERIOD.MAX_DAYS`(30), 앞으로 열두 달까지 넘겨 볼 수 있다.
- **디자인(시안 없음: SCR-04)**: Main.html 토큰만 썼다 — 입력 높이 48px·Line 테두리·모서리 8px, 칩 34px, 시트는 공통 BottomSheet. 검색 화면의 뒤로 가기·제목은 다른 탭 화면(SCR-12)과 같은 `PageHeader`.
- **일 대여료 0원 "무료" 표시**(사용자 요청: 허용될 때만): policies.md `item.price-per-day`가 "0원 또는 1,000원 ~ 1,000,000원(0원 = 무료 대여)"이라 0원 등록이 허용되므로 카드·상세 가격을 "무료"로 표시한다(`getPriceParts`). 보증금 0원은 카드에서 줄을 그리지 않고, 상세 하단 바에서는 "보증금 없음".
- **없는 카테고리는 "전체"(리뷰 반영)**: `?category=9999`는 카테고리 목록을 받은 뒤 칩·요청 모두 전체로 본다(`resolveCategory`). 목록을 받는 동안은 피드·검색을 부르지 않는다(없는 값으로 요청하면 서버가 400일 수도 있어서). 목록을 못 받으면 판단할 수 없어 URL 값을 그대로 쓴다. 주소는 다음에 조건을 바꿀 때 정리된다.
- **검색 입력은 다시 그리지 않고 값만 맞춘다(리뷰 반영)**: `key={keyword}`는 제출 뒤 포커스를 `body`로 떨어뜨렸다.
- **보증금 0원 표시를 나눈 이유(리뷰 후 유지)**: 카드는 좁은 목록에서 있는 정보만 보여 주려고 줄을 그리지 않고, 상세 하단 바는 예약을 결정하는 자리라 보증금이 없다는 사실 자체를 "보증금 없음"으로 알린다.
- **"대여 중" 배지**(`isRenting`)는 카드에만, 공통 `Badge tone="green"`(대여 중 상태 배지와 같은 토큰).
- **목 동네 이름을 행정동 시드 이름으로 맞췄다**(사용자 요청): 기존 물품 목 101~108의 동네·제공자 동네가 "송도동·연수동·동춘동"이라 홈 헤더(내 동네 "송도1동")와 카드의 동네가 다르게 보였다. 이제 `송도1동·연수1동·동춘1동`이고 물품마다 regionId를 가진다. 이 이름에 기대는 테스트·화면은 없었다(`features/item/api.test.js`는 자체 fixture를 쓴다). 상세의 거래 장소 좌표는 행정동 마스터의 중심 좌표, 주소 텍스트는 동네별로 다르게 둠.
- **상세**: 이미지 가로 스와이프(scroll-snap) + "n / N"(스크롤 위치로 셈, 이미지가 없으면 자리 면) + 이전·다음 버튼(리뷰 반영: 마우스로 넘길 수단이 없었다. `pointer-coarse`에서 숨기고, 끝에서는 포커스가 빠지지 않게 `aria-disabled`). 버튼은 즉시 이동(애니메이션 없음), 화살표 키는 직접 처리한다 — 브라우저 기본 스크롤은 snap과 겹쳐 왼쪽으로 가지 않는 경우가 있었다, 제공자 줄(닉네임·동네 — 거래 횟수·신뢰 점수는 응답에 없어 뺌), 제목, "카테고리 · 상태 · 경과 시간", 설명(`whitespace-pre-wrap`), 읽기 전용 캘린더(이번 달부터, 월을 넘기면 그 달만 부름), 거래 장소 텍스트. 하단 바: 일 대여료·보증금 + 예약하기(`/items/:id/reserve`).
- **조회수·관심 수·좌표는 화면이 쓰지 않아 `toItemDetail`이 옮기지 않는다.** 1순위 응답의 `isWished`·`wishCount`도 마찬가지.

## 목 (`mocks/`)
- `feed.js`(카테고리·피드·검색, 공개라 AT를 보지 않음) · `itemLogic.js`(필터·정렬·커서·페이지·검증 순수 함수, +test) · `itemSeed.js`(201~, 표는 파일 머리) · `item.js`(101~108 + 시드, 상세 `isOwner`는 **AT가 있으면 그 회원 기준**, 없거나 만료면 false). 대여 목은 그대로 회원 1 기준이다.
- 시드: 송도1동 30개(피드 20 + 10, 같은 시각 등록이 여럿인 커서 경계, 무료·보증금 없음·대여 중 배지), 송도2동 7(1페이지), 연수1동 12, 동춘1동 5(삭제된 106 제외), 옥련1동 3, 청학동 0(빈 피드·빈 검색). 카테고리는 V3 시드 8개 그대로.
- 핸들러 순서: `/api/items/feed`가 `/api/items/:itemId`보다 먼저 맞아야 해서(`feed`가 itemId로 읽힘) `handlers.js`가 `feedHandlers`를 물품 상세보다 앞에 둔다.
- 검색: 키워드는 이름·설명 부분 일치(대소문자 무시), 정확도순은 이름 일치 > 설명 일치 > 최신순(openapi 설명), 기간은 "그 기간 전체가 가능 기간 하나에 들어가고 점유 대여와 하루라도 겹치지 않는 것"(경계 날짜도 겹침), `availableOnly`는 openapi·open-issues #6 설명대로(기간이 있으면 그 기간, 없으면 "오늘 이후 가능 기간이 남아 있고 오늘이 예약되어 있지 않음" — 리뷰 반영: 처음에는 오늘이 가능 기간 안이어야 한다고 더 엄격하게 구현했다). 검증 400: regionId 필수(목), 시작·종료 함께, 시작 > 종료(details endDate), 최소 > 최대(details minPrice), 깨진 cursor(details cursor 가정).
- 피드의 `regionId` 없음은 400으로 둔다 — openapi는 "생략하면 로그인 회원의 대표 동네"지만 프론트는 공개 API라 토큰을 보내지 않고 항상 regionId를 보낸다.

## 백엔드에 확인할 것
1. **`GET /api/items/{itemId}`는 openapi에 security가 없지만 토큰이 있으면 `isOwner`를 채워야 한다**(선택적 인증). 프론트는 상세에만 토큰을 보낸다. 비회원은 `isOwner` false.
2. **피드·검색의 `regionId` 생략**(로그인 회원의 대표 동네)은 프론트가 쓰지 않는다 — 공개 API로 부르므로 토큰이 없어 항상 `regionId`를 보낸다.
3. **검색 `keyword`가 없을 때 `ACCURACY` 정렬**: 목은 최신순으로 처리(이름·설명 일치가 모두 같아서). 서버도 같은지.
4. **검색 `minPrice`·`maxPrice` 상한**: openapi는 `minimum: 0`뿐이다. 프론트는 일 대여료 상한(policies `item.price-per-day` 1,000,000원)까지 입력을 받고 그 위는 보내지 않는다. 서버가 상한을 넘으면 400인지.
5. **`availableOnly`의 정의**(open-issues #6 확정): 목이 그 설명대로 구현했다. 서버도 앞으로 시작하는 가능 기간만 있는 물품을 포함하는지.
6. **깨진 `cursor`의 400 details**: openapi에는 `COMMON_INVALID_INPUT`뿐이다. 목은 details `cursor`로 가정(프론트는 서버가 준 `nextCursor`만 보내 화면에는 영향 없음).
7. **카드(`ItemSummaryResponse`)에 `regionId`·`condition`이 없다.** 화면은 동네 이름만 써서 문제없다.
8. 이미지가 없는 물품의 `thumbnailUrl`은 null로 가정(openapi 설명). 상세 `images`는 빈 배열.
9. **없는 `categoryId`**(피드·검색)가 빈 목록인지 400인지. 프론트는 카테고리 목록에 없으면 보내지 않지만, 목록을 받은 뒤 비활성이 된 경우에는 보낼 수 있다.
10. **지난 날짜 기간**(`startDate`가 오늘 이전, 30일 초과) 검색을 허용하는지. openapi는 시작 > 종료만 400이다. 주소를 손으로 고치면 그대로 보낸다.

## feat/fe-item-form에서 이어받을 것
- **좌표(`latitude`·`longitude`, 등록 필수)**: 지도(REQ-030)는 2순위라 만들지 않지만 좌표는 필수다(item.md: "지도 UI가 없으면 주소 검색 결과 좌표로 대체"). `primaryRegion`·동네 검색 응답에 중심 좌표가 없어 동네 좌표를 쓸 수 없다. **추천안: 주소 텍스트 입력 + "현재 위치로 가져오기" 버튼**(`features/region/geolocation.js`의 `getCurrentCoords` 재사용), 좌표는 폼 값에만 두고 TanStack Query 캐시·로그에 남기지 않는다(fe-region의 좌표 취급 규칙과 같게 — 요청 본문에는 당연히 들어간다). 카카오 주소검색은 키·외부 의존이 생겨 REQ-030과 함께. 폼 브랜치 시작 때 사용자와 다시 정한다.
- **권장 보증금**: `GET /api/items/deposit-guide?categoryId&itemPrice`(로그인 필요, 응답 `{ minDeposit, maxDeposit }`)를 카테고리·물품 가격이 유효할 때만 부르고 보증금 칸 hint로 안내한다. 범위 밖이어도 등록은 허용한다(item.md REQ-006).
- **수정 이미지 `imageOrder`**: 수정 화면은 `GET /api/members/me/items/{itemId}`(`ItemEditResponse`: `itemPrice`·`availablePeriods`·`images[].imageId` 포함)로 채운다. `ImageUploader` 값은 기존 `{ id: imageId, url }`, 새 `{ id: 'local-n', file }`이고 순서 = 배열 순서. 요청은 multipart(`request` JSON Blob + `images` 새 파일만)이며 `imageOrder`는 각 칸 `{ imageId }`(유지) 또는 `{ newImageIndex }`(images 파트의 0부터 순번), 목록에 없는 기존 이미지는 삭제된다. **이미지를 바꾸지 않았으면 `imageOrder`를 생략하고 `images`도 보내지 않는다**(둘 다 보내면 400). 수정은 바뀐 필드만 보낸다(`dirtyFields`), 가능 기간은 보내면 전체 교체.
- **쿼리 무효화**: 등록 → `itemKeys.feed()`·`itemKeys.search()`·`itemKeys.mine()`. 수정 → 응답이 `ItemEditResponse`라 상세 모양이 아니다: 수정 키 `['items', id, 'edit']`만 `setQueryData`, 상세·피드·검색·내 물품은 무효화. 삭제 → `['items', id]` `removeQueries` + 같은 무효화.
- **삭제 메뉴**: 상세(내 물품이면 이 브랜치의 "내 물품 관리" 링크 자리에 "수정"·"삭제")와 `/me/items` 카드 메뉴(fe-rental-pages가 미뤄 둠), `ConfirmModal`. `ITEM_HAS_ACTIVE_RENTAL`(409)은 `error.details`의 한 건씩(`reason`: "2026-11-03 ~ 2026-11-05 대여 예정 (rentalId 12)")을 문구로 보여 준다. 성공 → 상세에서는 홈, `/me/items`에서는 그 자리 + 토스트.
- **오류 문구**(`features/item/errorMessages.js`에 openapi 코드만 추가): `ITEM_NOT_OWNER`·`ITEM_HAS_ACTIVE_RENTAL`·`ITEM_PERIOD_CONFLICT`(details에 충돌 기간)·`REGION_NOT_VERIFIED`·`IMAGE_COUNT_INVALID`·`IMAGE_INVALID_TYPE`·`IMAGE_SIZE_EXCEEDED`·`COMMON_UNSUPPORTED_MEDIA_TYPE`·`RENTAL_LOCK_TIMEOUT`. `REGION_NOT_VERIFIED`(등록)는 "동네 인증 후 등록할 수 있어요" 토스트 + `/regions` 안내(fe-region이 넘긴 몫).
- **정책 수치**(`features/item/constants.js`, policies 키 주석과 함께): 이름 50자·설명 2,000자·이미지 1~10장·10MB(`ITEM_IMAGES`는 이미 있음)·일 대여료 0원 또는 1,000~1,000,000원(`ITEM_PRICE_PER_DAY.MAX`는 이미 있음)·보증금 0원 또는 1,000~3,000,000원·물품 가격 0~10,000,000원. 글자 수는 코드 포인트(`shared/lib/validators.js` `countCodePoints`).
- **목 쓰기 핸들러**: 지금 `mocks/item.js`의 `ALL_ITEMS`는 불변 배열이다. 등록·수정·삭제를 목에 넣으려면 메모리 db로 바꿔야 하고(`findMockItem`·`listMockItems`·`listMyMockItems`가 같은 db를 보게), 피드·검색·내 물품·상세가 바로 반영돼야 한다. 쓰기 API는 AT 검사(`authenticateMockRequest`), 소유자 검사(남의 물품 403, 없는 id 404, 숫자 아닌 id 400 `readPathId`), 등록 시 대표 동네 미인증이면 `REGION_NOT_VERIFIED`, 검증 400 details. 계산은 `mocks/itemLogic.js`에 순수 함수로.
- 홈 FAB의 `/items/new`와 `/me/items`의 "수정"이 가리키는 `ItemFormPage`는 지금 자리표시다.
- **예약 화면 id 통일**: `RentalReservePage.jsx`의 `Number(id)`를 `parseItemId`(`features/item/itemId.js`)로 바꾼다. 지금은 상세와 기준이 다르다.

## 확인
- `npm run lint`·`npm test`(35파일 470개)·`npm run build` 통과. 바뀐 파일만 `npx prettier --check`. 처음에 디렉터리째 `prettier --write`를 돌렸다가 줄바꿈(CRLF→LF)만 바뀐 파일이 많이 생겨 내용이 안 바뀐 파일은 `git checkout --`로 되돌렸다.
- **build 결과에 목 코드 없음**: `.env.local`에 `VITE_USE_MOCK=true`인 채로 빌드해도 `dist/assets/*.js`에 `msw`·`__mockAuth`·`mock-at`·시드 물품명·`MOCK_REGION_MASTER`·`createFeedSeed`·시드 비밀번호가 없다. `dist/mockServiceWorker.js`는 `public/`에서 복사되어 함께 나오지만 MSW 워커 코드뿐이라 시드가 없고, 운영 코드는 등록하지 않는다. JS 번들 574kB(리뷰 반영 전 571.69kB, fe-region 기록 549.21kB 대비 +22kB). 500kB 경고는 기존 "다음에"(fe-rental-pages notes) 그대로.
- 헤드리스(앱 내장 브라우저, `VITE_USE_MOCK=true`, 390px·360px, 홈·카테고리·검색·상세·없는 id 가로 넘침 없음):
  - 비회원 동네 없음 → 안내 → `/regions`에서 송도1동 선택 → 홈 피드(20건), 새로고침 후에도 유지.
  - 무한 스크롤: 첫 요청 `regionId=1101`, 끝에 닿으면 `cursor=…` 한 번(30건), 이후 여러 번 더 스크롤해도 **요청은 2번뿐**(마지막 페이지에서 더 부르지 않음). 카테고리 칩(촬영·영상 장비) → `?category=3`, 새 요청은 `categoryId=3`에 cursor 없음(처음부터), 상세 → 뒤로 가기 시 칩·`?category=3` 유지.
  - 검색: 공백만 제출 → 요청 없음·안내 유지, "텐트" 엔터 → `?q=텐트`, 요청 `regionId=1101&keyword=텐트&page=0`, 결과 1개. 필터 시트: 최소 5000 > 최대 1000이면 오류 문구 + "적용" 비활성, 최대 20000·낮은 가격순·기간(10/30~10/31) 적용 → 주소 `?q=텐트&sort=PRICE_ASC&maxPrice=20000&startDate=2026-10-30&endDate=2026-10-31`, 요청도 같은 파라미터, "필터 3". 결과 없음 문구("‘없는물품’에 맞는 물품이 없어요"). 시트 안 가로 넘침 발견 → 수정(Calendar 월 이동 버튼의 `-mr-2`가 8px 밀려 나감, 시트 스크롤 영역에 좌우 8px 안쪽 여백).
  - 상세: 이미지 "1 / 1", 제공자·카테고리·상태·설명, 읽기 전용 캘린더(지난 날짜 흐림), 비회원·남의 물품은 예약하기(`/items/103/reserve`), 로그인한 회원 1의 103·108은 "내 물품 관리", 이미지 없는 108은 자리 면, 없는 id(`/items/999`)와 숫자가 아닌 id(`/items/abc`, **요청 0번**)는 ErrorState + 홈으로.
  - 로그인한 회원 2(연수1동)는 홈 헤더 "연수1동"·요청 `regionId=1103`(저장된 임시 동네 송도1동은 무시), 회원 3(동네 없음)은 임시 동네가 저장돼 있어도 안내 화면. 로그아웃(15초 뒤라 내 정보가 stale인 상태) → `/` 피드 20건(고치기 전에는 `/login?redirect=/`).
  - 리뷰 반영 확인(360·390px, 패널이 숨겨져 프레임이 멈추므로 스크롤은 실제 입력 + 스크린샷으로): 대표 동네 변경 → 헤더 연수1동·요청 `/members/me` 다음 `regionId=1103`(이전 동네 요청 없음), 동네 없는 회원 → 추가 → 송도2동 피드 7건, 달 넘김 10/31~11/2 → 적용 → 주소·요청 일치, 긴 id·`0` 요청 0번 + 찾을 수 없음, 이미지 5장 다음 버튼으로 5/5(끝에서 비활성, 포커스 유지)·3장 화살표 키 왕복, 390px(터치 흉내)에서 버튼 숨김. 내 정보 조회를 실패시킨 `/me`에서 로그아웃 → `/`에 내 정보 요청 0번(360px), 세션 만료 후 `/login` → 뒤로 → 103번이 처음부터 예약하기(360px), 내 정보를 늦춘 홈 헤더는 스켈레톤 → 동네 이름(360px), 없는 카테고리 → 전체·요청에 `categoryId` 없음(360px).
  - 대여 회귀: 회원 1의 `/items/101/reserve`가 상세 목을 읽어 "4인용 거실형 텐트 · 캠핑조아 · 연수1동"과 캘린더를 그대로 그리고, `/me/items`(대여 가능 탭의 108·107, 예약 현황·수정 버튼)도 그대로.
