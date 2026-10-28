# chore/fe-shared-ui 결정 기록

## 범위·의존성
- **새 의존성 없음**(dayjs, 드래그·아이콘 라이브러리, jsdom, testing-library 포함). 날짜는 문자열 + UTC 계산으로 충분하고, 아이콘은 인라인 SVG로 대체. 의존성은 실제로 쓰는 화면 브랜치에서 판단.

## shared/lib
- **대여 기간은 "YYYY-MM-DD" 문자열로만, 계산은 `Date.UTC` 밀리초로.** `new Date('2026-10-01')`은 UTC 자정이라 브라우저 시간대에 따라 하루가 밀리므로. 오늘 날짜도 `todayInSeoul(now)`로 서울 기준.
- **서버 시각에 오프셋이 없으면 +09:00으로 해석.** 서버는 오프셋 없는 LocalDateTime(Asia/Seoul)을 보내므로(conventions.md §3) 브라우저 시간대와 무관하게 계산.
- **경과 시간 단위**: 1분 미만 "방금 전"(미래 시각 포함) → 분 → 시간 → 일(7일 미만) → 주(30일 미만) → 개월(30일 = 1개월, 365일 미만) → 년(365일). 달력 월 계산 대신 고정 일수로 단순하게 — 피드 표시용이라 정확한 달력 차이가 필요 없음.
- **보증금 0원은 "보증금 없음".** DepositStatus `NONE`의 한글명과 같은 표현(사용자 결정).

## 상수
- **`RENTAL_TABS` 키는 API tab 값(`UPCOMING`·`ONGOING`·`DONE`, rental.md REQ-019).** 화면 탭과 요청 파라미터를 같은 키로 쓰려고.
- **캘린더 `UNAVAILABLE` 라벨은 "예약 불가".** rental.md에 한글 라벨이 없어(설명만 "가능 기간 외 또는 지난 날짜") "예약 가능"과 짝을 맞춤(사용자 결정).
- **`REGION_RANGE` 라벨은 거리 그대로(1.5km·3km·6km).** state-machines §5에 거리만 있어 별도 이름을 만들지 않음.
- **RETURNED 라벨 "반납 완료" → "상태 확인 대기", COMPLETED tone gray → ink(검정 면), CANCELED는 gray 유지.** 디자인 시안을 따름(사용자 결정). state-machines.md의 한글명이 "반납 완료(상태 확인 대기)"라 명세와 어긋나지 않고, 대여자가 거래가 끝난 것으로 오해하지 않게. 시안에 없는 CANCELED는 결제 대기와 같은 회색.

## 디자인 시안 반영 (`docs/design/`)
- **색은 시안 디자인 시스템(Main.html) 토큰으로.** 처음에는 주 색상 teal을 정했으나(사용자 결정) 디자인 시안이 나와 #1F6F54(Primary)로 교체. 단계(50~700) 대신 역할 이름(`primary`·`primary-soft`·`primary-hover`·`accent`·`ink`·`ink-muted`·`surface`·`line`·`danger`).
- **시안 HTML에 반복되는 색도 토큰으로 추가**: `accent-strong` #8F560F(보증금 글씨 — 시안 실제 글씨는 Accent #C77A1A가 아니라 이 색), `divider` #F0EEE9(헤더 아래선·목록 구분선), `placeholder` #EDEBE5(이미지 자리), `ink-subtle` #8B8E88(요일·비활성 글씨), `ink-faint` #A3A39C(이미지 자리 아이콘), 배지용 `info`·`info-soft`·`danger-soft`·`accent-soft`. 캘린더 지난 날짜 #C9C7C0은 Calendar 브랜치에서.
- **글꼴 IBM Plex Sans KR을 `src/styles/fonts/`에서 직접 서빙**(외부 CDN 없음). CSS `url()`로 참조해 Vite가 해시를 붙여 오래 캐시 가능. 400·500·600·700 × 한글·라틴 서브셋을 unicode-range로 나눔. 한글 파일이 굵기당 약 0.5MB라 첫 방문 최대 약 2.1MB. `font-display: swap`(시안은 block) — 느린 망에서 글자가 안 보이는 시간을 없애려고. 라이선스 원문은 IBM/plex 저장소의 plex-sans-kr `LICENSE.txt`를 받아 `fonts/OFL.txt`로 함께 둠(docs 사본도 동일).
- **글자 크기는 `@theme --text-*` + `--text-*--font-weight`**로 `text-title` 하나에 크기·굵기를 같이. 행간은 시안에 거의 지정이 없어 기본 1.5 상속. 토큰 밖 크기는 12px `text-xs`, 18px(헤더 제목) `text-lg`, 11px(탭 라벨)은 직접 값.
- **Button은 시안 5가지에 맞춤**: `primary`·`outline`(새로)·`secondary`(흰 면+회색 테두리 → 회색 면)·`danger`(**채움 → 외곽선**)·비활성(opacity 대신 Line 면 + ink-subtle 글씨). 시안에 없는 `ghost`는 삭제. 크기는 시안 기본 48px을 `md`로, 44px을 `sm`으로.
- **하단 탭 64px·아이콘 22px·라벨 11px, 선택 탭은 채운 아이콘.** 시안엔 홈의 채운 모양만 있어 채팅·알림은 같은 외곽을 채우고, 검색은 채울 수 없어 선을 굵게(2.2), 나의 대여(시안의 목록 문서 아이콘)는 사각형을 채우고 줄을 흰색으로.
- **(+) 버튼은 시안대로 알약형 "+ 물품 등록"(52px, 탭 위 16px).** 글자가 보이므로 aria-label을 두지 않음(사용자 결정).

## 화면 브랜치에서 반영 (이 브랜치에서 구현하지 않음)
- 대여 상세 스텝바 단계 이름(시안 RentalDetail.html).
- 가격의 "/일"을 흐린 글씨(13/500, ink-muted)로 분리 표시 — `formatDailyPrice`는 문자열 하나를 반환하므로 화면에서 금액과 단위를 나눠 그린다.
- 보증금 글씨 색 `accent-strong`(12/600), 이미지 썸네일 모서리 10px, 상세 하단 바 가격 17/700.
- 카테고리 칩: 홈·검색 필터에서 반복되므로 공통 `Chip`(선택 Ink 면 / 비선택 Line 테두리, 34px) 제안. 시안 높이 34px가 터치 영역 44px 규칙보다 작아 겉모양은 34px, 터치 영역은 `before`로 확장해야 함. 만든다면 `chore/fe-shared-ui-input`에서.

## shared/ui
- **Modal·BottomSheet는 네이티브 `<dialog>` + `useDialog` 훅 하나로.** 포커스 가두기·Esc·top layer를 브라우저에 맡겨 직접 구현할 코드를 줄임. Esc(`cancel`)는 막고 부모 상태로 닫아 open prop이 항상 기준. StrictMode에서 effect가 닫고 바로 다시 여는 경우의 `close` 이벤트는 플래그로 무시.
- **스크롤 잠금은 모듈 카운터.** 모달 위에 확인 모달이 겹쳐도 마지막이 닫힐 때만 해제.
- **Toast: 3초 표시, 최대 3개(넘치면 오래된 것부터 버림), 하단에서 6rem + safe-area 위.** 하단 탭(64px)·하단 고정 바(76px) 모두 위에 뜨도록(시안 반영 후에도 그대로. 홈의 "+ 물품 등록" 버튼과는 잠깐 겹칠 수 있음) 위치를 하나로 고정해 라우트별 계산을 없앰. 열린 모달(top layer) 아래에 그려지므로 모달 안의 오류는 모달 안에 표시.
- **PR 분리**: 변경 파일이 30개를 넘어 Calendar·ImageUploader는 다음 브랜치 `chore/fe-shared-ui-input`으로 미룸(사용자 결정). 그 브랜치에서 할 일: 선택 규칙·이미지 목록 조작을 순수 함수로 분리해 테스트, 이미지 순서 변경은 모바일에서 HTML5 drag&drop이 안 되므로 "앞으로/뒤로" 버튼 + 첫 장 "대표" 표시(드래그는 `feat/fe-item-pages`에서 결정).
- **jsdom·testing-library 미도입.** 컴포넌트 렌더 테스트 대신 로직(토스트 스토어·포맷·날짜)을 순수 함수로 두고 node 환경 Vitest로 검증. 화면 동작은 `/dev/ui`에서 수동 확인.

## 레이아웃
- **하단 탭 숨김은 라우트 `handle.hideTabBar` + `useMatches`.** 숨김 여부를 화면 컴포넌트가 아니라 라우트 표 한 곳에서 보게 하려고. 대상: 물품 상세·등록·수정, 예약, 대여 상세.
- **BottomActionBar가 자기 높이만큼 빈 자리를 본문 끝에 둔다.** AppLayout이 화면마다 바 유무를 알 필요가 없게.
- **PageHeader 뒤로 가기는 `location.key === 'default'`(직접 진입)이면 홈으로 replace.** 공유 링크로 들어온 사용자가 앱 밖으로 나가지 않게.
- **하단 탭 아이콘은 인라인 SVG.** 아이콘 5개라 라이브러리를 추가할 만큼이 아님.

## 미리보기 페이지
- **`/dev/ui`는 `import.meta.env.DEV`일 때만 라우트 등록 + lazy import.** 빌드에서 조건이 false로 치환돼 import가 빠짐(`npm run build` 후 dist에 없음 확인). 위치는 SCR와 1:1인 `pages/`가 아니라 `src/dev/`.

## 정책 수치
- 정책 수치(`RENTAL_PERIOD`·`ITEM_IMAGES`·`INSPECTION_IMAGES`)는 features 상수에 policies.md 키 주석을 달아 두고, shared/ui 컴포넌트는 기본값 없이 props로만 받는다.
