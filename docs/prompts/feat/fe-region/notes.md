# feat/fe-region 결정 기록

SCR-02 동네 설정·인증(`/regions`, REQ-004) 화면과 내 동네·검색·좌표 변환 MSW 목. 백엔드 동네 서비스는 아직 미구현이라 docs/openapi.json 형태 그대로 목으로 개발했다. fe-auth-region notes "feat/fe-region에서 이어받을 것"을 그대로 따랐고, 아래는 이 브랜치에서 새로 정했거나 구체화한 것만 적는다.

## 범위 밖
| 넣지 않은 것 | 어디서 |
| --- | --- |
| 홈의 "동네 미설정 → /regions" 유도, 피드의 regionId·임시 동네 사용 | feat/fe-item-pages |
| 물품 등록·예약의 `REGION_NOT_VERIFIED` 처리 | 물품·대여 화면(예약 쪽은 이미 있음) |
| 동네 범위(REQ-042) | 3순위 |
| 로그인할 때 임시 동네를 서버에 올리기 | 명세 없음 |

## 구조
- `features/region/`: `api.js` · `queries.js`(+test) · `constants.js` · `errorMessages.js` · `geolocation.js`(+test) · `coordsSlot.js`(+test, 찾은 좌표 칸) · `guestRegionStore.js`(+test) · `myRegions.js`(+test, 목록 계산 순수 함수) · `components/`(`RegionFinder` · `MyRegionList` · `MyRegionManager` · `GuestRegionPicker`). `pages/RegionPage.jsx`는 AT 유무로 두 화면 중 하나를 그린다.
- **`RegionFinder`를 로그인·비회원이 같이 쓴다**: 검색·현재 위치 찾기·결과 줄은 같고 "무엇을 하는지"만 다르다. 호출부가 `getSelectState`(버튼 라벨·비활성)·`onSelect`를 넘긴다. 좌표 칸도 여기 하나뿐이고 로그인일 때만 채운다(`isGuest`면 좌표를 받지 않음 — 비회원은 인증이 없어 필요 없다).
- **`useDebouncedValue`는 `shared/hooks`**: 피드 검색에서도 쓸 수 있는 범용 훅이라. 테스트 환경이 node(렌더러 없음)라 훅을 렌더하지 않고, 훅의 effect가 그대로 쓰는 `scheduleDebounce`를 effect 순서(이전 예약 취소 → 새 예약)대로 흉내 내 fake timers(`REGION_SEARCH_DEBOUNCE_MS` 전후)로 확인했다. 한계: useState·effect 배선은 단위 테스트가 보지 않아 헤드리스(입력마다 요청하지 않음)로 확인한다.
- `itemKeys.feed()`(`['items', 'feed']`)를 `features/item/queries.js`에 새로 두었다. 같은 키가 없었고, queryKey 규칙 `['items', 'feed', {regionId, range, categoryId}]`의 앞부분이다.

## 결정
- **목록 갱신**: 인증 응답(동네 하나)은 순서·대표를 바꾸지 않으므로 목록의 그 항목만 `setQueryData`(목록에 아직 없으면 다시 불러옴). 추가·삭제는 서버 순서·대표 승계를 따르려고 목록 무효화. 대표 변경은 응답이 목록 전체라 `setQueryData`. 셋 다 `['members', 'me']`도 무효화(exact — 하위인 목록까지 다시 부르지 않게).
- **뮤테이션은 목록 갱신까지 끝나야 끝난다**(onSuccess가 무효화 Promise를 돌려줌, 실패 쪽도 같음 — 아래) + **동네 뮤테이션이 하나라도 진행 중이면 목록 버튼 전체 비활성**(`useRegionMutating`, mutationKey `['region', ...]`): 삭제 variables의 `isPrimary`는 누른 시점의 목록 값이라, 대표 변경 직후 목록이 갱신되기 전에 누르면 틀린 값으로 피드 무효화를 정할 수 있어서. 줄 단위가 아니라 전체로 막는다(사용자 결정).
- **인증 onSuccess는 목록 재조회를 먼저 멈춘다**(`cancelQueries`): 추가 직후 재조회 응답이 인증 전 목록으로 늦게 덮어쓰지 않게.
- **추가 → 인증 연쇄(현재 위치로 찾은 동네)**: 추가 응답의 `memberRegionId`로 인증하고, `locate`는 좌표 칸에서 꺼낸 좌표를 한 번 돌려준 뒤 비운다(위치를 다시 받지 않음, MutationCache에 남는 함수가 좌표를 붙잡지 않게). 결과 문구 세 갈래: 둘 다 성공 "송도1동을 추가하고 인증했어요" / 추가만 성공 "동네는 추가했어요. " + 인증 실패 문구 / 추가 실패는 추가 오류 문구 + 좌표 삭제. **좌표가 없으면(검색으로 추가, 또는 칸이 이미 비었음) 인증을 부르지 않고** "추가했어요. 목록에서 인증해 주세요"로 끝낸다(리뷰 반영: 이전에는 LocationError로 만들어 "인증 실패"처럼 보였다).
- **좌표 칸(`coordsSlot.js` `createCoordsSlot`)과 토큰(리뷰 반영)**: 찾기 호출마다 토큰(증가 번호)을 받고 현재 토큰일 때만 좌표를 넣는다. 결과 표시도 같은 기준(뮤테이션 variables의 token == 현재 토큰, 토큰은 번호라 좌표 규칙과 무관). 비우는 때(토큰도 올림): (a) 새 검색 입력 (b) 검색 결과에서 무엇이든 고를 때 (c) 찾은 결과로 추가를 시도한 직후(성공·실패 무관, RegionFinder가 finally로 한 번 더) (d) 언마운트. 다시 찾으면 begin이 비운다. 이전에는 "찾은 순간 추가할 수 없으면 지움"이었는데, 찾은 뒤 목록이 바뀌면 틀려서 **누를 때 다시 판단**하도록 바꿨다. 찾은 결과의 "추가"는 목록이 로드되기 전에는 비활성이고, 이미 추가된 것이면 "추가하면 바로 인증돼요" 안내를 숨긴다. 칸 객체는 `useState(createCoordsSlot)`로 한 번 만들고 좌표는 객체 클로저에만 있다(state·Devtools에 보이지 않음).
- **위치 권한 거부는 화면 전체에서 안내하고 버튼은 막지 않는다**(사용자 결정): 사용자가 브라우저 설정에서 권한을 다시 켤 수 있어 누를 때마다 다시 시도한다. "현재 위치로 찾기"는 버튼 아래 문구, 목록의 "인증하기/다시 인증"은 토스트. 문구는 회원 "위치 권한이 없어 인증할 수 없어요. 검색으로 동네를 추가할 수 있어요", 비회원 "위치 권한이 없어요. 검색으로 동네를 골라 주세요"(리뷰 반영: 비회원에게는 인증이 없어서. `getLocateErrorMessage(error, { isGuest })`).
- **위치 옵션을 용도별로 나눴다 — fe-auth-region의 위치 옵션 결정을 인증에 한해 바꿈**: 목록의 인증은 `maximumAge: 0`(`VERIFY_GEOLOCATION_OPTIONS`), "현재 위치로 찾기"는 fe-auth-region 옵션 그대로(`LOCATE_GEOLOCATION_OPTIONS`, 1분 캐시). 이유: 불일치 뒤 그 동네로 옮겨 1분 안에 다시 인증하면 이전 위치가 돌아와 또 실패한다(헤드리스에서 발견). 추가 직후 인증은 찾을 때 받은 좌표를 그대로 쓴다.
- **`LocationError`(code `CLIENT_LOCATION_DENIED`·`_UNAVAILABLE`·`_TIMEOUT`·`_UNSUPPORTED`)는 ApiError 경로를 타지 않는다**: mutationFn 안에서 axios보다 먼저 던져져 401 인터셉터·재발급이 없고, 뮤테이션 기본 retry가 0이라 다시 시도하지 않는다(앱 QueryClient의 retry 함수는 queries에만 있고 status가 없는 LocationError는 거기서는 재시도 대상이 되지만, 위치는 쿼리에서 쓰지 않는다). 문구는 `getErrorMessage`가 code로 찾는다. `queries.test.js`에서 확인.
- **by-coord 404 문구는 따로**(`getLocateErrorMessage`): 같은 `COMMON_NOT_FOUND`가 내 동네 API에서는 "이미 삭제된 동네"라서. 내 동네 API의 404·`REGION_ALREADY_ADDED`·`REGION_LIMIT_EXCEEDED`는 목록이 서버와 어긋났다는 뜻이라 목록을 다시 불러오고, **onError가 그 Promise를 돌려줘 다시 받을 때까지 뮤테이션이 끝나지 않는다**(리뷰 반영: 실패 뒤 재조회 동안 버튼이 풀려 지난 목록으로 누를 수 있었다).
- **줄 버튼 aria-label에 동네 이름**(리뷰 반영): "송도1동 인증하기"·"송도1동 다시 인증"·"송도1동 대표로"·"송도1동 삭제", 검색·찾은 결과 "송도1동 추가"·"송도1동 선택". 눈에 보이는 글자를 그대로 이름 뒤에 둔다(음성 명령으로 "대표로"를 말해도 맞도록, 예시의 "송도1동을 대표 동네로" 대신).
- **동네 검색·좌표 변환에는 AT를 붙이지 않는다**(리뷰 반영): openapi에 security가 없는 공개 GET이라 `shared/api/client.js` `isPublicApi`에 `GET /regions`·`GET /regions/by-coord`를 더했다(쿼리는 빼고 경로로 판정).
- **인증 버튼 라벨**: 인증한 적 없으면 "인증하기", 있으면(유효·만료 모두) "다시 인증". 유효한 동네도 다시 인증하면 기간이 늘어나서.
- **최대 개수면** "동네는 2개까지 추가할 수 있어요. 기존 동네를 삭제한 뒤 추가해 주세요" 안내 + "현재 위치로 찾기"와 결과의 추가 버튼을 막는다(검색 입력은 둔다 — 추가됨 여부는 볼 수 있게).
- **비회원 임시 동네**: localStorage `rental.guestRegion`(앱 이름 접두사, 목 세션 `mock.session`과 같은 점 표기). Zustand persist, 값은 `{ regionId, name, fullName }`만(불러올 때도 다시 걸러 다른 필드·깨진 값은 버림). 고르면 토스트 + `/`로 replace(뒤로 가기로 선택 화면에 돌아오지 않게).
- **로그인해도 임시 동네를 서버에 올리지도, 지우지도 않고 로그인 사용자 화면에 보여 주지도 않는다**: 올리는 명세·API가 없고, 서버의 내 동네와 섞이면 무엇이 내 동네인지 헷갈린다. 지우지 않는 것은 로그아웃하면 다시 비회원 기준 동네로 쓰이기 때문.
- **검색 결과 줄은 fullName + 버튼**(같은 이름의 동이 다른 구에 있을 수 있어서). 결과 없음은 "‘키워드’에 해당하는 동네가 없어요". 검색은 `staleTime` 5분(행정동 마스터는 거의 바뀌지 않음).
- **"홈으로 가기"는 본문 맨 아래 primary 버튼**(하단 고정 바가 아님): 하단 탭이 있는 화면이라 바를 따로 두지 않았다.

## 목 (`mocks/`)
- `region.js`(검색·by-coord, 공개라 AT를 보지 않음) + `member.js`에 내 동네 API 5개(현재 AT가 아니면 401). 계산은 `regionLogic.js` 순수 함수(+test). `memberLogic.js`가 이미 커서 새 파일로 뺐고, 인증 만료 계산은 `memberLogic.toVerification`을 같이 쓴다. 행정동 시드는 이미 있는 `memberSeed.js` `MOCK_REGION_MASTER` 그대로(물품 목 동네명은 바꾸지 않음).
- **검색은 openapi대로 `name`만 부분 일치**(사용자 결정, 지시의 name·fullName과 다름 → 아래 "백엔드에 확인할 것").
- by-coord: 가장 가까운 시드 중심이 `MOCK_NEAREST_MAX_KM`(3) 안이면(**정확히 3km도 포함**) 그 동네, 아니면 404. verify도 같은 판정, 다르거나 어느 동네에도 없으면 400 `REGION_VERIFY_MISMATCH`.
- 검사 순서: 경로 id가 Long이 아니면 400(`readPathId`, `INVALID_PATH_ID`) → 본문 400(details lat·lng·regionId, `INVALID_BODY`) → 404(남의·없는 id) → 409·400 업무 오류. 첫 동네 자동 대표, 대표 삭제 시 남은 동네 중 먼저 추가한 것이 대표. 목록은 대표 먼저, 나머지는 추가 순서.
- `members/me`의 `primaryRegion`은 같은 메모리 db에서 계산하므로 바로 반영된다(헤드리스에서 대표 변경 → `/me` 요약 확인).

## 백엔드에 확인할 것
1. **검색 일치 범위**: openapi `GET /api/regions` 설명은 "행정동 이름에 keyword가 들어간 행정동"(name만), member.md는 "행정동 마스터 LIKE"로 대상 컬럼이 없다. 프론트·목은 name만으로 했다. fullName(시·구 이름)으로도 찾게 할지.
2. **검색 결과 순서**가 openapi에 없다. 목은 마스터 순서(regionId).
3. **목록에서 대표가 아닌 동네의 순서**가 openapi에 "대표 동네 먼저"만 있다. 목은 추가 순서로 가정.
4. **대표 삭제 시 승계할 동네**(남은 동네가 여럿일 때)가 명세에 없다. 지금은 최대 2개라 남는 동네가 하나뿐이지만, 목은 먼저 추가한 동네로 가정.
5. **keyword가 공백뿐일 때** 목은 400 `COMMON_INVALID_INPUT`(details keyword "공백일 수 없습니다", `@NotBlank` 가정). openapi에는 `minLength: 1`뿐이라 " "(공백 한 칸)을 통과시킬 수도 있다. 프론트는 공백을 지우고 비면 부르지 않아 화면에는 영향이 없다.
6. **숫자가 아닌 lat·lng의 details 문구**: 목은 없거나 숫자가 아니면 "널이어서는 안됩니다", 범위 밖이면 "-90 이상 90 이하여야 합니다"로 가정했다. 실제로는 타입 변환 실패(by-coord 쿼리)·`@DecimalMin/@DecimalMax` 문구가 다를 수 있다. 프론트는 이 문구를 보여 주지 않는다(브라우저 좌표만 보냄).
7. fe-auth-region notes의 1(by-coord GET 쿼리의 좌표가 접근 로그에 남을 수 있음)·2(verify 좌표가 어느 행정동에도 없을 때 응답)·3(추가 검사 순서)은 그대로 열려 있다.

## 확인
- `npm run lint`·`npm test`(349개)·`npm run build` 통과. 바뀐 파일만 `npx prettier --check`.
- **좌표 캐시 테스트**(`queries.test.js`): verify 성공·ApiError 실패, by-coord, 찾은 위치로 추가 → 인증 각각 뒤에 MutationCache(variables·data·error 등 state 전체)와 QueryCache를 깊이 훑어 표본 좌표가 숫자·문자열로 없는지 본다. API 대역이 그 좌표를 받았는지도 확인해 헛돌지 않게 했고, 일부러 variables에 좌표를 넣으면 실패하는 것도 한 번 확인했다. 좌표 칸 규칙(토큰·비우는 때·take 한 번)은 `coordsSlot.test.js`.
- **실패 뒤 목록 갱신 테스트**(`queries.test.js`): `REGION_LIMIT_EXCEEDED`면 목록 재조회가 끝날 때까지 뮤테이션이 진행 중, 목록과 무관한 400은 다시 받지 않음.
- **build 결과에 목 코드 없음**: `.env.local`에 `VITE_USE_MOCK=true`인 채로 빌드해도 `dist/assets/*.js`에 `msw`·`__mockAuth`·`mock-at`·시드 동네명·시드 좌표·`MOCK_NEAREST`·시드 비밀번호가 없다. JS 번들 549.21kB(fe-auth-region 기록 532.5kB 대비 +17kB). 500kB 경고는 기존 "다음에" 그대로.
- 헤드리스 Chrome(CDP, `VITE_USE_MOCK=true`, 390px·360px 각각 81개 확인 통과, 가로 넘침 없음). 위치는 `Emulation.setGeolocationOverride`, 권한은 `Browser.setPermission`:
  - 회원 3 로그인 → `/regions`(fromLogin) → 현재 위치(송도1동)로 추가 + 바로 인증(by-coord 1번·verify 1번, 위치 다시 받지 않음) → 대표·인증 완료 → "홈으로 가기" → `/`.
  - 회원 1: 최대 개수 안내·현재 위치 버튼·추가 버튼 막힘, 검색 "송도" → 송도1동 "추가됨". 만료 동네 다시 인증: 송도1동 좌표 → 불일치, 강남 좌표 → 불일치, 연수1동 좌표 → 성공. **한 컨텍스트 안에서 불일치 → 좌표 바꿈 → 바로 다시 인증 → 성공**(인증은 위치 캐시 없음). 인증·대표 변경 진행 중 목록 버튼 전체 비활성. 대표 변경 → 맨 위로 + `/me` 요약에 연수1동·인증 완료. 대표 삭제 → 승계 안내 → 송도1동이 대표. 대표 아닌 동네 삭제 → 승계 안내 없음 → 강남 "현재 위치로 찾기" 404 안내. 줄 버튼 aria-label("연수1동 다시 인증" 등).
  - 검색: "송도1"을 80ms 간격으로 입력 → 요청 1번(`keyword=송도1`), 결과 없음 문구, 공백만이면 요청 없음. **찾기 중 새 검색** → 늦게 온 찾기 결과는 보이지 않고, 검색으로 추가하면 인증 요청 없이 "목록에서 인증해 주세요".
  - **실패 잠금**: 서버에만 동네를 더해 둔 뒤(다른 기기 흉내) 화면에서 추가 → 409 `REGION_LIMIT_EXCEEDED` 안내 → 10ms 간격 표본에서 목록이 1줄인 동안 버튼이 풀린 순간 없음 → 다시 받은 2줄 목록에서 풀림. onError가 재조회를 기다리지 않던 이전 코드로 되돌리면 이 확인이 실패하는 것(1줄인데 활성 25번)도 확인했다.
  - 회원 4 위치 권한 거부: 찾기 → 안내 문구, 인증하기 → 토스트, 두 버튼 모두 활성 유지, verify 요청 없음. 검색으로 청학동 추가(미인증). 권한을 다시 켜면 같은 버튼으로 동작(다른 동네 위치 → 불일치).
  - 비회원: 로그인 링크 `/login?redirect=%2Fregions`, 위치 권한 거부 → 비회원 문구, 강남 404 안내, 현재 위치 결과 "선택". 청학동 선택 → `/` → 새로고침 후 유지(저장 값에 좌표 없음) → 로그인 → `/regions`에 서버 목록만(임시 동네 안 보임, 저장 값 유지) → 로그아웃 → `/regions`에 청학동 다시 보임.
  - AT 무효화 후 대표 변경 → 401 → 재발급 1회 → 재시도 성공(primary 요청 2번).
- **"현재 위치로 찾기"의 1분 캐시**: 같은 문서에서 1분 안에는 브라우저가 이전 위치를 돌려줘, 찾기 좌표를 바꿔 가며 확인하는 시나리오는 브라우저 컨텍스트를 새로 열어 나눴다. 인증은 캐시하지 않도록 바꿔(위 결정) 실제 사용의 "불일치 → 이동 → 바로 다시 인증" 문제는 없어졌다.
