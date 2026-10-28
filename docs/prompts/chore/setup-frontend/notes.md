# chore/setup-frontend 결정 기록

## 스택·도구
- **React Router·Zustand·axios·MSW를 "확정"으로 올림.** 이 브랜치에서 실제로 설치·사용하므로. stompjs·react-hook-form·dayjs·토스·카카오맵은 쓰는 화면 브랜치까지 "제안" 유지.
- **Vitest 추가 + CI frontend 잡에 `npm run test`.** 401 재발급(single-flight·1회 재시도·실패 처리)은 수동 시나리오로는 회귀를 못 잡으므로. 환경은 node + `msw/node`(jsdom 미도입 — 이동은 핸들러 주입으로 대체해 DOM이 필요 없음).
- **Tailwind v4 + `@tailwindcss/vite`.** 최신 안정판, PostCSS·tailwind.config.js 불필요.
- **버전 선택**: 각 패키지의 최신 안정판 중 출시 2주 이상 지난 것(2026-10-14 기준), exact 고정. 백엔드와 같은 규칙.
- **Node `.nvmrc` = 24** (현재 Active LTS Krypton). 26은 아직 LTS 전. react-router 8은 Node ≥22.22 요구.
- `prettier-plugin-tailwindcss`로 클래스 순서 자동 정렬 — 리뷰에서 클래스 순서 논쟁을 없애려고.
- **Prettier에서 `*.md` 제외.** `npm run format`이 `frontend/CLAUDE.md` 표를 패딩 정렬로 바꿔 문서 diff가 커지므로, 문서는 기존 형식 유지.

## 환경·프록시
- **`/files`도 프록시.** local 프로필의 `LocalFileStorage`가 `{url-prefix}/{key}` = `/files/...` 상대 URL을 반환하고 `SecurityConfig`가 `/files/**`를 공개로 둠. 운영은 S3 절대 URL이라 영향 없음.
- **`.env.example`을 지시대로 정리**: `VITE_USE_MOCK=false`(기본은 실제 백엔드 연동), `VITE_WS_URL` 제거(이 브랜치에서 안 씀, STOMP 브랜치에서 실제로 쓸 때 추가).

## shared/api
- **authStore는 `shared/api/authStore.js`에 둠.** 인터셉터(shared)가 features를 import하면 의존 방향이 뒤집히므로. 문서의 디렉터리 구조는 그대로.
- **프론트 전용 에러 코드**: 서버 응답이 없으면 `CLIENT_NETWORK_ERROR`, 타임아웃이면 `CLIENT_TIMEOUT`, 응답은 왔는데 공통 래퍼가 아니면(프록시 502, 백엔드 미기동 등) `CLIENT_INVALID_RESPONSE`. `{DOMAIN}_{REASON}` 규칙에 맞추되 서버 `ErrorCode`와 겹치지 않게 `CLIENT_` 접두사. `status`는 응답이 없으면 0.
- **요청 취소(AbortSignal)는 ApiError로 바꾸지 않고 그대로 throw.** TanStack Query가 취소를 인식해야 하므로.
- **axios 기본 timeout은 설정하지 않음.** 정책 수치가 `policies.md`에 없으므로 임의로 정하지 않는다. 필요한 호출에서 개별 지정.
- **재발급 실패 시 이동은 `setUnauthorizedHandler`로 주입.** shared/api가 라우터를 import하면 순환 의존이 생기고, 테스트에서 이동을 검증하려고. app/에서 `router.navigate`로 등록(전체 새로고침 없음).
- 앱 시작 복원은 인터셉터 재발급과 같은 single-flight 함수를 쓰되 실패해도 이동 핸들러를 부르지 않음.
- 응답 래퍼에서 AT 필드 접근은 `extractAccessToken` 하나로 격리(`feat/member-auth` 미머지).

## 라우트
- 1순위만 등록. `/password-reset`(REQ-036, 2순위)·`/payments/*`(REQ-024, 2순위)·`/me/wishes`(022)·`/me/recent`(035)·`/me/payments`(033·039)는 미등록.
- `/me/settings`는 대응하는 1순위 REQ가 없어 미등록(로그아웃 위치는 SCR-12 화면 브랜치에서 결정).
- `/regions`는 비회원 임시 동네 선택이 있어 공개 라우트. 등록·예약·대여 상세·나의 대여는 `RequireAuth`.
- 채팅·알림 탭은 `/chats`, `/notifications` 라우트에 같은 `ComingSoonPage`를 연결.
- 세션 복원 대기는 React 19 `use(promise)` + `Suspense` — 렌더 전에 시작한 Promise 하나를 쓰므로 StrictMode 이중 실행에도 재발급이 한 번만 나감.

## 셋업 절차 (지침에 쓰지 않음)
- create-vite 템플릿 대신 파일을 직접 작성(템플릿의 데모 파일·불필요 의존성 제거 수고를 줄이려고).
- `npm install --save-exact`로 설치, `npx msw init public --save`로 워커 생성.
