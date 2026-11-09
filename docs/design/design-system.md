# 프론트 디자인 시스템 (현재 구현 기준)

`develop`의 프론트엔드가 실제로 쓰는 토큰·공통 컴포넌트·레이아웃 패턴을 정리하고, 시안·규칙과 어긋나는 곳을 마지막 절 "정리 대상"에 모은다. 정리 대상은 `chore/fe-design-refactor` 브랜치의 작업 범위다.

## 1. 기준과 범위

- **이 문서의 기준 값은 현재 토큰(`frontend/src/styles/index.css`의 `@theme`)이다.** 시안(`mockups/*.html`)과 값이 다르면 표의 "시안" 열에 시안 값을 적고 "차이" 열에 표시만 한다. 어느 쪽으로 맞출지는 "정리 대상"에서 항목별로 정한다.
- 사용 규칙(쓸 수 있는 토큰·예외 크기·모서리·Button variant 등)은 `frontend/CLAUDE.md` "UI 규칙"이 원문이다. 이 문서는 그 규칙이 코드에서 어떻게 쓰이는지를 보여 주고, 규칙 문장을 다시 적지 않는다.
- 조사 범위: `frontend/src`의 `app/`·`shared/`·`features/`·`pages/`. `/dev`(미리보기)와 `mocks/`(목 이미지 자리 색)는 제외.
- 시안이 있는 화면: SCR-03 홈(`Home.html`), 05 상세(`Detail.html`), 07 예약(`Booking.html`), 08 대여 상세(`RentalDetail.html`), 10 채팅방(`Chat.html`, 미구현). 디자인 시스템은 `Main.html`.

## 2. 토큰

### 2.1 색

| 토큰 (Tailwind 클래스 접미사) | 코드 값 | 시안 값 | 차이 | 용도 |
| --- | --- | --- | --- | --- |
| `primary` | `#1f6f54` | `#1F6F54` | 같음 | 버튼·강조·선택 |
| `primary-hover` | `#174f3d` | 링크 hover만 | 시안에 토큰 없음 | 채움 버튼 hover |
| `primary-soft` | `#e8f1ec` | `#E8F1EC` | 같음 | 선택 배경·초록 배지·거래 장소 상자 |
| `accent` | `#c77a1a` | `#C77A1A` | 같음 | 보증금·주의 |
| `accent-strong` | `#8f560f` | 배지·카드 글씨로만 | 시안에 토큰 없음 | 보증금 글씨, 주의 배지 글씨 |
| `accent-soft` | `#fbf1e2` | 배지 배경으로만 | 시안에 토큰 없음 | 주의 배지·주의 안내 상자 |
| `info` / `info-soft` | `#22508f` / `#e6edf8` | 배지로만 | 시안에 토큰 없음 | "대여 예정" 배지 |
| `danger` | `#b0391f` | `#B0391F` | 같음 | 연체·이상·오류 문구·위험 버튼 |
| `danger-soft` | `#fbeae5` | 배지 배경으로만 | 시안에 토큰 없음 | 연체 배지, 위험 버튼 hover |
| `ink` | `#1c1c1a` | `#1C1C1A` | 같음 | 본문, 선택 칩, 검정 배지, 토스트 |
| `ink-muted` | `#5c5f5a` | `#5C5F5A` | 같음 | 보조 텍스트 |
| `ink-subtle` | `#8b8e88` | 요일 머리글·비활성 버튼 글씨 | 시안에 토큰 없음 | 요일·비활성·placeholder·글자 수 |
| `ink-faint` | `#a3a39c` | 캘린더 예약됨 | 시안에 토큰 없음 | 이미지 자리 아이콘, 캘린더 예약됨 |
| `ink-disabled` | `#c9c7c0` | 캘린더 지난 날짜 | 시안에 토큰 없음 | 캘린더 예약 불가 |
| `surface` | `#f6f5f1` | `#F6F5F1` | 같음 | 배경 면·회색 버튼·안내 상자 |
| `line` | `#e4e2dc` | `#E4E2DC` | 같음 | 테두리·탭·하단 바·비활성 버튼 면 |
| `divider` | `#f0eee9` | 헤더 아래선·목록 구분선 | 시안에 토큰 없음 | 헤더 아래선·목록 구분선 |
| `placeholder` | `#edebe5` | 이미지 자리 | 시안에 토큰 없음 | 이미지 자리·스켈레톤 |

- `Main.html`의 8색은 코드 값과 모두 같다. 코드에만 있는 11개 토큰은 화면 시안의 인라인 값을 토큰으로 올린 것이다.
- 토큰 밖 색: `bg-white`·`text-white`(허용), `bg-black/50`(모달·시트 배경막), `bg-white/90`(상세 이미지 위 뒤로 가기), `bg-ink/60`(이미지 위 배지·삭제 버튼). → 7.1 P3.
- 시안에만 있고 코드에 없는 색: 제공자 아바타 `#D9E6DF`(코드는 `primary-soft`), 채팅 사진 자리 `#E2E0D9`·`#D6D3CB`(채팅 미구현).

### 2.2 글자

글꼴: IBM Plex Sans KR 400·500·600·700 (`index.css` `@font-face`). 시안과 같다.

| 토큰 | 코드 | 시안 (`Main.html`) | 차이 | 대표 사용처 |
| --- | --- | --- | --- | --- |
| `text-title` | 22/700 | 22/700 | 같음 | 상세 물품명, 홈 동네 이름 |
| `text-heading` | 17/700 | 17/700 | 같음 | 모달·시트 제목, 빈 상태·에러 제목 |
| `text-body` | 15/400 | 15/400 | 같음 | 기본(`body`), 버튼 `md`, 입력, 탭 |
| `text-price` | 16/700 | 16/700 | 같음 | 가격, **섹션 제목(h2)에도 사용** → 7.3 |
| `text-caption` | 13/400 | 13/400 | 같음 | 보조 정보, 라벨, 칩 |
| 예외 `text-xs` | 12px | 12px(배지·힌트) | 같음 | 배지, 힌트, 폼 오류 일부, 단계 라벨 |
| 예외 `text-sm` | 14px | 14px(캘린더 월·날짜) | 같음 | 버튼 `sm`, 토스트, 캘린더 |
| 예외 `text-lg` | 18px | 18px(화면 헤더) | 같음 | `PageHeader` 제목, PC 헤더 로고 |
| 예외 `text-[11px]` | 11px | 11px(하단 탭) | 같음 | 하단 탭 라벨 |

화면 시안에는 5단계 밖 크기가 더 있다(상세 물품명 21px, 홈 동네 19px, 섹션 제목 16px·15px, 채팅 말풍선 14px, 채팅 배지 11px). 코드는 가장 가까운 토큰으로 맞췄다 → 7.3.

### 2.3 간격

- 시안 기준: 4px 단위(4·8·12·16·24·32), Tailwind 기본 스케일과 대응.
- 코드에서 자주 쓰는 값:

| 자리 | 코드 | 시안 | 차이 |
| --- | --- | --- | --- |
| 화면 좌우 여백 | `px-4` 16 | 16 | 같음 |
| 화면 헤더 높이 | `h-14` 56 | 56 | 같음 |
| 목록 카드 위아래·사진과 글 사이 | `py-3.5`·`gap-3.5` 14 | 14 | 같음(4px 단위 밖) |
| 안내 상자 안쪽 | `p-3.5` / `px-3.5 py-3` / `px-4 py-3` / `p-4` / `px-4 py-5` | 12 14 (`RentalDetail` AI 상자) | 섞여 있음 → 7.2 |
| 하단 바 안쪽 | `px-4 pt-3 pb-4` | 12 16 16 | 같음 |
| 폼 필드 사이 | `gap-4` 16 | — | 시안 없음 |

14px(`3.5`)는 시안에서도 자주 쓰는 값이라 4px 단위 밖이어도 유지한다.

### 2.4 모서리

| 대상 | 코드 | 시안 | 차이 |
| --- | --- | --- | --- |
| 입력 | `rounded-lg` 8 | 8 | 같음 |
| 이미지 썸네일 | `rounded-[10px]` | 10 | 같음 |
| 카드·버튼·안내 상자·토스트 | `rounded-xl` 12 | 12 | 같음 |
| 칩·배지·아이콘 버튼 | `rounded-full` | 999 | 같음 |
| 모달, 바텀시트 윗변 | `rounded-2xl` 16 | 시안 없음 | 규칙 밖 → 7.1 P3 |
| 스켈레톤 기본 | `rounded-md` 6 | 시안 없음 | 규칙 밖 → 7.1 P3 |
| 캘린더 범위 양 끝 | `rounded-l-full`·`rounded-r-full` | 999 0 0 999 | 같음 |
| 예약 화면 물품 썸네일(52px) | `rounded-[10px]` | 8 | 다름 → 7.3 |

### 2.5 그림자

시안에는 그림자가 없다. 코드는 떠 있는 요소 4곳에만 쓴다.

| 요소 | 코드 |
| --- | --- |
| `FloatingActionButton` | `shadow-[0_6px_16px_rgba(28,28,26,0.18)]` |
| `Modal`, `BottomSheet` | `shadow-xl` |
| `Toaster` | `shadow-lg` |

→ 7.1 P3 (토큰으로 둘지 확인).

## 3. 공통 컴포넌트 (`frontend/src/shared/ui`)

모두 `shared/ui/index.js`에서 export한다.

| 컴포넌트 | 용도 | 주요 props | 쓰는 곳 (대표) |
| --- | --- | --- | --- |
| `Button` | 버튼 | `variant` primary·outline·secondary·danger, `size` md(48)·sm(44), `fullWidth`, `isLoading`(Spinner + aria-busy) | 로그인·가입 제출, `ConfirmModal` |
| `ButtonLink` | 버튼 모양 링크 | `Button`과 같은 variant·size, `disabled`(aria-disabled) | 상세 "예약하기", 결제 결과 |
| `buttonClassName` | 버튼 클래스 함수 | variant·size·fullWidth | `Button`·`ButtonLink` 내부 |
| `Badge` | 상태 배지 | `tone` gray·blue·green·red·amber·ink (`Main.html` 상태 배지 6종) | `RentalHistoryCard`, 대여 상세 헤더, `ItemCard` |
| `Chip` | 필터 칩(토글) | `selected`(aria-pressed), 높이 34 | `CategoryChips`(홈·검색) |
| `TextField` | 라벨·입력·힌트·오류·글자 수 | `label`, `hint`, `error`, `required`, `multiline`, `maxLength` | 로그인·가입, `ItemForm` |
| `Calendar` | 대여 기간 캘린더(범위 선택) | `days`, 기간 제한·라벨은 props(정책은 features 상수) | 상세(SCR-05), 예약(SCR-07) |
| `ImageUploader` | 사진 여러 장 올리기·순서 바꾸기 | 장수·용량·형식 props | `ItemForm`, 인계·반납 시트 |
| `Thumbnail` | 이미지 또는 이미지 자리 | `url`, 크기는 `className` | `ItemCard`(104), `RentalHistoryCard`(64) |
| `Skeleton` | 로딩 자리 | 크기·모서리는 `className` | 홈·상세·목록 로딩 |
| `Spinner` | 회전 표시 | `size` sm·md·lg, `label`(있으면 role=status) | `Button` 로딩, 결제 성공 처리 중 |
| `EmptyState` | 빈 목록 | `title`, `description`, `action` | 나의 대여·내 물품·검색 |
| `ErrorState` | 조회 실패 | `title`, `message`, `onRetry`(secondary 버튼) | 상세·예약·대여 상세 |
| `InfiniteListFooter` | 무한 스크롤 끝(로딩·다시 시도) | `query`(useInfiniteQuery 결과) | 나의 대여·내 물품 목록 |
| `Tabs` | 탭(키보드 이동) | `items`, `value`, `onChange`, `label` | 나의 대여·내 물품 |
| `Modal` | 가운데 모달(`<dialog>`) | `open`, `onClose`, `title`, `footer`, `dismissible` | `DeleteItemModal`, `CancelModal` |
| `ConfirmModal` | 확인·취소 모달 | `confirmLabel`, `cancelLabel`, `danger`, `isLoading` | 삭제·취소 확인 |
| `BottomSheet` | 하단 시트(PC에서는 가운데 모달) | `Modal`과 같음 | 검색 필터, 인계·반납·이상 시트 |
| `Toaster` / `toast` | 토스트 | `toast.show({ message, tone })` tone default·danger | 저장 결과, 재조회 실패 |
| `CloseIcon` | 닫기 아이콘(export 안 함) | `className` | `Modal`·`BottomSheet`·`ImageUploader` |

## 4. 레이아웃 패턴

### 4.1 화면 틀 (`frontend/src/app/layout`)

| 요소 | 모양 | 비고 |
| --- | --- | --- |
| `AppLayout` | 세로 flex, 본문 `max-w-5xl px-4`. PC(xl, ≥1280)는 상단 헤더(높이 64, 로고 + 주 메뉴), 모바일은 하단 탭 | 라우트 `handle.hideTabBar`면 하단 탭과 아래 여백을 뺀다 |
| 하단 탭 | 높이 64, `border-t border-line`, 5칸(홈·검색·채팅·알림·나의 대여), 라벨 11px, 선택 시 아이콘 채움 + `font-semibold text-ink` | 시안(`Main.html` 64)과 같음 |
| `PageHeader` | sticky, 높이 56, `BackButton` + 제목 `text-lg font-bold` + 오른쪽 `action` | 시안 헤더(56, 18/700)와 같음 |
| `BackButton` | 44 원형 아이콘 버튼. 앱 안 기록이 없으면 홈으로 | 상세는 이미지 위에 `bg-white/90`로 띄운다 |
| `BottomActionBar` | sticky 하단, `border-t border-line`, `px-4 pt-3 pb-4` + safe-area | `hideTabBar` 화면 전용 |
| `FloatingActionButton` | 오른쪽 아래 알약 버튼(높이 52, `primary`, 그림자), 하단 탭 위 | 홈 "+ 물품 등록" |
| 홈 헤더 | `PageHeader` 대신 직접 만든 sticky 헤더(동네 이름 `text-title` + 알림 아이콘) | 시안도 별도 모양 |
| 인증 화면 | `AuthPageLayout`(AppLayout 밖, `max-w-md`, `PageHeader` 포함) | SCR-01 |

### 4.2 목록·상태

- 목록 카드: `ItemCard`·`RentalHistoryCard` — `flex gap-3.5 py-3.5`, 왼쪽 `Thumbnail`, 오른쪽 제목(`font-semibold` truncate)·보조(`text-caption text-ink-muted`)·금액. 카드 사이 구분선은 목록의 각 `li`에 `border-b border-divider`로 넣는다(`ItemListView`, `MyRentalsPage`, `MyItemsPage`).
- 로딩: 목록 모양을 흉내 낸 `Skeleton` 여러 개. 다음 페이지 로딩은 `InfiniteListFooter`의 `Spinner`.
- 빈 상태: `EmptyState`(탭별 문구는 페이지 상수, 예: `MyRentalsPage`).
- 에러: 첫 조회 실패는 `ErrorState` + 다시 시도, 재조회 실패는 토스트(`useRefetchErrorToast`), 다음 페이지 실패는 `InfiniteListFooter` 안 다시 시도.

### 4.3 바텀시트·모달

- 선택·입력이 길면 `BottomSheet`(검색 필터, 인계·반납 사진, 이상 신고, 물품별 예약 목록), 확인 한 번이면 `ConfirmModal`(삭제·취소).
- 둘 다 `<dialog>` top layer라 토스트가 가려진다. 모달 안 오류는 모달 안에 문구로 보인다(`DeleteItemModal`, `CancelModal`).

### 4.4 폼

- 필드: `TextField`(라벨 `text-caption font-semibold`, 힌트 `text-xs text-ink-muted`, 오류 `text-xs text-danger`, 글자 수 오른쪽).
- 필드에 붙지 않는 제출 오류: `FormError`(rental)·`FormRootError`(auth) 등 `role="alert"` 문구 → 7.2 중복.
- 사진: `ImageUploader`(react-hook-form `Controller`로 감쌈, 예: `InspectionImageField`).
- 제출 버튼: 화면 폼은 `BottomActionBar` 안 `Button fullWidth`, 시트 폼은 `footer`.

### 4.5 안내 상자

정보를 묶어 보여 주는 `rounded-xl` 상자. 아직 공통 컴포넌트가 없다 → 7.2.

| 색 | 쓰는 곳 |
| --- | --- |
| `bg-surface` | 금액 요약(`AmountSummary`), 검사 기록(`InspectionRecords`), 취소 정보(대여 상세), 동네 안내(`RegionFinder`), 등록 안내(`ItemFormPage`, 테두리 있음) |
| `bg-accent-soft` | 동네 인증 필요·보증금 보관 안내(예약 화면) |
| `bg-primary-soft` | 거래 장소(`ItemInfo`) |

## 5. 화면(SCR)별 패턴

| SCR | 화면 (페이지) | 헤더 | 하단 | 주요 패턴 |
| --- | --- | --- | --- | --- |
| 01 | 로그인·가입 (`LoginPage`, `SignupPage`) | `AuthPageLayout`(PageHeader) | 없음 | `TextField`, `Button` fullWidth |
| 02 | 동네 (`RegionPage`) | `PageHeader` | 하단 탭 | 목록·검색, `EmptyState`, 안내 상자 |
| 03 | 홈 (`HomePage`) | 직접 만든 홈 헤더 | 하단 탭 + FAB | `CategoryChips`(Chip), `ItemListView`(ItemCard), Skeleton |
| 04 | 검색 (`SearchPage`) | `PageHeader` | 하단 탭 | 검색창, Chip, `SearchFilterSheet`(BottomSheet), `EmptyState` |
| 05 | 물품 상세 (`ItemDetailPage`) | 이미지 위 `BackButton` | `BottomActionBar`(가격·보증금 + 예약하기) | 캐러셀, `Calendar`, `ConfirmModal`(삭제) |
| 06 | 물품 등록·수정 (`ItemFormPage`) | `PageHeader` | `BottomActionBar` | `ItemForm`(TextField·ImageUploader·Calendar·지도) |
| 07 | 예약·결제 (`RentalReservePage`, `Payment*Page`) | `PageHeader` | `BottomActionBar` | `Calendar`, `AmountSummary`, 안내 상자, `ErrorState` |
| 08 | 대여 상세 (`RentalDetailPage`) | `PageHeader` + 상태 `Badge` | `RentalActionBar`(BottomActionBar) | `RentalStepBar`, 시트(인계·반납·이상), `InspectionRecords` |
| 12 | 나의 대여 (`MyPage`, `MyRentalsPage`, `MyItemsPage`, `MyProfilePage`) | `MyPage`·`MyProfilePage`는 직접 만든 제목, 목록은 `PageHeader` | 하단 탭 | `Tabs`, 목록 카드, `InfiniteListFooter`, `EmptyState` |
| — | 준비 중·없는 페이지 (`ComingSoonPage`, `NotFoundPage`) | 직접 만든 제목 | 하단 탭 | 기본 팔레트 사용 → 7.1 |

SCR-09~11·13~16은 아직 없다(채팅·알림 탭은 `ComingSoonPage`).

## 6. 시안 대조 요약

| 항목 | 시안 | 코드 | 판단 |
| --- | --- | --- | --- |
| 버튼 높이 | 48 (`RentalDetail` 하단 바만 50) | md 48 / sm 44 | 디자인 시스템 48 유지 |
| 칩 | 34, 13px, 선택 ink 채움 | `h-8.5` 34, caption | 같음 |
| 배지 | 12/600, `4px 10px` | `text-xs font-semibold px-2.5 py-1` | 같음 |
| 하단 탭 | 64, 11px | 64, 11px | 같음 |
| 화면 헤더 | 56, 제목 18/700 | 56, `text-lg font-bold` | 같음 |
| 물품 카드 | 사진 104, 글 사이 3px, 단위 `/일` 13/500 muted | 사진 104(`size-26`), `gap-0.5`(2px), 단위 `text-body`(15/400) muted | 다름 → 7.3 |
| 상세 캐러셀 번호 | 12px, `3px 9px`, ink 72% | `text-caption font-semibold`, `px-3 py-1`, `ink/60` | 다름 → 7.3 |
| 상세 섹션 제목 | 16/700 | `text-price`(16/700) | 값 같음, 토큰 의미 다름 → 7.3 |
| 캘린더 날짜 칸 | 패딩 8(약 34) | 높이 44 | 터치 영역 규칙(44)으로 코드 유지 |
| 대여 단계 점 | 12, 현재 단계 테두리 3px | `size-3`, `border-[3px]` | 같음(임의 값) |

## 7. 정리 대상

우선순위:
- **P1**: 규칙 위반이 화면에 보이거나, 같은 UI가 서로 다르게 보이는 곳 → 리팩터링.
- **P2**: features·pages에서 따로 만든 같은 UI → `shared/ui`(또는 `app/layout`)로 올릴 후보.
- **P3**: 규칙에 없지만 의도한 값 → 리팩터링 대신 `frontend/CLAUDE.md` 예외로 넣을지 확인.
- **사용자 확인**: 우선순위와 따로, 기능·동작이나 보이는 모양이 바뀌는 항목에 붙인다.

grep 결과 요약(`/dev` 제외): 규칙 밖 `text-[..]` 0건, 앱 코드 `#hex` 0건(`mocks/` 15건은 목 이미지 색이라 대상 아님), 기본 팔레트 `gray-` 2건, 반투명 `black`·`white` 3건, 규칙 밖 `rounded-*` 4건, 그림자 4건, 임의 px 5건.

### 7.1 토큰 밖 값

| # | 우선 | 파일 | 현재 값 | 기준 값 | 제안 |
| --- | --- | --- | --- | --- | --- |
| 1 | P1 | `pages/ComingSoonPage.jsx` | `text-sm text-gray-500` | `text-caption text-ink-muted` | 리팩터링 |
| 2 | P1 | `pages/NotFoundPage.jsx` | 링크 `text-sm text-gray-700 underline` | `ButtonLink variant="secondary"` 또는 `text-ink-muted` | 리팩터링 (버튼으로 바꾸면 사용자 확인) |
| 3 | P3 | `shared/ui/Modal.jsx`, `BottomSheet.jsx` | `rounded-2xl`(16), `rounded-t-2xl` | 규칙에 없음 | 유지하고 규칙에 "모달·시트 16" 추가 — 사용자 확인 |
| 4 | P3 | `shared/ui/Modal.jsx`, `BottomSheet.jsx` | 배경막 `backdrop:bg-black/50` | 토큰 없음 | 유지하고 규칙에 예외 추가, 또는 `ink/50` — 사용자 확인 |
| 5 | P3 | `shared/ui/Skeleton.jsx` | 기본 `rounded-md`(6) | 규칙에 없음 | 기본을 `rounded-lg`로 — 로딩 모양이 바뀌므로 사용자 확인 |
| 6 | P3 | `pages/ItemDetailPage.jsx` | 스켈레톤 `rounded-none` | — | 이미지 꽉 찬 자리라 유지 |
| 7 | P3 | `pages/ItemDetailPage.jsx` | 뒤로 가기 `bg-white/90` | 시안 `rgba(255,255,255,0.92)` | 유지(시안 값과 거의 같음), 규칙에 예외 추가 |
| 8 | P3 | `ItemImageCarousel.jsx`, `ImageUploader.jsx` | 이미지 위 `bg-ink/60` | 시안 ink 72% | 유지, 규칙에 "이미지 위 반투명 ink" 예외 추가 |
| 9 | P3 | `FloatingActionButton.jsx` | `shadow-[0_6px_16px_rgba(28,28,26,0.18)]` | 시안 없음 | `@theme`에 그림자 토큰(`--shadow-float` 등)을 둘지 사용자 확인 |
| 10 | P3 | `Modal.jsx`, `BottomSheet.jsx`, `Toaster.jsx` | `shadow-xl`, `shadow-lg` | 시안 없음 | 9와 함께 정리 |
| 11 | P3 | `features/rental/components/RentalStepBar.jsx` | `border-[3px]` ×2 | 시안 3px | 유지(시안 값) |
| 12 | P3 | `BackButton.jsx`, `NavIcon.jsx` | 아이콘 `size-[22px]` | 시안 아이콘 22 | 유지(시안 값) |
| 13 | — | `shared/ui/buttonClassName.js` | `px-[22px]` | 시안 22 | 유지(시안 값) |

### 7.2 중복 UI (shared로 올릴 후보)

| # | 우선 | 파일 | 현재 | 기준 | 제안 |
| --- | --- | --- | --- | --- | --- |
| 14 | P1·P2 | `features/rental/components/FormParts.jsx` `FormError`, `features/auth/components/AuthFormParts.jsx` `FormRootError`, `features/item/components/ItemForm.jsx:58`, `features/item/components/TradePlaceField.jsx:28`, `features/item/components/AvailablePeriodsField.jsx:89`, `features/region/components/RegionFinder.jsx:171` | 똑같은 `role="alert"` 오류 문구를 6곳에서 만듦. 크기가 `text-caption` 3곳 / `text-xs` 3곳으로 섞임 | `TextField` 오류와 같은 `text-xs text-danger`(필드 오류), 폼 전체 오류는 하나로 | `shared/ui/FormError`로 합치고 크기를 하나로 — 글자 크기가 바뀌므로 사용자 확인 |
| 15 | P2 | `AmountSummary.jsx`, `InspectionRecords.jsx:127`, `RentalDetailPage.jsx:50`, `RegionFinder.jsx:10`, `ItemFormPage.jsx:22`, `RentalReservePage.jsx:199·245`, `ItemInfo.jsx:88` | 안내 상자 8곳. 안쪽 여백 `p-3.5`·`px-3.5 py-3`·`px-4 py-3`·`p-4`·`px-4 py-5`, 테두리 유무가 섞임 | 시안 `12px 14px`, `rounded-xl` | `shared/ui/Notice`(tone surface·accent·primary)로 합치고 여백을 `px-3.5 py-3`로 — 여백이 바뀌므로 사용자 확인 |
| 16 | P2 | `pages/MyPage.jsx:16`, `pages/MyProfilePage.jsx:2`, `pages/ComingSoonPage.jsx:4`, `pages/NotFoundPage.jsx:6` | 제목 `h1 text-lg font-bold`를 직접 만듦(높이·여백 제각각) | `PageHeader` | 탭 첫 화면(MyPage)은 뒤로 가기 없는 제목 헤더가 필요 → `PageHeader`에 `back={false}` 옵션을 두고 통일 — 사용자 확인 |
| 17 | P2 | `features/auth/components/ProfileSummary.jsx:54`, `features/region/components/RegionFinder.jsx:45` | 한 줄 "불러오지 못했어요 + 다시 시도" 알림을 각각 만듦 | `ErrorState`(큰 모양)만 있음 | `ErrorState`에 `inline` 모양을 두거나 `shared/ui/InlineError`로 합침 |
| 18 | P2 | `pages/RentalReservePage.jsx:50` | 물품 사진을 `div + img`로 직접 그림(이미지 없을 때 아이콘 없음) | `Thumbnail` | `Thumbnail className="size-13"`로 교체 — 이미지 없을 때 아이콘이 생기므로 사용자 확인 |
| 19 | P2 | `ItemImageCarousel.jsx:36`, `HomePage.jsx:68`, `Modal.jsx`·`BottomSheet.jsx` 닫기, `BackButton.jsx` | 44 원형 아이콘 버튼 클래스(`flex size-11 items-center justify-center rounded-full … focus-visible`)를 곳곳에서 반복 | — | `shared/ui/IconButton`(aria-label 필수)으로 합침 |
| 20 | P2 | `pages/HomePage.jsx:51` | 홈 헤더를 `PageHeader`와 같은 틀(sticky·56·divider)로 직접 만듦 | `PageHeader` | 16과 함께 `PageHeader`에 왼쪽 슬롯을 두면 합칠 수 있음. 홈 모양이 달라 유지도 가능 — 사용자 확인 |
| 30 | P2 | `features/item/components/ItemListView.jsx:10`, `pages/MyRentalsPage.jsx:30`, `pages/MyItemsPage.jsx:90` | 목록 스켈레톤 줄(`flex gap-3.5 border-b border-divider py-3.5` + Skeleton)을 화면마다 직접 만듦 | — | `shared/ui/ListSkeleton`(줄 수·사진 크기 props)으로 합침 |

### 7.3 시안과 다른 크기·간격·의미

| # | 우선 | 파일 | 현재 값 | 시안 값 | 제안 |
| --- | --- | --- | --- | --- | --- |
| 21 | P1 | `features/item/components/ItemCard.jsx:30`, `pages/ItemDetailPage.jsx:105` | 가격 단위 `/일` `text-body font-normal`(15/400) | 13/500 muted | `text-caption font-medium`로 — 사용자 확인(보이는 크기 변경) |
| 22 | P3 | `features/item/components/ItemCard.jsx:15` | 글 사이 `gap-0.5`(2px) | 3px | 유지(차이 1px) |
| 23 | P1 | `features/item/components/ItemImageCarousel.jsx:130` | 번호 배지 `text-caption font-semibold px-3 py-1 bg-ink/60` | 12px, `3px 9px`, ink 72% | `text-xs px-2.5 py-0.5`로 시안에 맞춤 — 사용자 확인 |
| 24 | P3 | `ItemDetailPage.jsx:51`, `RentalReservePage.jsx:219`, `ItemInfo.jsx:77` | 섹션 제목 h2에 `text-price` | 16/700 (값은 같음) | 값은 같으므로 유지. 의미가 섞이니 `@theme`에 `--text-section`(16/700)을 둘지 사용자 확인 |
| 25 | P3 | `pages/HomePage.jsx:55` | 동네 이름 `text-title`(22) | 19/700 | 토큰 유지(19는 토큰 밖) |
| 26 | P3 | `features/item/components/ItemInfo.jsx:42` | 물품명 `text-title`(22) | 21/700 | 토큰 유지 |
| 27 | P3 | `pages/RentalReservePage.jsx:50` | 썸네일 52 `rounded-[10px]` | 52, 8 | 썸네일 규칙(10) 유지. 18과 함께 정리 |
| 28 | P3 | `features/rental/components/RentalActionBar.jsx` | 버튼 48 | 50 | 디자인 시스템(48) 유지 |
| 29 | P3 | `shared/ui/Calendar.jsx` | 날짜 칸 높이 44 | 약 34 | 터치 영역 규칙(44) 유지 |
