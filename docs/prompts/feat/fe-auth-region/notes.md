# feat/fe-auth-region 결정 기록

SCR-01 로그인·회원가입(REQ-001·002), 재발급 목 교체(REQ-003), `/me` 프로필 요약(GET /api/members/me)·로그아웃, 오류 처리 공용화, 인증·회원 MSW 목. 백엔드 서비스는 아직 미구현이라 docs/openapi.json 형태 그대로 목으로 개발했다.

## 범위 밖과 어디서 할지
| 넣지 않은 것 | 이유 | 어디서 |
| --- | --- | --- |
| SCR-02 동네 설정·인증 화면(`/regions`)과 동네 API 훅·목 | 변경 파일이 30개를 넘어(약 45개) 나눴다(사용자 결정, A안). `/regions`는 자리표시 그대로 | `feat/fe-region`(git-workflow.md §2에 행 추가) |
| `/me/profile` 프로필 수정(REQ-005) | 별도 브랜치. 자리표시 그대로 | 회원 화면 브랜치 |
| 비밀번호 재설정·약관 동의(REQ-036·037) | 2순위. 화면·링크 모두 넣지 않음 | 2순위 |
| 소셜 로그인(041)·동네 범위(042) | 3순위 | 3순위 |
| 홈의 "동네 미설정 → /regions" 유도, 피드의 regionId | 피드 화면 범위 | feat/fe-item-pages |

### feat/fe-region에서 이어받을 것 (이 브랜치에서 정한 것)
- **이미 있는 것**: 로그인 직후 대표 동네가 없으면 `/regions`로 replace 이동하며 `state: { fromLogin: true }`를 넘긴다(`features/auth/queries.js` `fetchPostLoginDestination`). 그 표시를 읽는 쪽은 fe-region. 인증 상태 문구 `features/region/regionStatus.js` `getVerifyStatus`(+테스트)는 `/me` 요약이 쓰려고 이 브랜치에서 만들었다. 목 시드·메모리 db(`mocks/memberSeed.js`: 행정동 마스터 6개, 회원 1의 두 동네, 회원 3 동네 없음)도 이 브랜치에 있고, 동네 API 핸들러(`mocks/member.js`의 `/api/members/me/regions*`)와 `mocks/region.js`(검색·by-coord)만 fe-region에서 더한다.
- **queryKey**: 내 동네 `['members', 'me', 'regions']`, 검색 `['regions', { keyword }]`. 현재 위치 변환·인증은 useMutation. primary 응답(목록 전체)은 setQueryData, verify·add는 목록 갱신 후 `['members', 'me']` 무효화, 대표가 바뀌는 동작(첫 추가·대표 변경·대표 삭제)은 `['items', 'feed']`도 무효화.
- **검색 디바운스 300ms**, 공백 제외 1자 이상일 때만 조회.
- **위치 옵션 `{ enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 }`**: 데스크톱에서 고정밀은 느리거나 실패하고 행정동 판정에는 일반 정확도면 충분하다. 1분 안의 캐시 위치를 받아 다시 누를 때 빠르게.
- **현재 위치로 찾은 동네를 추가하면 같은 좌표로 바로 verify**(실패해도 추가는 유지, 문구로 안내): 사용자가 방금 그 자리에서 위치를 줬으니 인증을 따로 한 번 더 누르게 하지 않으려고.
- **좌표는 ref·콜백으로만**: TanStack Query는 뮤테이션 variables·data를 MutationCache에 두고 Devtools에도 보이므로 좌표를 variables·data·queryKey·state에 넣지 않는다. `useVerifyRegion` variables는 `{ memberRegionId, locate }`(locate는 좌표를 돌려주는 함수, mutationFn 안에서 부름). `useLocateRegion`(by-coord)도 variables에 좌표가 없고 반환은 RegionResponse만, 좌표는 mutationFn 안에서 받아 호출부가 넘긴 콜백이나 ref로만 넘긴다. 그 좌표는 `RegionFinder`의 useRef에 두었다가 추가+인증에 한 번 쓰고 지운다(새 검색·화면 이탈 때도). ApiError는 axios config를 들고 있지 않아 오류 쪽으로 좌표가 새지 않는다.
- **비회원 임시 동네**: features/region의 Zustand persist store(localStorage) 하나. 로그인해도 서버에 올리지 않는다(명세 없음).
- **"홈으로 가기" 버튼**: `state.fromLogin`이고 동네가 1개 이상일 때만 하단에. 일반 진입은 PageHeader 뒤로 가기만.
- **목 by-coord**: 가장 가까운 시드 중심이 **3km 안**이면 그 동네, 아니면 404(목 전용 상수, `region.range`와 무관). verify는 by-coord 결과 == 해당 동네면 성공.
- 개발 중 위치는 크롬 DevTools → More tools → Sensors → Location에서 좌표를 바꿔 확인한다.

## 결정
- **오류 처리 공용화**: `applyFieldErrors`·`createErrorMessageGetter`·`applyCodeFieldError`는 `shared/lib/formErrors.js`, `useSubmitErrorHandler`는 `shared/hooks/`로 옮겼다. 시그니처는 `(form, isPending, { fieldMap, codeFields, getMessage })` — 가입 409 중복을 필드에 붙이려고 `codeFields`(code → 필드)를 더했다. 문구는 도메인별 `features/*/errorMessages.js`. 대여 쪽은 `getErrorMessage`·`isStaleStateError` 이름·경로가 그대로라 pages·queries는 손대지 않았고 폼 4개의 import·인자만 바꿨다. 대여 `errorMessages.test.js`는 `shared/lib/formErrors.test.js`로 옮겼다.
- **글자 수는 코드 포인트**(`shared/lib/validators.js` `countCodePoints`, 백엔드 MemberTexts.length와 같음). 이메일·닉네임에 `maxLength` 속성을 쓰지 않고 검증 함수로만 막는다(브라우저 maxLength는 UTF-16이라 이모지가 2자).
- **닉네임 글자 수 카운터는 표시하지 않는다**: 10자로 짧아 hint "2~10자" + 제출 검증으로 충분하고, 코드 포인트 카운터를 위해 공용 TextField를 넓히지 않으려고.
- **비밀번호 정규식은 openapi `MemberSignupRequest.password` pattern 그대로**(`\[` 이스케이프까지 같게, eslint no-useless-escape만 그 줄에서 끔). 문구는 백엔드 PASSWORD_PATTERN_MESSAGE와 같은 뜻.
- **이메일 형식은 느슨하게(`^[^\s@]+@[^\s@]+$`)**: 백엔드 `@Email`(Hibernate)이 도메인의 점을 요구하지 않아서, 프론트가 더 엄격하면 서버가 받는 값을 막는다. 백엔드 `@Email` 기준이 바뀌면 같이 맞춘다.
- **로그인 폼은 빈 값만 검사**(백엔드 AuthLoginRequest도 형식 검사 없음, 규칙이 바뀌기 전 계정도 로그인할 수 있어야 함).
- **가입 폼에 "비밀번호 확인" 칸(프론트 전용, 보내지 않음)**: 비밀번호 재설정이 2순위라 가입 때 오타가 나면 되돌릴 방법이 없다.
- **이메일은 앞뒤 공백을 지워 보낸다**(로그인·가입 모두). 비밀번호·닉네임은 입력 그대로(서버가 받은 그대로 글자 수를 센다).
- **로그인 직후 이동**: 성공하면 `queryClient.clear()` → `setAccessToken` → 안전한 redirect가 있으면 그곳으로 replace, 없으면 `['members', 'me']` fetchQuery로 대표 동네가 없으면 `/regions`, 있으면 `/`, 조회 실패면 `/`.
- **"이미 로그인됨 → redirect"는 마운트할 때 한 번만 본다**(`useState(() => accessToken !== null)`): 제출 성공으로 토큰이 생겨 다시 그려질 때 이 처리가 `/regions` 이동보다 먼저 `/`로 보내지 않게. 세션 복원은 SessionGate가 라우트를 그리기 전에 끝나 마운트 시점 값이 정확하다.
- **redirect 검사(`app/loginPath.js` `getSafeRedirect`)**: `/`로 시작하고 `//`·`/\`로 시작하지 않을 때만, `/login`·`/signup`(쿼리·끝 슬래시 포함)이면 무시. **제어 문자(탭·줄바꿈)가 있으면 거부**도 더했다 — 브라우저가 URL에서 지워 `/\t/evil.com`이 `//evil.com`이 된다.
- **가입 성공 → 토스트 + `/login`(redirect 유지)**, 가입한 이메일은 주소가 아니라 location state로 넘겨 미리 채우고 비밀번호 칸에 포커스. 주소·기록에 이메일을 남기지 않으려고.
- **로그아웃 순서**: API 성공 → `navigate('/', { replace: true, flushSync: true })` → AT 삭제 → `queryClient.clear()`. RouterProvider가 경로 변경을 transition으로 반영할 수 있어 `await navigate`만으로는 `/me`가 아직 그려진 채 AT 삭제(동기 렌더)가 먼저 반영될 수 있다. flushSync로 새 화면까지 커밋한 뒤 지운다. 실패(네트워크·5xx)면 토스트만 띄우고 로그인 상태 유지(RT 쿠키가 남아 다음 시작 때 복원되므로 로컬만 지우면 안 됨). 확인 문구 "이 기기에서만 로그아웃돼요"(family 단위 로그아웃, member.md REQ-002).
  - **로컬 정리는 `useLogout` 훅 옵션 onSuccess에서 한다(리뷰 반영)**: 컴포넌트가 내려가면 mutate에 넘긴 호출별 onSuccess가 불리지 않아, 요청 중에 화면을 벗어나면 서버는 로그아웃됐는데 AT·캐시가 남았다. LogoutButton은 떠 있는 동안만 ref에 "홈으로 떠나기" 함수를 두고, 훅은 그 함수가 있으면 먼저 떠난 뒤, 없으면 이동 없이 정리만 한다.
- **`queryClient.clear()` 메모(로그인·로그아웃 공통)**: clear는 화면에 떠 있는 쿼리까지 캐시에서 떼어 내, 그 화면은 다시 불러오지 않는다. 지금은 로그인 화면·로그아웃 후 홈에 쿼리가 없어 문제없다. 홈에 피드 쿼리가 붙으면(feat/fe-item-pages) `resetQueries()`로 바꿔야 할 수 있다.
- **로그인·가입 뮤테이션은 `gcTime: 0`**: variables에 비밀번호가 들어가 MutationCache에 남는다. 화면이 내려가면 바로 지우고, 로그인 성공 시는 clear()가 지운다.
- **`/me` 프로필 요약**: 사진(없으면 자리 원) · 닉네임 · 대표 동네 + 인증 상태 배지(`getVerifyStatus`: 유효 green "인증 완료 · M월 D일까지", 만료 amber, 미인증 gray). 동네가 없으면 "아직 동네가 없어요 · 동네 설정하기"(`/regions`). 서버 isVerified를 그대로 따르고 날짜로 다시 판정하지 않는다. 로그아웃은 메뉴 아래 회색 면 버튼 + ConfirmModal.

## 목 (`mocks/`)
- **파일**: `auth.js`(login·logout·reissue) · `member.js`(가입·내 정보) · `session.js`(목 세션·AT) · `memberSeed.js`(계정·행정동 마스터·내 동네 시드, 메모리 db) · `memberLogic.js`(검증·중복·AT 형식·인증 만료·MemberResponse 순수 함수, 테스트).
- **재발급 목 "항상 성공"을 교체**: 목 세션(memberId)은 localStorage `mock.session` 한 키(RT 쿠키 흉내, 새로고침해도 유지). 로그인 때 만들고 로그아웃 때 지운다. 세션이 없거나 그 회원이 목에 없으면 401 `AUTH_REFRESH_TOKEN_INVALID`. 개발 전용 코드라 localStorage를 써도 되며 앱 코드의 AT 저장 금지와 무관하다고 주석으로 적었다.
- **AT는 `mock-at.{memberId}.{발급 순번}`**, 재발급마다 바뀌고 메모리에만 있다. 새로 만든 보호 API 목(`GET /api/members/me`)은 현재 AT가 아니면 401 `AUTH_UNAUTHORIZED`. **기존 대여·물품 목은 건드리지 않았다**(AT를 보지 않고 MOCK_ME = 회원 1 기준). 그래서 회원 3으로 로그인해도 빌린 내역·내 물품은 회원 1의 것이 보인다.
- **`window.__mockAuth.expireAccessToken()`**(`mocks/browser.js`): AT만 무효화해 401 → 재발급 → 재시도를 콘솔에서 확인한다.
- **로그인 목**: 빈 값 400 `COMMON_INVALID_INPUT`(details, "공백일 수 없습니다"), 불일치 401 `AUTH_INVALID_CREDENTIALS`. **가입 목**: 백엔드 검증과 같은 규칙·문구의 400 details(필드마다 첫 위반 하나로 단순화), 중복 409(이메일 → 닉네임 순서로 가정). 가입 계정은 메모리에만 있어 새로고침하면 사라진다(그 세션은 재발급 401).
- **가입 목은 이메일 local part 64자 초과를 @Email 문구("올바른 형식의 이메일 주소여야 합니다")로 거부한다(리뷰 반영)**: Hibernate EmailValidator가 local part를 64자까지만 받아서, 100자 경계 같은 값을 넣었을 때 목과 서버의 결과가 같도록. 프론트 검증은 느슨한 그대로다.
- **시드**(비밀번호 모두 `rental123!`, 가짜 값): 회원 1 동네주민(송도1동 대표 인증 D-5 → 유효 + 연수1동 인증 D-40 → 만료) · 2 캠핑조아(연수1동) · 3 텐트초보(동네 없음) · 4 이웃(동춘1동 미인증). 닉네임은 기존 목(item.js MOCK_MEMBERS)과 같다. 동네 이름은 행정동(송도1동 등)이라 **물품 목의 동네명(송도동 등)과 다를 수 있다**(물품 목은 바꾸지 않았다).
- 목은 화면 상수를 가져오지 않는 규칙대로 정책 값을 `memberLogic.js` `MOCK_MEMBER_POLICY`에 따로 뒀다.

## 화면 (시안 없음)
- docs/design에 SCR-01·02 시안이 없어 디자인 시스템(Main.html)의 입력(TextField 48px)·버튼(primary 48px, secondary)·토큰만 썼다. fe-my-pages notes "화면 (시안 없음)"과 같은 방식.
- `/login`·`/signup`은 AppLayout 밖(하단 탭 없음) 그대로, PageHeader(뒤로 가기) + 최대 폭 `max-w-md` + 좌우 16px. 입력 autocomplete(email / current-password / new-password / nickname), 이메일 `inputMode="email"`·`autoCapitalize="none"`.
- 로그인 불일치 문구는 폼 아래 한 줄(`role="alert"`), 이메일·비밀번호 중 무엇이 틀렸는지 구분하지 않는다. 로그인 ↔ 가입 링크는 redirect를 유지하고 높이 44px.

## 백엔드에 확인할 것
1. **by-coord가 GET 쿼리에 좌표를 싣는다**: 서버·Nginx 접근 로그에 좌표가 남을 수 있어 "좌표는 저장하지 않는다"(member.md REQ-004)와 어긋날 수 있다. 프론트는 openapi대로 GET으로 부른다.
2. **verify 좌표가 어떤 행정동에도 속하지 않을 때**의 응답이 openapi에 없다(404는 경로 id에만). 목은 400 `REGION_VERIFY_MISMATCH`로 가정(fe-region).
3. **동네 추가 검사 순서**: 목은 regionId 400 → `REGION_ALREADY_ADDED` → `REGION_LIMIT_EXCEEDED`로 가정(최대 개수인데 이미 추가한 동네를 고르면 "이미 추가함"이 더 정확한 안내). 가입 중복은 이메일 → 닉네임 순서로 가정.
4. **이메일 형식**: 프론트는 백엔드 `@Email`(Hibernate) 수준으로 느슨하게 검사한다(도메인의 점 요구 안 함). `@Email` 기준이 바뀌면 프론트도 같이 맞춘다. 이메일 대소문자 구분(중복·로그인)도 목은 구분한다고 가정했다.
5. `frontend/CLAUDE.md`에 `shared/lib/validators`가 있다고 적혀 있었지만 파일이 없었다 — 이 브랜치에서 만들었다(문서 쪽 문제, 백엔드와 무관).

## 확인
- `npm run lint`·`npm test`(21파일 283개)·`npm run build` 통과. 바뀐 파일만 `npx prettier --check`.
- **build 결과에 목 코드 없음**: `.env.local`에 `VITE_USE_MOCK=true`인 채로 빌드해도 `dist/assets/*.js`에 `__mockAuth`·`mock-at`·`msw`·시드 이메일 문자열이 없다(`mocks/browser.js`는 main.jsx가 개발 서버 + VITE_USE_MOCK일 때만 동적 import). JS 번들 532,473B(fe-my-pages 기록 521.90kB 대비 약 +10kB, develop 직접 빌드 비교는 안 함). 500kB 경고는 기존 "다음에" 그대로.
- 브라우저(앱 내장 브라우저, `VITE_USE_MOCK=true`, 360px·390px, 가로 넘침 없음):
  - 비로그인 `/me` → `/login?redirect=%2Fme` → 빈 제출(두 필드 오류, 이메일 포커스) → 틀린 비밀번호(폼 아래 "이메일 또는 비밀번호가 맞지 않아요") → 회원 1 로그인 → `/me`(동네주민 · 송도1동 · 인증 완료 · 11월 24일까지).
  - 새로고침 후 로그인 유지. AT 무효화 + `/members/me` 동시 3건 → 재발급 1회, 3건 모두 새 AT로 재시도 성공.
  - 로그아웃: history 기록이 `replaceState /` 하나뿐(`/login?redirect=/me`를 거치지 않음), AT·목 세션 삭제. 다시 `/me` → 로그인 화면. 로그아웃 500(worker.use) → 토스트, `/me` 유지, AT·세션 유지.
  - redirect 없이 회원 3 로그인 → `replaceState /regions {fromLogin: true}` 하나(`/`를 거치지 않음). 회원 3 `/me` → "아직 동네가 없어요 · 동네 설정하기"(44px, `/regions`).
  - 로그인한 채 `/login?redirect=/me/rentals` → `/me/rentals`, `/signup` → `/`. `/login?redirect=//evil.example` → 회원가입 링크에 redirect 없음, 로그인 후 `/`.
  - 가입: 형식 오류 4필드(이메일·비밀번호 구성·확인 불일치·닉네임 1자) + 이메일 포커스, 409 이메일 → 이메일 필드·포커스, 409 닉네임 → 닉네임 필드·포커스, 서버 400 details(worker.use) → 두 필드 오류 + details 첫 필드 포커스, 이모지 닉네임("새이웃😀") 통과, 성공 → 토스트 + `/login`(redirect 유지), 이메일 채워짐·비밀번호 포커스·주소에 이메일 없음 → 그 계정으로 로그인 → `/me` → 새로고침 → 재발급 401로 로그인 화면.
  - 대여 회귀: 대여 2 취소 모달에 서버 400 details(reasonText) → 모달 안 필드 오류 + 포커스(옮긴 hook 동작).
