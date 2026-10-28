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
## shared/ui
- **주 색상 teal, 위험 색상 red를 `@theme` 토큰(`primary-*`·`danger-*`)으로.** teal은 대여 상태 Badge 색(blue·green·amber·red)과 겹치지 않아 구분이 쉬움(사용자 결정). 주 색상을 바꿀 때 토큰만 고치면 되도록 컴포넌트는 토큰 클래스만 쓴다.
- **Modal·BottomSheet는 네이티브 `<dialog>` + `useDialog` 훅 하나로.** 포커스 가두기·Esc·top layer를 브라우저에 맡겨 직접 구현할 코드를 줄임. Esc(`cancel`)는 막고 부모 상태로 닫아 open prop이 항상 기준. StrictMode에서 effect가 닫고 바로 다시 여는 경우의 `close` 이벤트는 플래그로 무시.
- **스크롤 잠금은 모듈 카운터.** 모달 위에 확인 모달이 겹쳐도 마지막이 닫힐 때만 해제.
- **Toast: 3초 표시, 최대 3개(넘치면 오래된 것부터 버림), 하단에서 6rem + safe-area 위.** 하단 탭·하단 고정 바(4.5rem) 모두 위에 뜨도록 위치를 하나로 고정해 라우트별 계산을 없앰. 열린 모달(top layer) 아래에 그려지므로 모달 안의 오류는 모달 안에 표시.
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
