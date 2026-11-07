# feat/payment-deposit 결정 기록 (REQ-049, REQ-018 미반납 신고)

## 요약
- API: `PATCH /api/rentals/{id}/deposit`(PROPOSE·ACCEPT·REJECT, multipart), `GET /api/rentals/{id}/deposit/policy?grade=`, `POST /api/rentals/{id}/lost-report`, `GET /api/admin/deposits/held`, `PATCH /api/admin/rentals/{id}/deposit`. `GET /api/rentals/{id}` 응답에 `depositStatus`·`refundedAmount`·`depositRefundPending`·`depositHeldAt`·`deduction`·`latestDeductionProposal` 추가. 배치: 차감 제안 자동 수락(매시 15분).
- 주요 파일: `payment/service/DepositDeductionService`·`DepositDeductionTxService`·`DeductionLimit`·`DepositQueryService`, `payment/entity/DeductionProposal`, `payment/batch/DeductionAutoAcceptJob`, `payment/controller/DepositController`·`AdminDepositController`, `rental/service/RentalDepositQueryPort`, `rental/dto/RentalDepositInfo`·`RentalDepositView`, `V10__create_deduction_proposals.sql`.
- prod `payment-enabled`: "049 머지 전까지 끈다" 조건은 이 브랜치로 풀린다. 남은 조건은 open-issues #33(같은 멱등성 키 재시도에 토스가 실패 응답을 재생하는지)을 실제 토스로 확인하는 것이고, 결제·보증금 알림이 아직 로그 구현(REQ-032 전)이라는 점도 켜기 전에 알고 있어야 한다.
- 남은 일(REQ-039): 정산 생성 자리는 `DepositDeductionTxService.settle`의 반환액 0원 분기 주석과 `PaymentRefundTxService.applyResult`(반환 완료)다. 채팅 SYSTEM 메시지는 채팅 구현 후.
- 미결: open-issues #41(미반납 신고 뒤 돌아온 물건의 정상 반환 경로 없음). 프론트는 수락·거절에 `latestDeductionProposal.proposalId`를 보내야 한다.

## 범위
- **관리자 확정 API를 이 PR에 같이 둔다.** 대여자가 계속 거절할 때 HELD를 끝내는 유일한 길이라 떼면 보류가 끝나지 않는다. 변경 파일이 30개를 넘는 이유로 PR 설명에 적는다.
- **"보류 7일 초과 표시" 배치는 만들지 않았다.** 바꿀 상태가 없어 목록 API가 `held_at`으로 조회한다.

## 상태 확인 '이상'·미반납 신고
- **`Rental.markAbnormal()`은 RETURNED 확인만 하는 그대로 둔다.** 보증금 상태는 `Payment`에 있어 엔티티가 바꿀 수 없다. 서비스가 `RentalRefundPort.holdDeposit`(FROZEN → HELD, `held_at`)을 부르고 RETURNED → RETURNED 이력을 남긴다. rental-return notes의 "markAbnormal이 HELD로 바뀐다"는 이 방식으로 실현했다(그 파일은 고치지 않는다).
- **`hasFrozenDeposit`을 `holdDeposit`으로 바꿨다.** 확인과 전이가 같은 결제 행 락 안에서 일어나야 해서 한 메서드로 묶었다.
- **미반납 신고 건은 판정하지 않는다.** `prepareDepositReturn`은 FROZEN만 받는다(state-machines §2에서 HELD → REFUNDED는 차감 확정뿐). 물건이 돌아와도 D 등급·0원 이상 제안으로 끝낸다. 정상 반환 경로는 open-issues #41로 남겼다.
- **동결 보증금이 없는 미반납 신고는 바로 COMPLETED다**(open-issues #39). 이력은 두 건, 둘 다 reason `LOST_REPORT`.
- **미반납 신고 메모는 선택, 길이는 `return.note-max`.** item_inspections.note 칸을 반납 메모와 같이 쓴다.

## 상한·증빙
- **상한은 대여 시점이 아니라 현재 물품 카테고리로 계산한다.** 대여에 카테고리 스냅샷이 없다(`ItemQueryService.getCategoryId`를 새로 만들었다).
- **수락·자동 수락 때 상한을 다시 검사하지 않는다.** 제안 뒤 카테고리가 바뀌어도 합의된 금액을 깎지 않는다. 남은 보증금 초과만 불변식으로 확인한다.
- **영수증 필수 여부는 damage_policies.deduction_type `ACTUAL`로 정한다.** C 전부와 TOOL의 B(소모품 실비)가 해당한다.
- **증빙은 손상 사진(`evidenceImages`)과 영수증(`receiptImages`)을 따로 받고 `receipt_urls` 컬럼을 추가했다.** 한 목록이면 영수증이 있는지 검사할 수 없다. 장수·용량은 `inspection.images.*`(open-issues #40). 필수인데 0장이면 `DEDUCTION_EVIDENCE_REQUIRED`, 장수 범위 위반은 `IMAGE_COUNT_INVALID`.
- **업로드·정리 코드는 `RentalInspectionService`와 같은 패턴을 payment에 복제했다.** global로 빼면 공통 코드 리뷰가 필요해 이번에는 넓히지 않았다.

## 협의·확정
- **수락·거절은 `proposalId`를 받는다.** 화면을 연 사이에 새 제안으로 바뀌었을 때 모르는 제안을 수락하지 않게 한다. 다른 대여의 ID나 없는 ID는 404.
- **응답 기한이 지나면 배치 전이어도 수락·거절 모두 409**(open-issues #43). 자동 수락 대상은 기한 정각부터.
- **관리자 확정은 PROPOSED 제안이 있으면 409다.** 기한이 지나 배치를 기다리는 제안도 포함한다(한 시간 안에 자동 수락된다).
- **수락·자동 수락·관리자 확정은 `DepositDeductionTxService.settle` 하나를 탄다.** 반환액 = 결제의 보증금 − 연체료 − 차감액. 0보다 크면 기존 `prepare(DEPOSIT)`로 부분 취소하고 환불 결과 반영(`applyResult`)이 COMPLETED로 바꾼다. 실패하면 재시도 배치가 같은 키로 끝낸다(완료 이력 reason은 `REFUND_RETRY_BATCH`).
- **완료 이력 reason**: 수락 `DEDUCTION_ACCEPTED`(changedBy 대여자), 자동 수락 `DEDUCTION_AUTO_ACCEPT_BATCH`(null), 관리자 `DEDUCTION_ADMIN_DECIDED`(관리자).
- **동시성**: 모든 쓰기가 `RentalPaymentTxService.lockForDeposit`(대여 행 락)으로 시작하고 결제 행을 잠근 뒤 제안 행을 읽는다. 락 전에 일반 SELECT를 하지 않는다(payment-core와 같음). `DepositDeductionConcurrencyTest`가 수락 vs 자동 수락, 수락 vs 관리자 확정에서 PG 1회·이력 1건을 확인한다.

## 알림·응답
- **결제 쪽도 `RentalNotificationPort`를 쓴다.** payment → rental 방향이라 순환이 없고, REQ-032가 구현 하나만 바꾸면 된다. event_id: `LOST_REPORTED:{rentalId}`, `DEPOSIT_HELD:{rentalId}`, `DEDUCTION_*:{rentalId}:{proposalId}`. `DEDUCTION_ACCEPTED`는 양측(notification.md 표).
- **대여 상세의 보증금 정보는 `RentalDepositQueryPort`로 읽고 상태·등급을 코드 문자열로 담는다.** rental이 payment Enum을 import하지 않게 했다. Swagger에는 `allowableValues`로 값을 적었다. 상세 조회 쿼리는 결제 없을 때 6개, 결제가 있으면 제안 조회까지 7개다.
- **관리자 확정 응답은 대여 상세가 아니라 `RentalDepositInfo`다.** 관리자는 당사자가 아니다.
- **보류 목록은 대여 정보를 한 번에 읽는다**(`RentalPaymentTxService.getDepositViews`, 최신 제안도 한 쿼리).
