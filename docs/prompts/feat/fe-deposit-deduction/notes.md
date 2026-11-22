# feat/fe-deposit-deduction 결정 기록 (REQ-049, REQ-018 미반납 신고, REQ-025·023 표시 프론트)

## 화면 형태와 버튼 표
- **새 화면 없이 SCR-08 안의 시트·모달로 넣었다(사용자 승인).** 차감 제안은 입력이 많아 바텀시트(`DeductionProposeSheet`), 수락·거절과 미반납 신고는 확인 모달(`DeductionRespondModal`·`LostReportModal`)이다. 제안 내용(사유·사진)은 모달이 아니라 상세의 보증금 카드에 둬서 결정 전에 볼 수 있게 했다.
- **미반납 신고 버튼은 오늘 > 반납 예정일 + 7일이 될 때까지 숨긴다(사용자 결정).** 비활성 + 사유로 보이는 안도 있었지만 연체 직후부터 보이면 신고를 재촉하는 것처럼 읽힌다.
- **RETURNED이고 depositStatus가 HELD면 상태 확인 분기보다 먼저 차감 협의 버튼을 정한다.** 미반납 신고 건은 RETURN 기록이 판정 전(result null)이라 예전 표대로면 "이상 있음·정상 확인"이 떠서 409가 났다. 신고 건에 정상 반환 경로는 만들지 않았다(open-issues #41 현재 결정).
- **거절된 제안 뒤 제공자 버튼 라벨은 "다시 제안"이다.** 같은 동작이지만 지금 상태를 알 수 있게 했다.
- **기한이 지난 PROPOSED는 양쪽 모두 버튼 없이 "자동 수락을 처리하고 있어요"만 보인다.** 응답하면 409 `DEDUCTION_PROPOSAL_CLOSED`라서다(#43). 기한 판단에 쓰는 지금 시각은 페이지의 `useNow`(30초)로 받아 버튼 표와 카드가 같은 값을 쓴다(렌더 중 `Date.now()`는 React Compiler 린트가 막는다).

## 보증금 카드
- **`getDepositNotice` 추정을 지우고 상세 응답 필드만 보는 `getDepositView`(deposit.js)로 바꿨다.** depositStatus null(결제 없음)·NONE(보증금 0원)이면 카드가 없다. 금액 줄은 결제 내역 카드의 `AmountRows`를 그대로 써 두 화면 모양을 맞췄다.
- **반환액은 보증금 − 연체료 − 차감액으로 계산해 보인다.** `refundedAmount`는 취소 환불까지 더한 누적값이라 "누적 환불액" 줄로 따로 둔다.
- **관리자 확정 안내는 depositHeldAt + `deposit.hold-agreement-days`가 지나면 붙인다.** 관리자 확정 화면은 3순위라 안내만 한다.

## 미반납 신고 건 판단 (open-issues #57)
- **"HELD이고 RETURN 기록의 result가 null"이면 신고 건으로 본다(사용자 승인, `isLostReportHold` 한 곳).** 상세·inspections 응답에 신고 여부 필드가 없다. 이상 판정은 항상 result ABNORMAL을 남기므로 구분된다. 백엔드에 `lostReported` 필드 추가를 #57로 제안했다. 필드가 생기면 이 함수만 바꾼다.
- **policies.md §2 "돌려주면 일반 반납 확인" 문장은 건드리지 않고 #58로 기록만 했다(사용자 결정).** rental.md·#41과 어긋나지만 #41과 함께 정할 일이다.

## 차감 제안 시트
- **정액(FIXED)·전액(FULL) 등급은 상한을 금액 칸에 미리 채운다.** 사용자가 금액을 고쳤으면(dirty) 등급을 바꿔도 덮어쓰지 않는다. 실비(ACTUAL)는 직접 넣는다.
- **상한을 못 불러오면 상한 검사는 서버에 맡기고 제출은 막지 않는다.** 400 `DEDUCTION_EXCEEDS_CAP`의 details maxAmount를 금액 칸에 보이고 그 등급 상한을 다시 불러온다.
- **사진 칸은 손상 사진·견적서·영수증 두 개이고, 검사는 `validateDeductionImages` 한 곳이다.** 기존 `InspectionImageField`에 `validate`를 받게 해 0장 허용(#38)과 영수증 필수(receiptRequired, #40)를 넣었다.
- **multipart는 `toMultipart`를 쓰지 않고 `actOnDeposit`에서 직접 만든다.** 사진 파트 이름이 `images`가 아니라 `evidenceImages`·`receiptImages`다.

## 캐시 무효화
- **결제·정산 내역 무효화는 `applyRentalDetail`의 `hasPaymentChanged` 하나로 넣었다.** 대상: 예약 취소, 취소 동의 수락, 반납 요청(연체료 확정), 상태 확인, 미반납 신고, 차감 제안·수락·거절. 반납 요청은 계획에 없었지만 연체료가 이때 확정돼 결제 내역 금액이 바뀌어서 넣었다.
- **`paymentHistoryKeys`를 `features/payment/keys.js`로 옮겼다.** payment/queries.js가 rental/queries.js를 가져오고 있어 반대로 가져오면 순환한다. queries.js는 다시 export해 기존 import는 그대로다.

## 목
- **보증금 상태는 결제 내역 목과 같은 계산(`paymentHistoryLogic.js` `rentalPaymentState`)을 쓴다.** 화면에서 생긴 보류·연체료·차감은 대여의 `depositExtra`에 쌓고 `paymentExtraOf`가 시드 보충 표와 합친다. 그래서 대여 상세와 결제·정산 내역 금액이 어긋나지 않는다.
- **연체료는 반납 요청·미반납 신고 때 확정해 `depositExtra.overdueFee`에 둔다.** 예전 목은 반납 때 연체료를 남기지 않았다.
- **시드 18·19(대여자)·35·36·37(제공자)를 더했다.** 35·37은 연체료가 보증금을 다 먹지 않게 대여 시점 보증금을 100,000원으로 바꿨다(스냅샷 흉내).
- **화면 확인은 하지 못했다.** 앱 내장 브라우저가 MSW 서비스 워커 등록을 거부했다. 대신 msw/node로 목 흐름(신고 → 보류 → D 제안, 수락 → 반환, 기한 지남 409, 상한 초과 details, 신고 건 상태 확인 409)을 한 번 돌려 확인했고, 그 임시 테스트는 커밋하지 않았다.

## 리뷰 반영
- **보류 7일 관리자 확정 안내는 응답을 기다리는 제안(OPEN·기한 지난 CLOSED)이 있으면 붙이지 않는다.** 그동안 관리자 확정은 409 `DEDUCTION_PROPOSAL_IN_PROGRESS`다(payment.md REQ-049 5번).
- **FORFEITED이고 deduction이 없으면 "연체료로 보증금이 모두 쓰였어요"로 나눴다.** 차감 확정 없이 전액 소진되는 경우는 연체료뿐이다(REQ-025).
- 넘김: 목 상수 분리 규칙 — `mocks/depositLogic.js`의 LOST_REPORT_AFTER_DAYS·72시간은 features 상수를 가져오지 않고 그대로 둔다(목은 서버 흉내라 화면 상수와 같이 틀리지 않게, 사용자 결정).
- 넘김: 보증금 카드 메모와 하단 바 안내가 겹치는 문제(리뷰 제안 2)는 고치지 않았다(사용자 결정).
