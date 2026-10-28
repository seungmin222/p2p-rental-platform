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
- 정책 수치(`RENTAL_PERIOD`·`ITEM_IMAGES`·`INSPECTION_IMAGES`)는 features 상수에 policies.md 키 주석을 달아 두고, shared/ui 컴포넌트는 기본값 없이 props로만 받는다.
