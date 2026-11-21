# feat/fe-payment-settlement 결정 기록 (REQ-033, REQ-039 프론트)

## 요약
- 화면: SCR-12 `/me/payments` 한 화면 두 탭. 결제·보증금(`GET /api/members/me/payments`, 구분 칩·기간 시트)과 정산(`GET /api/members/me/settlements`, 합계 3칸·상태·지급 예정일)이다. `/me` 메뉴 "결제·정산 내역"은 결제가 켜져 있을 때만 보인다.
- 주요 파일: `pages/MyPaymentsPage.jsx`, `features/payment/paymentHistory.js`(#44 해석·표시 한 곳)·`constants.js`·`errorMessages.js`·`queries.js`(`usePaymentHistory`·`useSettlements`)·`components/`(PaymentHistoryCard·SettlementCard·SettlementSummary·HistoryRangeSheet·AmountRows), 목 `mocks/paymentHistory.js`·`paymentHistoryLogic.js`(+ 시드 대여 34).
- 남은 일: open-issues #44 확정 시 `paymentHistory.js`만 고친다. 대여 상세의 보증금 상태·연체료(`getDepositNotice` TODO)는 상세 응답에 필드가 생기면 따로 한다.

## 정산 탭과 라우트
- **`/me/payments` 한 화면에 탭 두 개(`?tab=payments|settlements`)로 두었다.** payment.md REQ-033에 "정산 내역은 같은 화면 별도 탭"이라고 이미 적혀 있다. 그래서 라우트 표는 바꾸지 않았다.
- **결제가 꺼져 있으면(`VITE_PAYMENT_ENABLED` false) `/me` 메뉴에서 빼고, 주소로 들어오면 API를 부르지 않고 안내만 보인다.** 결제를 끄고 만든 건에는 결제·정산이 생기지 않는다(payment.md D3). 그래서 메뉴를 두면 누를 때마다 빈 화면이 나온다.

## 구분(type)과 open-issues #44
- **"구분"은 필터 칩으로만 쓰고 항목에는 라벨을 붙이지 않는다(사용자 확인).** #44 임시 해석에서는 한 결제가 PAYMENT·REFUND·DEPOSIT에 함께 들어간다. 취소 환불은 보증금도 REFUNDED로 돌려준다. 그래서 항목마다 구분 하나를 정할 수 없다. 응답에도 항목별 구분 필드가 없다. 항목에는 결제 종류(대여료·보증금/연장료)·결제 상태·보증금 상태를 보인다. 이 내용을 open-issues #44에 덧붙였다.
- **해석에 닿는 코드는 `features/payment/paymentHistory.js` 한 곳**이다(`PAYMENT_HISTORY_TYPES` 칩 라벨·설명, `getPaymentHistoryView`). 해석이 바뀌면 이 파일과 라벨 표(`constants.js`)만 고친다.
- **결제 상태 표시명은 APPROVED "결제 완료" · PARTIAL_CANCELED "부분 환불" · CANCELED "환불 완료"(사용자 확인).** state-machines §4에는 한글 열이 없어서 표에 열을 더했다. 목록에 안 나오는 READY·FAILED도 같은 표에 "결제 대기"·"결제 실패"로 채웠다.

## 기간 필터
- **조건은 URL `?type=&from=&to=`에 두고 기본값은 넣지 않는다.** 대여 상세에 다녀와도 같은 조건이 남게 하기 위해서다. 틀린 값이나 from > to는 없는 것으로 읽어 400이 나지 않게 한다.
- **기본 기간(최근 3개월)의 날짜는 프론트가 계산하지 않는다.** 비우면 요청에서 빼고 서버 기본에 맡긴다. 화면은 "최근 3개월"이라는 문구만 보인다. 서버와 날짜 계산(월 말일 처리)이 갈라질 여지를 없애기 위해서다.
- **from > to는 기간 시트에서 막는다(`validateHistoryRange`).** to를 비우면 서버가 오늘로 채우므로 오늘과 비교한다. 서버와 같은 순서(기본값을 채운 뒤 비교)다. 입력은 `TextField type="date"`이고, 기존 `Calendar`는 쓰지 않았다. `Calendar`는 대여 기간용이라 최대 일수·예약 상태 표시가 붙어 있다. 그래도 400(details from·to)이 오면 "기간을 확인해 주세요" + 기본 기간으로 돌리는 버튼을 보인다.

## 정산
- **HELD·지급 예정일·지급 시각·heldTotal은 응답에 모두 있어서 다 보인다.** 합계는 페이지마다 같은 값이 와서 마지막으로 받은 페이지 것을 쓴다.
- **지급 시점 안내(영업일 2일)는 `SETTLEMENT_PAYOUT_DELAY_BUSINESS_DAYS`(policies `settlement.payout-delay`) 문구로만 쓴다.** 예정일은 서버 `scheduledDate`를 그대로 보인다.

## 목
- **결제·정산 내역 목은 대여 목 db에서 매번 계산한다(`mocks/paymentHistoryLogic.js`).** 따로 저장하면 예약·취소·연장 뒤 금액이 빌린 내역과 어긋난다. 응답에만 있는 값(연체료·차감·반환 실패·분쟁 보류)은 그 파일 맨 위 표로 보충했다.
- **정산 CANCEL_FEE 예시용 시드 대여 34를 더했다**(물품 104, 대여자 취소, 수수료 12,000원). 제공자 쪽 시드에는 수수료 있는 취소가 없었다.
- **목 주문에 `createdAt`을 붙였다.** 결제 내역 일시로 쓴다. 그 전에 저장한 주문은 대여 생성 시각으로 본다.
