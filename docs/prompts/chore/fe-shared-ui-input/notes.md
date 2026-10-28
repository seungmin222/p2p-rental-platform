# chore/fe-shared-ui-input 결정 기록

## 범위·의존성
- **새 의존성 없음.** 날짜는 기존 문자열 유틸, 아이콘은 인라인 SVG, react-hook-form은 붙일 수 있게만(TextField `ref`) 하고 설치는 화면 브랜치에서.
- **정책 수치는 props로만.** shared/ui에 기본값을 두지 않고, 미리보기·화면이 `RENTAL_PERIOD`·`ITEM_IMAGES`·`INSPECTION_IMAGES`를 넘긴다.
- **인계·반납 사진 형식을 jpg/png로 확정**(사용자 결정). policies.md `inspection.images`에 형식이 없어 `accept`로 넘길 값이 없었음. 물품 이미지와 같게 맞추고 `INSPECTION_IMAGES.ACCEPT` 추가. 서버 검증(`feat/rental-return`)도 같은 값으로.

## date.js
- **없는 날짜는 `Date.UTC` 왕복 비교로 거부**(2026-02-30, 2026-13-01, 평년 2/29). 정규식만으로는 월별 일수·윤년을 못 걸러서. `parseMonth`도 01~12만.
- **`countDaysInclusive`는 종료일 < 시작일이면 오류.** 0 이하를 돌려주면 "−2일"처럼 조용히 틀린 값이 화면까지 가므로. 순서는 호출부(Calendar 선택 로직)가 보장한다.
- **`formatDateRange`는 연도를 쓰지 않는다.** 최대 기간이 30일이라 연도를 넘어도(12/30~1/2) 월만으로 구분됨.

## Calendar
- **선택 규칙은 `calendarSelection.js` 순수 함수**(`selectDate`, `rangePosition`). jsdom 없이 node Vitest로 경계(1·30·31일, 사이 BOOKED, 달 넘김, days에 없는 날짜)를 검증하려고.
- **`days`는 여러 달을 합친 배열을 받는다.** 예약이 달을 넘을 수 있어(10/30~11/2) 화면에 보이는 한 달만으로는 범위 검사를 못 하므로. 호출부가 지나간 달의 응답을 캐시에서 합쳐 넘기고, days에 없는 날짜는 UNAVAILABLE로 본다(아직 안 불러온 달을 예약 가능으로 오판하지 않게).
- **검사 순서: 누른 날 상태 → TOO_SHORT → TOO_LONG → 사이(시작·끝 포함) 날짜.** 시작일도 다시 보는 것은 고른 뒤 refetch로 BOOKED가 됐을 수 있어서.
- **거부하면 기존 선택(시작일)을 유지하고 onChange를 부르지 않는다.** 사유는 Calendar 안의 `aria-live` 줄에 표시. 결과적으로 시작일만 있는 상태에서 뒤의 날짜를 누르면 항상 "종료일"로 해석되므로, 새 시작일을 고르려면 앞선 날짜를 누르거나 같은 날을 두 번 눌러야 한다 → 사용자 확인 필요(보고에 적음).
- **AVAILABLE이 아닌 날짜는 `disabled` 대신 `aria-disabled="true"`.** disabled면 클릭 이벤트가 안 와서 "예약된 날짜는 선택할 수 없어요"를 보여 줄 수 없고, 스크린 리더 탐색에서도 빠지므로.
- **앞뒤 달 칸은 빈칸.** 시안은 흐린 숫자지만 `buildMonthGrid`(null 칸)를 바꾸지 않으려고. 다른 달 날짜를 누를 수 있게 하면 규칙만 늘어남.
- **색만으로 구분하지 않는다**: 날짜마다 aria-label("10월 3일 토요일, 예약됨, 시작일"), 선택 칸은 `aria-pressed`, 범례 3개. 라벨은 `statusLabels` prop(호출부가 `RENTAL_DAY_STATUS`를 넘김) — shared가 features를 import하지 않게.
- **UNAVAILABLE 색은 새 토큰 `ink-disabled` #C9C7C0**(시안 지난 날짜 색, fe-shared-ui에서 예고). BOOKED는 `ink-faint` + 취소선(시안).
- **날짜 칸 높이 44px**(시안은 padding 8px로 약 37px). 터치 영역 규칙 때문. 1일 범위·시작일만 고른 상태는 44px 원, 범위 시작·끝은 칸 바깥쪽만 둥글게(시안). 날짜·월 글씨 14px는 `text-sm` 예외에 추가(frontend/CLAUDE.md).
- **월 이동 버튼은 44px**(시안 32px). `minMonth`·`maxMonth`를 넘으면 진짜 `disabled`(눌러도 보여 줄 사유가 없으므로).
- **로딩 중에는 날짜 칸을 원형 자리로 바꾸고 버튼을 렌더하지 않는다.** Skeleton은 `rounded-md`가 고정이라 원을 직접 그림.
- **연장(REQ-038)**: min/max가 props라 회당 7일로 재사용 가능. 다만 안내 문구가 "대여" 기준이라 연장 화면에서 문구가 맞는지 그때 확인.

## ImageUploader
- **value 모양은 `{ id, url }`(기존) / `{ id: 'local-n', file }`(새).** 물품 수정(유지할 기존 id + 새 파일 + 순서)을 배열 하나로 표현. 실제 요청 필드명은 API가 나오면 화면에서 변환한다.
- **로컬 id는 `local-` 접두사 + 모듈 카운터.** 서버 id(숫자)와 겹치지 않고, key를 index가 아니라 id로 써서 순서를 바꿔도 썸네일이 다시 만들어지지 않게. `crypto.randomUUID`는 비보안 컨텍스트(폰에서 LAN IP로 개발 서버 접속 등)에 없고, 짧은 순번이 테스트·디버깅에 읽기 쉬워서 쓰지 않음.
- **미리보기 objectURL은 value에 넣지 않는다.** 썸네일이 `file`로 effect에서 만들고 cleanup에서 해제. value에 넣으면 삭제·부모의 value 교체·언마운트마다 해제를 따로 챙겨야 하고 StrictMode 이중 실행에서 샌다. src는 상태 대신 DOM에 직접 넣어 리렌더를 줄임. 헤드리스 Chrome에서 추가·삭제·바텀시트 닫기(언마운트) 후 살아 있는 URL 수가 새 이미지 수와 같은 것을 확인.
- **부분 거부.** 형식·용량을 먼저 보고 통과한 파일만 남은 장수를 쓴다(큰 파일 하나 때문에 정상 파일이 장수 초과로 밀리지 않게). 사유는 사유당 한 줄로 컴포넌트 안 `aria-live`에 표시하고 `onError(rejected)`도 부른다. 인계·반납은 모달·바텀시트 안에서 쓰이는데 토스트는 top layer 아래라 가려지므로.
- **형식 판정은 `file.type`.** 확장자만 바꾼 파일은 서버가 다시 검사한다. HEIC는 type이 `image/heic`라 거부.
- **순서 변경은 앞으로·뒤로 버튼 + "대표로"(첫 장으로 이동), 첫 장에 "대표" 배지.** 모바일 브라우저가 HTML5 drag&drop을 지원하지 않고, 버튼은 키보드·스크린 리더로도 쓸 수 있어서. 드래그는 넣지 않음.
- **버튼을 누른 뒤 포커스를 옮겨 준다.** 이동하면 같은 사진의 같은 버튼(비활성이면 반대 방향), 삭제하면 이웃 사진의 삭제 버튼, 없으면 "사진 추가". 누른 버튼이 사라지거나 비활성이 돼 포커스가 body로 떨어지지 않게.
- **"사진 추가"는 첫 칸의 정사각형 타일(카운트 "3/10")**, 가득 차면 `disabled`. 썸네일 모서리 10px(fe-shared-ui "화면 브랜치에서" 값).
- **최소 장수(1장)는 막지 않는다.** 마지막 장도 지울 수 있어야 사진을 바꿀 수 있으므로. 제출 시 폼 검증이 맡는다.

## TextField
- **`ref`를 일반 prop으로 받는다**(React 19). react-hook-form `register`를 그대로 펼칠 수 있게. 라이브러리는 화면 브랜치에서 추가.
- **필수는 `aria-required` + 빨간 `*`(aria-hidden).** 네이티브 `required`는 브라우저 검증 말풍선이 떠서 JS 폼 검증과 겹침.
- **글자 수는 value가 있으면 그 길이, 없으면 입력 이벤트로 센다.** 비제어 입력을 코드(`reset` 등)로 바꾸면 갱신되지 않으므로 그때는 value를 넘긴다. 세는 단위는 브라우저 maxLength와 같은 UTF-16.
- **시안에 없어 정한 값**: 오류 문구 12px danger, 글자 수 12px `ink-subtle` 오른쪽 정렬, hint와 error는 둘 다 표시(오류 중에도 권장 범위 같은 안내가 필요해서), 포커스는 primary 테두리 + 1px 링, 비활성은 surface 면. 여러 줄은 `multiline`(textarea, 최소 96px, 세로 크기 조절).

## Chip
- **겉높이 34px(시안), 터치 영역은 `before`로 44px.** absolute 기준이 테두리 안쪽(32px)이라 위아래 6px씩 늘린다(처음엔 5px로 42px였던 것을 헤드리스 Chrome 판정으로 발견해 수정). 여러 줄로 감쌀 때는 줄 간격 10px(`gap-y-2.5`) 이상.
- **`aria-pressed`로 선택 표시.** 가로 스크롤 목록·단일/다중 선택 의미는 화면 브랜치에서.
