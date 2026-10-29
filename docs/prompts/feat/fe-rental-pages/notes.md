# feat/fe-rental-pages 결정 기록

SCR-07 대여 예약 + SCR-08 대여 상세 + 공통(`features/rental/`)·MSW 목. 결제 화면·2순위 기능(결제·연장·미반납 신고·연체료·후기·채팅·AI 비교·손상 등급/차감)은 범위 밖.

## 범위·의존성
- **SCR-12(나의 대여 `/me`·`/me/rentals`·`/me/items`)는 `feat/fe-my-pages`로 분리**(사용자 결정). 변경 파일이 SCR-07·08만으로 약 30개, SCR-12까지 약 38개라서. 빌린 내역 API(`GET /members/me/rentals`) 훅·목, 임시 API(내 물품·물품별 예약 현황)도 그 브랜치에서 만든다. 상태 변경 뒤 `['rentals', 'me']` 무효화는 미리 넣어 둔다. git-workflow.md §2에 행 추가.
  - 그 브랜치에서 할 일: `/me` 메뉴(빌린 내역·내 물품 관리만), `/me/rentals` 탭(`?tab=`, 기본 ONGOING) + `useInfiniteQuery`(page 0부터, `hasNext`면 +1, size 보내지 않음, 정렬 다시 하지 않음), `/me/items`(AVAILABLE·RENTING 탭, 예약 현황 BottomSheet, 카드 메뉴는 "수정"만 — 삭제는 feat/fe-item-pages에서 REQ-008과 함께), frontend/CLAUDE.md "무한 스크롤 nextCursor" 문장을 page 방식과 맞추기.
- **react-hook-form 7.90.0**(exact). 최신 7.91.0은 10/23 배포라 2주가 안 돼 그 전 버전(10/11). peer React 19 지원. dayjs는 넣지 않음.

## 임시 API (openapi.json에 없음, `// TODO: 백엔드 확정 전 임시`)
| API | 가정한 응답 | 읽는 곳 |
| --- | --- | --- |
| `GET /api/items/{id}` (REQ-011) | `{ itemId, name, images: [{ imageId, url }], pricePerDay, deposit, owner: { memberId, nickname, regionName }, isOwner }` — item.md REQ-011 목록에서 예약 화면에 필요한 것만, 이름은 rental API(`owner`·`memberId`)에 맞춰 가정 | `features/item/api.js` `toItemSummary` |
| `POST /api/auth/reissue` | `{ accessToken }` (기존 `extractAccessToken` 가정 그대로). 목은 항상 성공해 RequireAuth를 통과시킨다 | `shared/api/reissue.js`, 목 `mocks/auth.js` (feat/fe-auth-region에서 교체) |

## 목 (`mocks/`)
- **대여 API 10개 목은 openapi.json 스키마 그대로**(응답 필드·에러 코드·메시지는 응답 예시). 검사 순서는 백엔드 notes와 같게(입력 400 → 404 → 403 당사자 → 403 제공자 → 409 상태·시점 → 사진 400). `delay(400)`으로 제출 중 상태를 볼 수 있게.
- **결제 플래그**: `VITE_PAYMENT_ENABLED=false`면 예약은 바로 RESERVED, 상태 확인은 ABNORMAL이어도 COMPLETED(feat/rental-return notes의 1순위 규칙과 같음). true면 결제 금액 > 0인 예약은 PENDING_PAYMENT(15분), ABNORMAL은 RETURNED 유지.
- **메모리 저장소라 새로고침하면 시드로 돌아간다.** 올린 사진은 `URL.createObjectURL`로 돌려준다.
- 목은 화면 상수(features)를 가져오지 않고 정책 값을 따로 둔다(서버 흉내라 화면 상수가 틀려도 목이 같이 틀리지 않게).
- **`cancelFee`는 취소 전에도 0**(null 아님): 백엔드 `RentalDetailResponse.cancelFee`가 `int`, `rentals.cancel_fee`가 `NOT NULL DEFAULT 0`. 목도 0으로 맞췄다. 화면은 `> 0`일 때만 보여 줘서 어느 쪽이든 동작은 같다.
- 나 = 회원 1(동네주민). 날짜는 `todayInSeoul()` 기준 상대값(D+n).

| id | 상태 | 내 역할 | 경우 |
| --- | --- | --- | --- |
| 1 / 21 | PENDING_PAYMENT | 대여자 / 제공자 | 결제 만료 전 |
| 2 | RESERVED (D+4) | 대여자 | 단독 취소, 수수료 없음 |
| 3 | RESERVED (D+2) | 대여자 | 수수료 구간(대여료 30%) |
| 22 | RESERVED (D+3) | 제공자 | 인계 확인 비활성 |
| 4 / 23 | RESERVED (D+0) | 대여자 / 제공자 | 시작일 당일, 동의 요청 없음 / 인계 가능 |
| 5 / 24 | RESERVED | 대여자 / 제공자 | 동의 요청 진행 중, 내가 요청 |
| 6 / 25 | RESERVED | 대여자 / 제공자 | 동의 요청 진행 중, 상대가 요청(25는 사유 OTHER) |
| 7 / 26 | RENTING | 대여자 / 제공자 | 반납 요청 / 반납 대리 접수 |
| 8 / 27 | OVERDUE | 대여자 / 제공자 | 연체 2일(삭제된 물품) / 1일 |
| 9 / 28 | RETURNED | 대여자 / 제공자 | 판정 전(28은 체크리스트 '아니오' + 메모) |
| 29 | RETURNED | 제공자 | 대리 접수(writerRole OWNER), 판정 전 |
| 10 / 30 | COMPLETED | 대여자 / 제공자 | 정상 / 이상 판정 |
| 11 / 31 | CANCELED | 대여자 / 제공자 | RENTER(수수료 기록) / MUTUAL |
| 900 | RESERVED | 남 | 403 RENTAL_NOT_PARTICIPANT |
| 901 / 902 | PENDING_PAYMENT | 남 | 물품 101 캘린더: 만료 지남(예약 가능) / 만료 전(예약됨) |
| 999 | — | — | 404 RENTAL_NOT_FOUND |

| 물품 | 쓰임 |
| --- | --- |
| 101 텐트 | 예약 기본. 가능 기간 D+0~D+40, D+45~D+90(사이 예약 불가), 예약됨 섞임, 달 넘는 선택 가능 |
| 102 드릴(보증금 없음) | 예약하면 항상 409 RENTAL_PERIOD_CONFLICT |
| 103·104 | 내 물품(103 예약 화면 → "내 물품은 예약할 수 없어요") |
| 106 | 삭제된 물품(isDeleted) 표시 |

## 공통
- **버튼 표 `getRentalActions`는 `{ actions: [{ type, label, variant, disabledReason }], notice }`를 돌려준다.** 비활성 사유는 버튼 아래 한 줄(aria-describedby). notice는 연체 안내·대여자 상태 확인 대기 안내·결제 카운트다운(`paymentExpiresAt`, 결제가 켜졌을 때만 — 꺼져 있으면 PENDING_PAYMENT여도 notice 없음). 결제 플래그는 네 번째 인자 `{ isPaymentEnabled }`(기본 env)로 받아 테스트에서 바꾼다.
- **RESERVED·시작일 이후에 상대가 요청하면 제공자 바는 "거절 · 동의 · 인계 확인" 3개.** 인계는 동의 요청과 무관하게 서버가 허용하므로 숨기지 않는다. 360px에서 한 줄에 들어간다(헤드리스 확인).
- **"취소 요청 중"은 비활성 버튼 + "M월 D일 HH:mm까지 상대의 응답을 기다려요".** 진행 중 요청 카드(본문)에도 사유·만료 시각.
- **연체 안내는 하단 바 notice로 한 번만**(본문에 중복하지 않음). 바가 sticky라 항상 보인다.
- **캘린더 days는 `useQueries` + 모듈 함수 `combine`으로 합친다**(지시의 useMemo 대신). combine 참조가 고정이라 결과가 그대로면 이전 days를 돌려줘 같은 효과이고, 연 달 수에 따라 길이가 달라지는 deps 배열 문제가 없다. 한 번 연 달은 계속 불러 둔다.
- **`findInspection`은 못 불러옴(undefined)과 기록 없음(null)을 구분**한다. RETURNED에서 못 불러왔으면 버튼을 그리지 않으려고.
- **에러 문구는 `errorMessages.js` 한 곳**, openapi 대여 API 응답 예시의 코드 25개 전부. 모르는 code(CLIENT_* 등)는 ApiError.message. stale 3종은 같은 문구 + 상세 무효화(`['rentals', id]`가 inspections 키의 앞부분이라 둘 다).
- **동의 요청 문구용으로 `CANCEL_AGREEMENT_EXPIRE_HOURS`(48, `cancel-agreement.expire-hours`)를 상수에 추가.** 지시 목록에는 없었지만 모달 안내("48시간 안에 응답이 없으면 만료")에 필요.
- **체크리스트 문구는 질문형(`RETURN_CHECKLIST_ITEM.question`)** — 시안의 "구성품이 모두 있나요?"처럼 예/아니오 Chip과 짝이 맞게. 라벨(state-machines §5 한글명)도 함께 둔다.

## 시안과 다르게 한 곳
- **취소 정책 문구**: 시안 "전날까지 전액 환불" 대신 policies.md §2. 결제가 켜져 있으면 "시작일 3일 전까지 무료 취소, 2일 전부터 전날까지는 대여료의 30%가 수수료로 부과돼요"(숫자는 `CANCEL_FEE`). **결제가 꺼진 1순위에는 수수료를 받지 않으므로** "시작일 전날까지는 바로 취소할 수 있고, 시작일부터는 상대의 동의가 있어야 취소돼요"로 취소 방법만 안내한다(아래 수수료 결정과 같은 이유).
- **취소 수수료 표시는 결제가 켜졌을 때만**(사용자 결정): 취소 전 모달 안내, 취소 후 토스트·취소 정보 카드 모두. 백엔드는 1순위에도 cancelFee를 계산해 응답하지만 실제로 받은 돈이 없어서. rental.md REQ-015 "1순위" 줄에 화면 규칙으로 한 문장 덧붙였다.
- **결제 버튼**: 결제 꺼짐 → "예약하기", 켜짐 → "N원 결제하기" + "15분 안에" 안내(세로 쌓기). 대여 상세의 "결제하기"는 2순위라 버튼만 있고 동작이 없다. 결제가 켜져도 예약 후에는 대여 상세로 간다(결제 화면은 2순위).
- **합계 이름**: 결제 꺼짐 "합계", 켜짐 "총 결제 금액"(시안). 1순위엔 결제가 없으므로.
- **보증금 보관 안내**: 결제가 켜지고 보증금 > 0일 때만, 첫 문장만("반납 후 제공자가 상태를 확인하면 돌려드려요"). 손상 등급 차감 문장은 2순위(REQ-049)라 뺐다.
- **SCR-08 시안의 AI 상태 비교·손상 등급·"차감 제안하기"는 넣지 않는다**(2순위). 대신 "이상 있음"(danger 외곽선) · "정상 확인"(primary). 1순위 이상 판정은 내용(500자)·사진만 받고 기록 후 완료.
- **체크리스트 3개 → 5개**: state-machines §5 `ReturnChecklistItem` 기준.
- **연체료 미표시**: RentalDetailResponse에 없고 REQ-023(2순위) 범위. 지난 일수만 안내.
- **스텝바 라벨 11px → 12px(`text-xs`)**: 11px 예외는 하단 탭 라벨 전용(frontend/CLAUDE.md UI 규칙).
- **예약 화면 금액 줄 14px → `text-body`(15px)**: 14px는 작은 버튼·토스트·캘린더 날짜 예외에만.
- **보증금 보관 안내 카드는 `accent-soft` 면만**: 시안의 테두리·면 색(#F0DDBF·#FDF8EF)은 토큰에 없어서.

## 화면
- **모달·시트는 열린 동안만 렌더**(`ActionDialog`) — 열 때마다 폼이 새로 시작해 reset을 따로 하지 않는다. useDialog cleanup이 포커스를 돌려준다.
- **모달 안의 오류는 모달 안에**(stale 포함). 네 폼 모두 `useSubmitErrorHandler`를 onError로 쓴다: details[]는 `applyFieldErrors`로 바로 setError, 나머지는 `root.server`.
- **서버 필드 오류 포커스는 effect로**(`mutateAsync` + 다음 tick 대신): 제출 중엔 필드가 disabled라 setError의 shouldFocus가 먹지 않는다. 첫 필드 이름을 state에 두고 `isPending`이 false로 다시 그려진 뒤 effect에서 `setFocus`. React가 언제 커밋할지 tick을 추측하지 않아도 돼서. 헤드리스에서 worker.use로 400 details를 돌려 네 폼(reasonText·memo·체크리스트 첫 '예'·resultNote) 포커스 확인.
- **사진 필드는 `InspectionImageField`(Controller) — 감싼 div에 ref + tabIndex -1** 이라 사진 누락도 `shouldFocusError`로 포커스가 간다. 제출 중에는 fieldset disabled.
- **이름 규칙(conventions.md §5)**: 컴포넌트만 내보내는 `FormParts.jsx`는 PascalCase. 이 브랜치의 boolean은 `is`/`has`(`isValidId`·`isNotFound`·`isAccept`·`isNoteRequired`·`isPaymentEnabled` 옵션·스텝 `isDanger`·`hasPeriodChanged` 등, 목 포함). 공통 UI(develop)의 `open`·`danger`·`dismissible` 같은 props와 API 필드(`requestedByMe`)는 그대로 둔다.
- **`watch` 대신 `useWatch`**: react-hooks v7의 React Compiler 검사가 `watch`를 쓰는 컴포넌트에 경고(incompatible-library)를 내서.
- **기타 사유·반납 메모 검증은 다른 필드 값(values)으로 조건을 다시 본다.** 입력란이 사라져도 값·규칙이 남을 수 있어서.
- **예약 실패 처리**: `RENTAL_PERIOD_CONFLICT`·`RENTAL_INVALID_PERIOD`·`RENTAL_OUT_OF_AVAILABLE_PERIOD` → 토스트 + 캘린더 무효화 + 선택 초기화, `REGION_NOT_VERIFIED` → 토스트 + 본문에 "동네 인증하기"(`/regions`) 카드, `RENTAL_OWN_ITEM`·`isOwner` → 예약 대신 안내, `ITEM_NOT_FOUND` → ErrorState + 홈.
- **대여 상세 `RENTAL_NOT_FOUND`·`RENTAL_NOT_PARTICIPANT`·숫자가 아닌 id → "대여 정보를 볼 수 없어요" + 홈.** 숫자가 아니면 요청하지 않는다(`useRental` enabled).
- **조회 실패: 전체 ErrorState는 데이터가 없을 때(`isInitialLoadError`)만.** 이미 보던 화면에서 재조회만 실패하면 화면·열린 모달은 그대로 두고 `useRefetchErrorToast`가 토스트 한 번(실패 시각 `errorUpdatedAt`이 바뀔 때만). 대여 상세(상세·inspections)·예약(물품·연 달 캘린더) 모두. 판단은 `shared/lib/queryState.js` 순수 함수로 테스트. 모달이 열려 있으면 토스트는 그 아래라 가려진다(모달 안 오류는 모달이 따로 보여 줌).
- **inspections는 RENTING·OVERDUE·RETURNED·COMPLETED에서만 부른다**(인계 전·취소에는 기록이 없음).
- **반납 체크리스트 예/아니오 칩은 `min-w-11`(44px)**: '예' 칩이 41.6px라 가로 터치 영역이 모자랐다(헤드리스 360px 측정). 공통 Chip은 바꾸지 않고 이 화면에서만.
- **사진은 누르면 원본을 새 탭으로**(라이트박스는 만들지 않음). 대표 사진 나란히 + 나머지는 44px 줄.

## 백엔드에 확인할 것
- **`GET /api/items/{id}`(REQ-011, 아직 openapi.json에 없음)** 응답에 예약 화면이 쓰는 필드: `images[].url`(첫 장을 썸네일로), `pricePerDay`, `deposit`, `owner{ nickname, regionName }`, `isOwner`. 이름은 대여 API에 맞춰 가정했다(`features/item/api.js` `toItemSummary` 한 곳).
- **`POST /api/auth/reissue`** 응답이 `{ accessToken }`인지(`shared/api/reissue.js` `extractAccessToken`).
- **2순위**: 대여 상세 응답(`RentalDetailResponse`)에 `depositStatus`와 보증금 차감 정보(차감액·사유)가 필요하다(REQ-025·049 화면). 지금 응답에는 없다.

## 결제 브랜치에서
- **결제 카운트다운이 끝나면 지금은 "결제 시간이 지났어요"만 보이고 상세를 다시 불러오지 않는다.** 1순위(결제 꺼짐)에는 PENDING_PAYMENT가 생기지 않아 만료되는 화면이 없으므로, 만료 시 재조회(상세 무효화 → 서버가 CANCELED로 바꾼 상태 표시)는 결제 브랜치에서 넣는다.

## 확인
- 헤드리스 Chrome(CDP)으로 `VITE_USE_MOCK=true` 390px·360px: 대여자(예약 → 상세 → 사유 검증·포커스 → 취소, 시작일 당일 동의 요청 → "취소 요청 중", 상대 요청 동의 → MUTUAL), 제공자(시작일 전 인계 비활성 → 당일 인계(사진 누락 오류) → 대리 접수(체크리스트 미선택·'아니오' 메모 오류) → 정상 확인 → 거래 완료, 이상 있음 → 판정 표시), 오류(물품 102 기간 충돌 → 토스트·선택 초기화, 403·404 대여, 다른 곳에서 먼저 반납된 26에 대리 접수 → 시트 안 "상태가 바뀌었어요" + 배지 갱신). `VITE_PAYMENT_ENABLED=true`로 결제 버튼·카운트다운·수수료 안내·취소 정보 수수료도 확인.
- **백엔드 로컬 서버 연동은 아직 불가**: 로그인 API(REQ-002)가 없어 실제 AT를 받을 수 없다. 대여 API는 모두 인증 필요라 RequireAuth 뒤 화면이 401 → 재발급 실패로 로그인 화면으로 간다. feat/member-auth 머지 후 확인한다(백엔드 컨트롤러는 대여 3개뿐. local 비밀키로 JWT를 직접 서명하면 부를 수는 있지만 이번에는 하지 않았다).
- 리뷰 반영 확인(헤드리스, worker.use로 목 응답을 바꿈): 반납 시트를 연 채 상세 재조회 500 → 시트·화면 유지, 토스트 1번(시트 안 조작으로 다시 그려도 늘지 않음). 예약 화면에서 기간을 고른 뒤 캘린더 재조회 500 → 선택·캘린더 유지, 토스트 1번. 네 폼 400 details → 첫 오류 필드 포커스. `/items/abc/reserve` → 요청 없이 "찾을 수 없어요". 목 `2026-02-30`·`2026-13-01` → 400 COMMON_INVALID_INPUT(details startDate/endDate). 스텝바 지난 단계 "예약 완료"처럼 읽힘. 360px 시드 25 바 버튼 3개 한 줄(각 약 104px, 잘림 없음).
- **500kB 청크 경고는 이 브랜치에서 처음 생긴다**(develop b877af8 396kB → 이 브랜치 508kB). develop은 화면이 공통 UI·뮤테이션을 아직 쓰지 않아 트리 셰이킹으로 빠져 있었다. 늘어난 111kB: react-hook-form 35.7 · 화면에서 쓰기 시작한 shared/ui 20.1 · rental 컴포넌트·features·pages 약 32 · TanStack Query(useMutation·useQueries) 13. lazy 라우트 분리는 별도 작업(사용자 확인 대기).
- `npm run build` 결과 JS 번들에 MSW·목 코드 없음. `dist/mockServiceWorker.js`는 `public/`에서 복사되는 기존 파일(등록하지 않으면 동작하지 않음).
