# frontend/CLAUDE.md — React SPA 규칙

루트 `CLAUDE.md`, `docs/api.md`, `docs/state-machines.md`를 먼저 읽는다.

## 무엇을 어디서 확인하나
| 필요한 것 | 기준 |
| --- | --- |
| 요청·응답 필드, 타입, 필수 여부, 에러 코드 목록 | `docs/openapi.json` (백엔드 Swagger에서 생성) |
| 아직 없는 API, 순위 | `docs/api.md` |
| 상태 라벨·상태별 버튼 | `docs/state-machines.md` |
| 입력 제한(장수·글자 수·기간) | `docs/policies.md` |
| 화면 동작·예외 상황·에러별 안내 | `docs/domains/*.md` |

- **필드명을 추측해서 쓰지 않는다.** openapi.json에서 확인하고, 없으면(미구현 API) 백엔드 담당과 응답 형태를 합의할 때까지 MSW 목에 `// TODO: 백엔드 확정 전 임시` 주석을 단다. 코드에서 미확정 응답 필드를 읽어야 하면 그 접근을 함수 하나로 격리하고 같은 주석을 단다(예: `shared/api/reissue.js`의 `extractAccessToken`).
- openapi.json과 실제 응답이 다르면 프론트에서 맞추지 말고 백엔드에 알린다(openapi.json 재생성 누락일 가능성).
- 사람은 백엔드 로컬 서버의 `/swagger-ui.html`에서 직접 호출해 볼 수 있다.

## 스택
**확정**: React · Vite · JavaScript(JSX) · Tailwind CSS v4(`@tailwindcss/vite`) · **TanStack Query**(서버 상태) · React Router · Zustand(인증·동네 등 클라이언트 상태) · axios · MSW(목 API) · Vitest(단위 테스트) · react-hook-form(폼)
**제안**(쓰는 브랜치에서 확정): @stomp/stompjs · 토스페이먼츠 결제창 SDK(API 개별 연동, 결제위젯 아님) · 카카오맵 JS SDK. dayjs는 쓰지 않는다(날짜는 `shared/lib/date.js`)

- 의존성은 **실제로 쓰는 브랜치에서** 추가한다(미리 설치하지 않는다). 버전은 exact로 고정하고 `package-lock.json`을 커밋한다.

## 디렉터리

```
src/
├── app/            라우터, QueryClientProvider, 레이아웃(하단 탭 / PC 헤더)
│   └── layout/         AppLayout, PageHeader, BottomActionBar, FloatingActionButton
├── pages/          화면 단위 (SCR-xx와 1:1)
├── dev/            개발 서버 전용 화면(`/dev/ui` 공통 UI 미리보기, 빌드에서 제외)
├── features/       도메인별 컴포넌트·훅·API  (auth, region, item, rental, payment, chat, notification, review)
│   └── rental/
│       ├── api.js          axios 호출 함수
│       ├── queries.js      useQuery/useMutation 훅 + queryKey
│       ├── components/
│       └── constants.js    상태 라벨·탭 매핑
├── shared/
│   ├── api/        axios 인스턴스, 인터셉터, 에러 정규화, 토큰 재발급, authStore(AT)
│   ├── ws/         STOMP 클라이언트 싱글톤
│   ├── ui/         Button, ButtonLink, Badge, Chip, TextField, Calendar, ImageUploader, Spinner, Skeleton, EmptyState, ErrorState, Modal, ConfirmModal, BottomSheet, Toaster(+ toast) — index.js로 모아 export. 선택·목록 로직은 calendarSelection.js·imageList.js(순수 함수, 테스트)
│   ├── lib/        format(가격·경과시간), date(대여 기간 날짜 문자열), validators
│   └── hooks/
├── mocks/          MSW 핸들러 (Swagger 응답 형태 그대로)
└── styles/
```

## 화면 ↔ 라우트

| SCR | 화면 | 라우트(제안) | 주요 REQ | 순위 |
| --- | --- | --- | --- | --- |
| 01 | 로그인 / 회원가입 | `/login`, `/signup`, `/password-reset` | 001–003, 036, 037 | 1 |
| 02 | 동네 설정·인증 | `/regions` | 004, 042 | 1 |
| 03 | 홈(동네 피드) | `/` | 009, 060 | 1 |
| 04 | 검색 | `/search` | 010, 046, 064, 065 | 1 |
| 05 | 물품 상세 | `/items/:id` | 011, 012, 022, 030 | 1 |
| 06 | 물품 등록·수정 | `/items/new`, `/items/:id/edit` | 006–008, 030 | 1 |
| 07 | 대여 예약·결제 | `/items/:id/reserve`, `/payments/success`, `/payments/fail` | 013, 024 | 1 (결제 2) |
| 08 | 대여 상세 | `/rentals/:id` | 015–018, 025, 034, 038, 040, 049 | 1 |
| 09 | 채팅 목록 | `/chats` | 028, 029 | 2 |
| 10 | 채팅방 | `/chats/:roomId` | 026–029 | 2 |
| 11 | 알림 | `/notifications` | 031, 032 | 2 |
| 12 | 나의 대여 | `/me`, `/me/rentals`, `/me/items`, `/me/wishes`, `/me/recent`, `/me/payments`, `/me/settings`, `/me/profile` | 005, 019, 020, 022, 033, 035, 039 | 1 |
| 13 | 사용자 프로필 | `/users/:id` | 021, 054 | 2 |
| 14 | 후기 작성 | `/rentals/:id/review` | 034 | 2 |
| 15 | 약관·정책 | `/terms/:type` | 037 | 2 |
| 16 | 관리자 | `/admin/*` | 057 | 3 |

- 현재 순위의 라우트만 등록한다. 현재 순위보다 높은 순위 화면은 라우트·탭을 **"준비 중" 자리표시(`ComingSoonPage`)로만** 둔다(1순위 단계: 채팅·알림 탭).
- 하단 탭(모바일): 홈 · 검색 · 채팅 · 알림 · 나의 대여. PC(≥1280px)는 상단 헤더 메뉴로 같은 구성.
- 인증 필요 라우트는 `RequireAuth`로 감싸고, 비로그인 시 `/login?redirect=...`.
- 동네 미설정 시 홈 진입 → `/regions`로 유도. 비회원 임시 동네는 localStorage.

## 사용 흐름 (핵심 경로)
`로그인 → 동네 인증 → 홈 → 상세 → 예약하기(캘린더 기간 선택) → 결제 → 대여 상세(인계 → 반납 요청 → 상태 확인) → 후기 → 프로필`
상세에서 채팅하기 → 채팅방(상단에 대여 상태 표시, 대여 상세로 이동).

## API 연동 규칙
- 응답은 `{ success, data, error }`. axios 응답 인터셉터에서 `data`만 꺼내고, 실패는 `ApiError(code, message, details, status)`로 정규화해 throw.
  - 서버 응답이 아닌 실패는 프론트 전용 코드: `CLIENT_NETWORK_ERROR`(응답 없음, `status` 0) · `CLIENT_TIMEOUT` · `CLIENT_INVALID_RESPONSE`(공통 래퍼가 아닌 응답, 예: 프록시 502). 요청 취소는 ApiError로 바꾸지 않는다.
- **분기는 `error.code`로** (예: `RENTAL_PERIOD_CONFLICT` → "이미 예약된 기간입니다" 토스트 + 캘린더 refetch). HTTP 메시지 문자열로 분기하지 않는다.
- 폼 검증 오류(`details[]`)는 react-hook-form `setError`로 필드에 매핑(`features/rental/errorMessages.js` `applyFieldErrors`).
- 폼은 react-hook-form. 제출할 때 검증하고 첫 오류 필드로 포커스(`shouldFocusError`). 값 구독은 `watch` 대신 `useWatch`(React Compiler가 `watch`가 있는 컴포넌트를 건너뛴다). 제출 중에는 Modal·BottomSheet `dismissible={false}`, 버튼 비활성.
- **multipart**(사진 + JSON): `request` 파트는 `new Blob([JSON.stringify(body)], { type: 'application/json' })`(Content-Type이 없거나 text/plain이면 415 `COMMON_UNSUPPORTED_MEDIA_TYPE`), 사진은 `images` 파트를 장마다 반복 append. ImageUploader value에서 `file`만 꺼낸다(`features/rental/api.js` `toMultipart`).
- Access Token은 **메모리(Zustand)에만**. localStorage 저장 금지. Refresh Token은 HttpOnly 쿠키라 JS가 만지지 않는다(`withCredentials: true`).
- **401 처리**: 재발급 요청을 **단일 비행(single-flight)**으로 — 동시에 401이 여러 개 와도 `/api/auth/reissue`는 한 번만 호출하고 나머지는 대기 후 재시도. 원 요청은 1회만 재시도. 재발급 실패 → 상태 초기화 후 `/login?redirect=<현재 경로>`.
  - 재발급 호출은 **인터셉터를 타지 않는 별도 호출**(무한 루프 방지).
  - `/auth/login`·`/auth/logout`·`/auth/reissue`의 401은 재발급을 시도하지 않는다.
- 앱 시작 시 `/api/auth/reissue`를 한 번 호출해 로그인 상태 복원. 끝날 때까지 라우트 렌더를 보류하고, 실패하면 비로그인으로 진행한다(로그인 화면으로 보내지 않음).
- 로그아웃하거나 재발급에 실패하면 저장된 AT를 지운다.
- 공개 API(`/api/auth/**`, `POST /api/members`)에는 Authorization 헤더를 붙이지 않는다. 판정은 **baseURL을 뺀 상대 경로**(`/auth/**`, `POST /members`) 기준.
- **서버에서 온 데이터는 전부 TanStack Query로 다룬다.** `useEffect + useState`로 API를 호출해 상태를 들고 있지 않는다. Zustand에는 서버 데이터를 복사해 두지 않는다(토큰·임시 동네·UI 상태만).
- 조회는 `useQuery`/`useInfiniteQuery`, 변경은 `useMutation` → `onSuccess`에서 관련 queryKey `invalidateQueries`. 훅은 `features/*/queries.js`에 모으고 컴포넌트에서 axios를 직접 호출하지 않는다.
- 기본 옵션(`QueryClient`): `retry: 1`(401·403·404·409는 재시도 안 함), `staleTime`은 목록 30초·상세 10초 정도로 시작. 캘린더·대여 상세처럼 상태가 바뀌는 화면은 짧게.
- queryKey 규칙: `['items', 'feed', {regionId, range, categoryId}]`, `['items', id]`, `['rentals', id]`, `['items', id, 'calendar', month]`. 뮤테이션 후 관련 키 invalidate.
- 무한 스크롤: `useInfiniteQuery` + `nextCursor`. IntersectionObserver.

## 실시간 (STOMP)
- 로그인 후 `shared/ws`의 싱글톤 클라이언트 연결, `connectHeaders: { Authorization: 'Bearer ' + AT }`. 토큰 재발급 시 재연결.
- 구독: `/user/queue/notifications`(알림 배지·토스트), `/user/queue/chat-rooms`(목록·안 읽은 수), 채팅방 진입 시 `/sub/chat-rooms/{id}`.
- 수신 이벤트로 TanStack Query 캐시를 직접 갱신(`setQueryData`)하거나 invalidate.
- 자동 재연결 + 재연결 후 놓친 메시지는 REST로 보충.

## 상태 표시
`docs/state-machines.md`의 Enum 코드 → 라벨·색을 `features/*/constants.js` 한 곳에서 매핑.

```js
export const RENTAL_STATUS = {
  PENDING_PAYMENT: { label: '결제 대기', tone: 'gray' },
  RESERVED:        { label: '대여 예정', tone: 'blue' },
  RENTING:         { label: '대여 중',   tone: 'green' },
  OVERDUE:         { label: '연체',     tone: 'red' },
  RETURNED:        { label: '상태 확인 대기', tone: 'amber' },
  COMPLETED:       { label: '거래 완료', tone: 'ink' },
  CANCELED:        { label: '취소',     tone: 'gray' },
};
```
대여 상세(SCR-08)는 스텝바 5단계(결제 · 대여 예정 · 대여 중 · 반납 완료 · 거래 완료, 결제가 꺼져 있으면 첫 단계는 "예약". OVERDUE는 "대여 중"을 danger로, CANCELED는 스텝바 대신 취소 정보 카드)와 **현재 상태 × 내 역할(대여자/제공자)**에 따라 보이는 액션 버튼을 정의한 표 하나로 렌더링한다. 버튼 노출 조건을 컴포넌트 곳곳에 흩뿌리지 않는다. (서버가 최종 검증하므로 UI는 보조)

### 대여 화면 규칙 (SCR-07·08)
- 버튼 표는 `features/rental/rentalActions.js` `getRentalActions(rental, returnInspection, today)`, 스텝바는 `rentalSteps.js` `getRentalSteps(status)` 순수 함수(테스트 있음). today는 호출부가 `todayInSeoul()`로 넘긴다. RETURNED는 inspections를 불러오기 전(`returnInspection === undefined`)에는 버튼을 그리지 않는다.
- 오류 문구는 `features/rental/errorMessages.js` 한 곳(code → 문구). `RENTAL_INVALID_TRANSITION`·`COMMON_CONCURRENT_UPDATE`·`RENTAL_CANCEL_AGREEMENT_CLOSED`는 "상태가 바뀌었어요. 다시 불러왔어요" + 상세·inspections 무효화(`queries.js`가 뮤테이션 onError에서 처리).
- 상태 변경 응답이 RentalDetailResponse면 `['rentals', id]`에 `setQueryData`, `['rentals', 'me']` 무효화. 기간 점유가 바뀌면(예약·취소·동의 수락·반납 요청(대리 접수 포함)) 물품 캘린더·예약 현황도, 기록이 생기면(인계·반납·상태 확인) inspections도 무효화. 동의 요청(`CancelAgreementResponse`)은 상세를 다시 불러온다.
- 모달·시트는 열린 동안만 렌더해 폼이 매번 새로 시작한다. 모달 안의 오류는 모달 안에.
- 결제 금액·수수료·보증금 보관 안내는 `VITE_PAYMENT_ENABLED`가 true일 때만 보인다(1순위엔 받지 않음). 연체료·손상 등급·차감·AI 비교는 2순위라 표시하지 않는다.
- 정책 수치는 `features/rental/constants.js`(`RENTAL_TEXT_MAX`·`CANCEL_FEE`·`RETURN_AUTO_CONFIRM_DAYS`·`PAYMENT_EXPIRE_MINUTES`·`CANCEL_AGREEMENT_EXPIRE_HOURS`).

## UI 규칙
- 모바일 우선(360px~). 데스크톱은 중앙 정렬 최대 폭 컨테이너.
- 사용감은 **기존 동네 중고거래 앱과 최대한 비슷하게**: 카드형 피드, 하단 탭, 상세 하단 고정 바(관심 · 가격/보증금 · 채팅하기 · 예약하기), 채팅 상단 물품 요약, 우측 하단 "+ 물품 등록" 버튼. (특정 서비스 로고·브랜드 요소를 복제하지 않는다)
- 가격: `12,000원/일`, 보증금 `보증금 50,000원`. 경과 시간: `3분 전`, `2일 전` (bumpedAt 기준).
- 이미지: 목록은 thumbnailUrl + `loading="lazy"`. 등록·수정·인계·반납은 `ImageUploader`(장수·용량·형식은 `ITEM_IMAGES`·`INSPECTION_IMAGES`로 넘김). 순서 변경은 썸네일 아래 줄의 앞으로·대표로·뒤로 버튼(첫 장 = 대표, 사진 위에는 삭제만). 드래그는 넣지 않는다(모바일 브라우저가 HTML5 drag&drop을 지원하지 않음). 필요해지면 해당 화면 브랜치에서 PC 전용으로 검토.
- 캘린더: AVAILABLE/BOOKED/UNAVAILABLE 3색 구분, 범위 선택, 1~30일 제한을 UI에서도 안내(`Calendar`, 기간은 `RENTAL_PERIOD`·라벨은 `RENTAL_DAY_STATUS`로 넘김).
  - `days`는 지금까지 불러온 여러 달의 응답을 합쳐 넘긴다. 예약이 달을 넘을 수 있어(10/30~11/2) 범위 검사를 받은 days 전체로 하기 때문이다. days에 없는 날짜는 예약 불가로 본다.
- 빈 상태·로딩·에러 상태를 모든 목록에 구현 (`EmptyState`, 스켈레톤).
- 본인 물품 상세에서는 예약하기 대신 수정·관리 버튼.
- 물품 가격(itemPrice)은 등록·수정 화면에서만 입력, 상세에 표시하지 않는다. 등록 시 권장 보증금 범위를 보증금 입력란 아래 안내.
- 접근성: 버튼에 텍스트 또는 aria-label, 터치 영역 44px 이상.
- 디자인 기준은 `docs/design/`(정확한 값은 `mockups/*.html` 인라인 스타일, PNG는 전체 모양 참고). 시안과 명세가 다르면 명세가 우선이고 사용자에게 확인한다.
- 색·글자 크기는 `styles/index.css`의 `@theme` 토큰만 쓴다(색: `primary`·`primary-soft`·`accent`·`accent-strong`·`ink`·`ink-muted`·`surface`·`line`·`divider`·`danger` 등, 글자: `text-title`·`heading`·`body`·`price`·`caption`. 토큰 밖 크기는 다음 예외만 허용: `text-xs` 12px(배지·보조 글씨), `text-sm` 14px(작은 버튼 `sm`·토스트·캘린더 날짜와 월 표시), `text-lg` 18px(헤더 제목), `text-[11px]`(하단 탭 라벨). 그 밖의 직접 값(`text-[15px]` 등)은 쓰지 않는다 — `/dev` 미리보기 예외). Tailwind 기본 팔레트(gray·red 등)를 직접 쓰지 않는다. 모서리는 입력 8px(`rounded-lg`), 이미지 썸네일 10px(`rounded-[10px]`), 카드·버튼 12px(`rounded-xl`), 칩·배지 full.
- Button variant는 시안 5가지에 맞춘다: `primary`(채움) · `outline`(주 색상 외곽선) · `secondary`(회색 면) · `danger`(위험 **외곽선**) · 비활성(`disabled`). 크기 기본 `md` 48px, 좁은 곳은 `sm` 44px.
- 토스트: `import { toast } from '../shared/ui'` → `toast.show({ message, tone })`(tone `default`·`danger`). `<Toaster />`는 App에 한 곳만 있다. 모달 안의 오류는 토스트 대신 모달 안에 표시(모달이 top layer라 토스트가 가려짐).
- 하단 고정 바가 있는 화면은 라우트에 `handle: { hideTabBar: true }`를 달면 AppLayout이 하단 탭을 숨긴다. 바는 `BottomActionBar`(본문이 가려지지 않게 자리도 확보).
- 날짜: 대여 기간(`startDate`·`endDate`, 캘린더)은 `"YYYY-MM-DD"` 문자열로만 다루고 `shared/lib/date.js`로 계산한다. `new Date('YYYY-MM-DD')`를 쓰지 않는다(UTC 자정이라 시간대에 따라 하루 밀림). 오늘 날짜는 `todayInSeoul()`. 서버 일시(오프셋 없음)는 `parseServerDateTime`이 서울 시각으로 해석.
- 정책 수치(장수·용량·기간)는 shared/ui에 기본값으로 두지 않는다. `features/*/constants.js`에 policies.md 키 주석과 함께 두고 props로 넘긴다.

## 로컬 연동 (백엔드와 같은 출처로)
- `vite.config.js`의 `server.proxy`로 `/api` → `http://localhost:8080`, `/ws` → `ws://localhost:8080`(`ws: true`), `/files` → `http://localhost:8080`(local 프로필 업로드 이미지가 `/files/...` 상대 URL)를 넘긴다. 브라우저 기준으로 프론트와 API가 같은 출처라 CORS가 필요 없고, Refresh Token 쿠키(`SameSite=Lax`, `Path=/api/auth`)가 그대로 전달된다.
- axios `baseURL`은 `VITE_API_URL`(기본 `/api`), `withCredentials: true`. 절대 주소(`http://localhost:8080`)를 코드에 쓰지 않는다.
- 운영은 Nginx가 정적 파일과 `/api`, `/ws`를 같은 도메인에서 서빙하므로 같은 코드가 그대로 동작한다.

## 외부 SDK
- **카카오맵**: 앱 키는 `VITE_KAKAO_MAP_KEY`. 스크립트 동적 로드 래퍼 훅(`useKakaoMap`). 로드 실패 시 주소 텍스트만 표시(REQ-030 예외).
- **토스페이먼츠**: `VITE_TOSS_CLIENT_KEY`(테스트, **API 개별 연동 키** — 결제위젯 키와 다름). 카드 결제창 → successUrl `/payments/success?paymentKey&orderId&amount` → 이 값을 서버 `/api/payments/confirm`으로 전달. **금액 검증은 서버가 한다.**
- **위치**: `navigator.geolocation` 권한 거부 시 안내 후 동네 검색만 제공.

## 개발 방식
- 백엔드가 컨트롤러·DTO를 먼저 머지하면 `docs/openapi.json`이 갱신된다. 그 스키마의 응답 형태 그대로 `mocks/` MSW 핸들러를 만들어 먼저 개발한다(공통 래퍼는 `mocks/response.js`의 `ok`·`fail` 헬퍼로). `VITE_USE_MOCK=true`로 전환.
  - MSW는 **개발 서버 + `VITE_USE_MOCK=true`일 때만** `main.jsx`가 동적 import로 시작하고 운영 번들에는 들어가지 않게 한다. 처리하지 않는 요청은 bypass. `public/mockServiceWorker.js`는 커밋한다(msw 버전을 올리면 `npx msw init public`으로 갱신).
- 타입 힌트가 필요하면 openapi.json에서 JSDoc 타입을 생성해 쓸 수 있다(선택, `openapi-typescript` 등). 생성 파일은 직접 수정하지 않는다.
- 환경 변수: `frontend/.env.example`을 `frontend/.env.local`로 복사해서 사용(`.env.local`은 커밋 금지). 키를 추가하면 `.env.example`에도 추가. 현재 키: `VITE_API_URL` · `VITE_USE_MOCK` · `VITE_PAYMENT_ENABLED` · `VITE_KAKAO_MAP_KEY` · `VITE_TOSS_CLIENT_KEY`.
- Node 버전: `frontend/.nvmrc`(현재 `24`, LTS)로 팀원과 CI가 같은 버전을 쓴다.
- 포맷은 Prettier, 이름 규칙은 `docs/conventions.md` §5. 커밋 전 `npm run format`.
- `npm run lint`, `npm run test`, `npm run build`가 통과해야 PR (CI도 같은 순서).
- 1순위 단계에서는 결제 화면 대신 "예약 완료 → 대여 상세"로 바로 이동 (백엔드 `payment-enabled=false`와 맞춤). 플래그 `VITE_PAYMENT_ENABLED`.
