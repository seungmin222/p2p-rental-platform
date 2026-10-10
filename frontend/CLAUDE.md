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

- **필드명을 추측해서 쓰지 않는다.** openapi.json에서 확인하고, 없으면(미구현 API) 백엔드 담당과 응답 형태를 합의할 때까지 MSW 목에 `// TODO: 백엔드 확정 전 임시` 주석을 단다.
- openapi.json과 실제 응답이 다르면 프론트에서 맞추지 말고 백엔드에 알린다(openapi.json 재생성 누락일 가능성).
- 사람은 백엔드 로컬 서버의 `/swagger-ui.html`에서 직접 호출해 볼 수 있다.

## 스택
**확정**: React · Vite · JavaScript(JSX) · Tailwind CSS · **TanStack Query**(서버 상태)
**제안**(팀 확정 시 수정): React Router · Zustand(인증·동네 등 클라이언트 상태) · axios · @stomp/stompjs · react-hook-form · dayjs · MSW(목 API) · 토스페이먼츠 결제창 SDK(API 개별 연동, 결제위젯 아님) · 카카오맵 JS SDK

## 디렉터리

```
src/
├── app/            라우터, QueryClientProvider, 레이아웃(하단 탭 / PC 헤더)
├── pages/          화면 단위 (SCR-xx와 1:1)
├── features/       도메인별 컴포넌트·훅·API  (auth, region, item, rental, payment, chat, notification, review)
│   └── rental/
│       ├── api.js          axios 호출 함수
│       ├── queries.js      useQuery/useMutation 훅 + queryKey
│       ├── components/
│       └── constants.js    상태 라벨·탭 매핑
├── shared/
│   ├── api/        axios 인스턴스, 인터셉터, 에러 정규화
│   ├── ws/         STOMP 클라이언트 싱글톤
│   ├── ui/         Button, BottomSheet, Modal, Toast, Badge, Calendar, ImageUploader, EmptyState
│   ├── lib/        format(가격·경과시간), validators
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

- 하단 탭(모바일): 홈 · 검색 · 채팅 · 알림 · 나의 대여. PC(≥1280px)는 상단 헤더 메뉴로 같은 구성.
- 인증 필요 라우트는 `RequireAuth`로 감싸고, 비로그인 시 `/login?redirect=...`.
- 동네 미설정 시 홈 진입 → `/regions`로 유도. 비회원 임시 동네는 localStorage.

## 사용 흐름 (핵심 경로)
`로그인 → 동네 인증 → 홈 → 상세 → 예약하기(캘린더 기간 선택) → 결제 → 대여 상세(인계 → 반납 요청 → 상태 확인) → 후기 → 프로필`
상세에서 채팅하기 → 채팅방(상단에 대여 상태 표시, 대여 상세로 이동).

## API 연동 규칙
- 응답은 `{ success, data, error }`. axios 응답 인터셉터에서 `data`만 꺼내고, 실패는 `ApiError(code, message, details)`로 정규화해 throw.
- **분기는 `error.code`로** (예: `RENTAL_PERIOD_CONFLICT` → "이미 예약된 기간입니다" 토스트 + 캘린더 refetch). HTTP 메시지 문자열로 분기하지 않는다.
- 폼 검증 오류(`details[]`)는 react-hook-form `setError`로 필드에 매핑.
- Access Token은 **메모리(Zustand)에만**. localStorage 저장 금지. Refresh Token은 HttpOnly 쿠키라 JS가 만지지 않는다(`withCredentials: true`).
- **401 처리**: 재발급 요청을 **단일 비행(single-flight)**으로 — 동시에 401이 여러 개 와도 `/api/auth/reissue`는 한 번만 호출하고 나머지는 대기 후 재시도. 원 요청은 1회만 재시도. 재발급 실패 → 상태 초기화 후 `/login`.
- 앱 시작 시 `/api/auth/reissue`를 한 번 호출해 로그인 상태 복원.
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
  RETURNED:        { label: '반납 완료', tone: 'amber' },
  COMPLETED:       { label: '거래 완료', tone: 'gray' },
  CANCELED:        { label: '취소',     tone: 'gray' },
};
```
대여 상세(SCR-08)는 스텝바(예약 → 결제 → 인계 → 반납 → 상태 확인 → 완료)와 **현재 상태 × 내 역할(대여자/제공자)**에 따라 보이는 액션 버튼을 정의한 표 하나로 렌더링한다. 버튼 노출 조건을 컴포넌트 곳곳에 흩뿌리지 않는다. (서버가 최종 검증하므로 UI는 보조)

## UI 규칙
- 모바일 우선(360px~). 데스크톱은 중앙 정렬 최대 폭 컨테이너.
- 사용감은 **기존 동네 중고거래 앱과 최대한 비슷하게**: 카드형 피드, 하단 탭, 상세 하단 고정 바(관심 · 가격/보증금 · 채팅하기 · 예약하기), 채팅 상단 물품 요약, 우측 하단 (+) 등록 버튼. (특정 서비스 로고·브랜드 요소를 복제하지 않는다)
- 가격: `12,000원/일`, 보증금 `보증금 50,000원`. 경과 시간: `3분 전`, `2일 전` (bumpedAt 기준).
- 이미지: 목록은 thumbnailUrl + `loading="lazy"`. 등록 화면에서 순서 변경(드래그) 지원, 최대 10장.
- 캘린더: AVAILABLE/BOOKED/UNAVAILABLE 3색 구분, 범위 선택, 1~30일 제한을 UI에서도 안내.
- 빈 상태·로딩·에러 상태를 모든 목록에 구현 (`EmptyState`, 스켈레톤).
- 본인 물품 상세에서는 예약하기 대신 수정·관리 버튼.
- 물품 가격(itemPrice)은 등록·수정 화면에서만 입력, 상세에 표시하지 않는다. 등록 시 권장 보증금 범위를 보증금 입력란 아래 안내.
- 접근성: 버튼에 텍스트 또는 aria-label, 터치 영역 44px 이상.

## 로컬 연동 (백엔드와 같은 출처로)
- `vite.config.js`의 `server.proxy`로 `/api` → `http://localhost:8080`, `/ws` → `ws://localhost:8080`(`ws: true`)를 넘긴다. 브라우저 기준으로 프론트와 API가 같은 출처라 CORS가 필요 없고, Refresh Token 쿠키(`SameSite=Lax`, `Path=/api/auth`)가 그대로 전달된다.
- axios `baseURL`은 `VITE_API_URL`(기본 `/api`), `withCredentials: true`. 절대 주소(`http://localhost:8080`)를 코드에 쓰지 않는다.
- 운영은 Nginx가 정적 파일과 `/api`, `/ws`를 같은 도메인에서 서빙하므로 같은 코드가 그대로 동작한다.

## 외부 SDK
- **카카오맵**: 앱 키는 `VITE_KAKAO_MAP_KEY`. 스크립트 동적 로드 래퍼 훅(`useKakaoMap`). 로드 실패 시 주소 텍스트만 표시(REQ-030 예외).
- **토스페이먼츠**: `VITE_TOSS_CLIENT_KEY`(테스트, **API 개별 연동 키** — 결제위젯 키와 다름). 카드 결제창 → successUrl `/payments/success?paymentKey&orderId&amount` → 이 값을 서버 `/api/payments/confirm`으로 전달. **금액 검증은 서버가 한다.**
- **위치**: `navigator.geolocation` 권한 거부 시 안내 후 동네 검색만 제공.

## 개발 방식
- 백엔드가 컨트롤러·DTO를 먼저 머지하면 `docs/openapi.json`이 갱신된다. 그 스키마의 응답 형태 그대로 `mocks/` MSW 핸들러를 만들어 먼저 개발한다(공통 래퍼 `{ success, data, error }` 포함). `VITE_USE_MOCK=true`로 전환.
- 타입 힌트가 필요하면 openapi.json에서 JSDoc 타입을 생성해 쓸 수 있다(선택, `openapi-typescript` 등). 생성 파일은 직접 수정하지 않는다.
- 환경 변수: `frontend/.env.example`을 `frontend/.env.local`로 복사해서 사용(`.env.local`은 커밋 금지). 키를 추가하면 `.env.example`에도 추가.
- Node 버전: `frontend/.nvmrc`에 LTS 버전을 적어 팀원과 CI가 같은 버전을 쓴다(프로젝트 생성 시 만든다).
- 포맷은 Prettier, 이름 규칙은 `docs/conventions.md` §5. 커밋 전 `npm run format`.
- `npm run lint`, `npm run build`가 통과해야 PR.
- 1순위 단계에서는 결제 화면 대신 "예약 완료 → 대여 상세"로 바로 이동 (백엔드 `payment-enabled=false`와 맞춤). 플래그 `VITE_PAYMENT_ENABLED`.
