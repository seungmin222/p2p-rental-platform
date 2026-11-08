# feat/payment-settlement 결정 기록 (REQ-039, REQ-033)

## 정산 생성
- **대여 완료·수수료 취소와 같은 트랜잭션에서 `RentalSettlementPort`로 만든다.** backend/CLAUDE.md는 정산 생성을 AFTER_COMMIT 이벤트로 적었지만, 커밋 뒤 리스너가 실패하거나 서버가 죽으면 정산 없는 COMPLETED가 남고 이를 메우는 복구 배치가 따로 필요하다. 같은 트랜잭션이면 둘이 함께 커밋되거나 함께 롤백돼 복구 배치가 필요 없다. `RentalRefundPort`와 같은 포트 방식이라 rental이 payment를 직접 알지 않는다. backend/CLAUDE.md 문장을 고쳤다.
- **RETURNED → COMPLETED는 `RentalCompletionRecorder.complete` 하나로만 지난다.** 완료 경로가 5곳(정상·이상 판정, 미반납 신고, 보증금 반환 완료, 차감 확정)에 흩어져 있어 한곳에 모아야 정산이 빠지지 않는다. 부르는 곳이 없어진 `Rental.confirmNormal`은 지웠다.
- **수수료 취소 정산은 취소 전이 직후, 환불 TX1보다 먼저 만든다.** 결제가 아직 APPROVED일 때 "받은 돈이 있다"를 확인하기 위해서다. 수수료는 대여자 환불 결과와 상관없이 제공자 몫이라 환불이 REFUND_FAILED로 남아도 정산은 그대로 둔다.
- **받은 돈이 없으면 정산을 만들지 않는다(D3).** 승인된(APPROVED·PARTIAL_CANCELED) RENTAL 결제가 없거나(결제 기능을 끄고 만든 건) 지급액이 0원이면(대여료 0원·차감 없음) 건너뛴다. 0원 원장은 지급할 것이 없어 목록만 어지럽힌다.
- **금액 출처**: 대여료 = `payments.rental_fee_amount`(실제 받은 금액), 연체료 = `rentals.overdue_fee`(보증금 한도), 손상 차감 = `payments.deduction_amount`(제안 금액만 복사돼 연체료가 섞이지 않는다 — `DepositDeductionTxService.settle`), 연장료 = 승인된 EXTENSION 결제 합계(038 전에는 0, 038은 결제만 만들면 된다).
- **지급 예정일 기준은 `rentals.completed_at` / `canceled_at`의 날짜(Asia/Seoul)**다.
- **`settlements`에 `paid_at`·`created_at`·`updated_at`과 인덱스 두 개를 더했다(D2).** erd.md에 없던 컬럼이다. 지급 완료 시각을 목록에 보여 주기 위해, 인덱스는 지급 배치(status, scheduled_date)와 내 정산 목록(owner_id, id)용이다.
- **HELD 전이 메서드는 만들지 않았다.** 트리거인 문의(063)가 3순위다. 지급 배치가 SCHEDULED만 고르므로 HELD가 생겨도 지급되지 않는다.

## 정산 지급 배치
- **실행 시각은 application.yml cron `app.batch.settlement-payout-cron` = 매일 00:30이다.** policies.md 배치 주기 표는 "매일"만 두었다(D4). 지급 예정일 당일 아침 전에 지급되도록 자정 직후로 했고, 연체 감지(00:05)와 겹치지 않게 30분으로 정했다.
- **건마다 행 락을 잡고 `isPayable`을 다시 본다.** 같은 회차를 두 서버가 돌리는 것은 ShedLock이 막고, 재실행·겹친 실행은 이 확인으로 한 번만 지급된다.

## 033 결제·보증금 내역
- type 의미는 open-issues #44의 임시 해석으로 구현했다(확정 전). REFUND = 대여 CANCELED이고 환불했거나 환불이 남은 결제, DEPOSIT = deposit_status가 HELD·REFUNDED·FORFEITED·REFUND_FAILED. 취소 환불도 보증금을 REFUNDED로 돌려주므로 DEPOSIT에도 나온다.
- **from·to는 `payments.created_at`(주문 생성 시각)의 날짜에 건다.** 승인 시각 컬럼이 없고, 승인은 주문 뒤 15분(payment.expire-minutes) 안이라 날짜가 거의 같다. 응답 필드도 `createdAt`으로 이름을 정직하게 두었다.
- **from > to 검사는 기본값을 채운 뒤 서비스에서 한다.** from만 미래로 주면 to(오늘)보다 늦어지는 경우까지 같은 400(details field `to`)으로 막기 위해서다.
- **대여자 조건은 QueryDSL 서브쿼리로 rentals를 본다.** ItemListingRepositoryImpl과 같은 "조회 전용 관계 쿼리" 선례를 따랐다. 대여 Service를 거쳐 대여 ID 목록을 받으면 대여 수만큼 IN 목록이 커진다.
- **목록 화면의 물품·기간·연체료·취소 수수료는 `RentalSummaryReader`(rental 쪽)로 페이지 단위 일괄 조회**한다. 정산 목록도 같이 쓴다.
