# feat/rental-extension 결정 기록 (REQ-038)

## 요약
- API: `POST /api/rentals/{id}/extensions`(연장 요청, 결제 대기 또는 바로 확정), `POST /api/payments`에 `extensionId`(연장료 주문), `/api/payments/confirm`은 결제 종류로 분기. 배치: 연장 결제 만료(1분). 연체 배치는 결제 대기 연장이 있으면 건너뛴다(open-issues #45 B+A).
- 주요 파일: `rental/entity/RentalExtension`·`ExtensionStatus`, `rental/service/RentalExtensionService`·`RentalExtensionTxService`·`RentalExtensionRecorder`·`ExtensionPeriodCalculator`, `RentalOccupancyReader`(결제 대기 연장 점유), `payment/service/ExtensionPaymentTxService`, `payment/batch/ExtensionPaymentExpireJob`, `V12__create_rental_extensions.sql`.
- 정해진 규칙: 락 순서 대여 → 연장 → 결제, 연장 환불 키 `extension-refund:{paymentId}`, 연장 결제는 보증금 상태 null, 정산 연장료는 실제로 받은 금액, 대기 시간은 `payment.expire-minutes` 재사용.
- 남은 일: 007 가능 기간 수정은 점유를 `RentalOccupancyReader`로 읽어야 결제 대기 연장 기간이 반영된다(B). 프론트가 연장 결제 화면을 떠나면 extensionId를 다시 얻을 방법이 없다(fe 연장 브랜치에서 상세 응답에 넣을지 결정). `RENTAL_EXTENDED` 알림은 REQ-032 전이라 로그 구현.

## 점유·겹침
- **결제 대기 연장 기간은 `RentalOccupancyReader` 한곳에서 합친다.** 예약 겹침(013)과 캘린더 BOOKED(012)가 이미 이 클래스를 거치므로, 여기에 넣으면 두 곳이 같이 바뀌고 따로 고칠 곳이 없다. 연장 검사는 `excludeRentalId`로 자기 대여 행만 뺀다. 자기 대여의 결제 대기 연장은 빼지 않는다. 대여당 1건 제한이 먼저 막기 때문이다.
- **연장 구간은 가능 기간 하나 안에 있어야 한다.** 예약(013)과 같은 규칙이다. 이어진 다음 가능 기간으로는 넘어가지 않는다.
- **"연장 가능한 최대 날짜"는 `error.details`의 `{ field: "maxEndDate", reason: "yyyy-MM-dd" }`에 싣는다.** 선례가 `DEDUCTION_EXCEEDS_CAP`의 `maxAmount`라서 공통 응답 형식을 바꾸지 않고 따랐다. 값은 다음 셋 중 가장 이른 날이다(`ExtensionPeriodCalculator`). 하루도 연장할 수 없으면 details를 비운다.
  - 기존 종료일 + `max-days-per`
  - 기존 종료일 다음 날이 들어 있는 가능 기간의 끝
  - 다음 점유 시작일 − 1
- **(명세 외) 가능 기간 밖 400 `RENTAL_OUT_OF_AVAILABLE_PERIOD`에도 같은 maxEndDate를 싣는다.** 프론트가 같은 안내 문구를 재사용할 수 있게 하려는 것이고, 계산은 409와 한 번에 끝난다.

## 명세 외로 넣은 것
- **결제 대기 연장은 대여당 1건이다(409 `RENTAL_EXTENSION_IN_PROGRESS`).** 두 건이 동시에 대기하면 서로의 기간을 선점해 둘 다 막히거나, 둘 다 승인돼 종료일이 엇갈린다. 1건으로 제한하면 "확정된 연장 < 2" 검사만으로 횟수 한도가 맞는다.
- **새 에러 코드 5개.**
  - `RENTAL_EXTENSION_OVERDUE`(409): 반납 안내 문구가 필요해 RENTAL_INVALID_TRANSITION과 구분했다.
  - `RENTAL_EXTENSION_LIMIT_EXCEEDED`(400): 횟수·일수 한도
  - `RENTAL_EXTENSION_IN_PROGRESS`(409): 위의 1건 제한
  - `RENTAL_EXTENSION_PERIOD_CONFLICT`(409): 예약 충돌과 문구·details가 달라 따로 뒀다.
  - `RENTAL_EXTENSION_NOT_FOUND`(404): 계획의 4개에 더했다. 주문 API에 남의 대여 연장이나 없는 extensionId가 오면 낼 코드가 필요했다.
- **`PAYMENT_CANCELED_AFTER_EXPIRY` 문구를 "예약 또는 연장"으로 넓혔다.** 연장 경합(4-A)도 같은 코드로 답한다. 프론트는 code로 분기하므로 영향이 없다.
- **`PaymentConfirmResponse`에 `type`(RENTAL/EXTENSION)을 추가했다.** 승인 뒤 화면이 대여 상세와 연장 결과 중 어디로 갈지 고르려면 필요하다. 연장 결제의 `depositStatus`는 null이다.
- **연장료 주문명은 "{물품명} 대여 연장"이다.** 토스 결제창에서 대여 결제와 구분하려는 것이다.

## 결제 연결
- **주문 API에 `extensionId`를 더했다(payment-core 요약대로).** 연장 요청 응답은 `extensionId`·`additionalFee`·`expiresAt`만 주고 주문은 만들지 않는다. 결제창을 여는 흐름이 대여 결제와 같아지고, 주문 재사용(READY)·실패 뒤 새 주문 규칙도 그대로 쓴다.
- **`rental_extensions.payment_id`는 연장의 "현재 주문"(READY·FAILED 포함)이다.** 주문을 만들 때 묶고, 승인 실패 뒤 새 주문을 만들면 바꾼다. payments에는 extension_id 컬럼이 없어서, 승인할 때 주문 → 연장을 찾는 기준으로 이 컬럼을 쓴다(erd.md 정의는 "payment_id NULL"뿐이라 컬럼을 더하지 않았다).
  - 실패 뒤 새 주문에 밀려 연장에서 떨어진 옛 주문을 다시 승인하면 409 `PAYMENT_INVALID_STATUS`다.
  - 조정 배치가 그런 주문의 PG 승인을 찾으면 `approve()`로 맞춘 뒤 `EXTENSION_CANCEL` 전액 환불을 준비한다(키 `extension-refund:{paymentId}`). 처음에는 `needs_admin_check` 표시였다. 리뷰에서 바꾼 이유: 그 돈으로 확정할 연장이 없다는 것은 분명하고(연장은 현재 주문만 본다), 사람이 볼 일은 돌려주는 것뿐이라 자동 환불이 결과가 같고 더 빠르다. 연장 상태는 건드리지 않는다.
- **연장 결제 대기 시간은 `payment.expire-minutes`(15분)를 재사용한다.** 같은 결제창·같은 PG 흐름이라 대기 시간을 나눌 이유가 없다. 새 키를 만들지 않고 policies.md 설명에 "연장 결제 대기에도 적용"을 적었다.
- **결제 실패 해석은 대여 결제와 같다.** PG 승인 오류는 결제만 FAILED로 두고 연장은 대기로 남아 15분 안에 새 주문으로 재시도할 수 있다. 확정 실패인 금액 불일치만 즉시 연장 CANCELED다. rental.md 038 문장에도 적었다.
- **보증금 상태는 RENTAL 결제에만 둔다(open-issues #1).** 기존 `Payment.approve()`는 종류와 상관없이 FROZEN/NONE을 넣었고, `applyRefund`·`markRefundFailed`는 "NONE이 아니면" 상태를 바꿨다. 그대로 두면 연장 결제에 보증금 상태가 생기거나 null이 REFUNDED로 바뀐다. 승인은 RENTAL일 때만 넣고, 환불은 null·NONE을 건너뛰게 했다. RENTAL 동작은 회귀 테스트로 고정했다(`PaymentTest.rentalDepositRegression`).
- **연장 환불 멱등성 키는 `extension-refund:{paymentId}`다.** 계획에서는 extensionId였다. 환불을 준비하는 `PaymentRefundTxService.prepare`가 결제만 받고, 승인된 연장 결제는 연장당 1건이라 결제 ID로도 유일하다. 대여 취소 키 `cancel-refund:{rentalId}`와 겹치지 않는다. payment.md에 적었다.

## 경합 (open-issues #45, 사용자 결정 B+A)
- **종료일이 지난 RENTING도 연장 요청을 막는다(409 `RENTAL_EXTENSION_OVERDUE`, 오늘은 Clock 기준).** 00:00 ~ 00:05(연체 배치 전)에 연장을 요청하면 B의 건너뛰기가 연체를 계속 미룰 수 있었다. 요청 시점에 막으면 건너뛰기는 종료일 당일에 시작한 결제 대기(최대 15분)만큼만 미룬다. 승인 시점에는 이 검사를 하지 않는다. 종료일 당일 밤에 요청한 결제가 자정을 넘겨 승인되는 것이 B가 지키려는 경우이기 때문이다.
- **B: 연체 배치는 만료 전 결제 대기 연장이 있는 대여를 건너뛴다.** 대여 행 락 안에서 확인하고, 연장 승인도 대여 행 락을 먼저 잡으므로 둘이 한 줄로 선다. 연장이 만료되면 매시 재검사(최대 1시간 뒤)가 OVERDUE로 바꾼다. 연체료는 날짜로 계산해 금액이 같다.
- **A: 그래도 남는 경합은 승인 시점에 처리한다.** 결제 대기 중 반납 요청, PG 호출 중 만료가 그런 경우다. 연장이 만료됐거나, 대여가 RENTING이 아니거나, 종료일이 요청 때와 다르면 확정하지 않고 연장 CANCELED + 전액 환불, 409 `PAYMENT_CANCELED_AFTER_EXPIRY`로 답한다. 대여 결제의 "만료 뒤 승인"과 같은 경로이고 새 전이는 없다.
- **조정 배치에서 연장 결제는 항상 환불 쪽으로 간다.** 조정 대상은 `payment.expire-minutes` 동안 바뀌지 않은 결제라서, 볼 때는 연장이 이미 만료돼 있다. 대여 결제와 같은 규칙이고, 이 경우도 A로 처리한다.

## 락 순서
- **연장 메서드는 대여 행을 다시 락 걸지 않는다.** `RentalPaymentTxService.lockedRental`은 호출자가 `lock()`으로 이미 잡은 대여를 영속성 컨텍스트에서 꺼낸다(`findById`, 추가 FOR UPDATE 없음). 대여 행 락을 먼저 잡은 호출자만 부른다(javadoc). `lockExtensionByPayment`는 대여 ID를 함께 받아 다른 대여의 연장을 돌려주지 않는다.
**대여 행 → 연장 행 → 결제 행.** 연장 요청만 그 앞에 물품 락(`lock:item:{itemId}`, Redisson)을 잡는다. 확인한 경로:

| 경로 | 순서 |
| --- | --- |
| 연장 요청 `RentalExtensionTxService.request` | 물품 락 → 대여 행 (연장 행은 INSERT, 결제 없음) |
| 주문 생성 `ExtensionPaymentTxService.createOrder` | 대여 → 연장 → (기존 주문) 결제 |
| 승인 TX1 `prepareConfirm` / TX2 `applyApproved` | 대여 → 연장 → 결제 |
| PG 오류 TX2 `PaymentTxService.applyFailed` | 대여 → 결제 (연장을 건드리지 않음, 부분 순서) |
| 만료 배치 `ExtensionPaymentTxService.expire` | 대여 → 연장 → 결제(`closeReadyOrder`) |
| 조정 배치 `PaymentTxService.reconcile` | 대여 → (EXTENSION이면) 연장 → 결제 |
| 4-A 환불 TX1 | 승인 TX2 안(대여 → 연장 → 결제) / 환불 TX2·재시도 `PaymentRefundTxService` 대여 → 결제 |
| 연체 배치 `RentalOverdueTxService.markOverdue` | 대여 (연장은 락 없는 존재 확인) |

- 대여 결제·환불 경로(대여 → 결제)는 이 순서의 부분 순서라 교착하지 않는다.
- 승인 TX2와 만료 배치를 동시에 돌리는 테스트(`ExtensionPaymentIntegrationTest.approvalRacesExpireBatch`)로 확인했다. 어느 쪽이 먼저든 결과는 연장 CANCELED + 환불 1회다.

## 정산·내역
- **`sumExtensionFees`는 실제로 받은 금액으로 바꿨다.** 이 브랜치에서 연장 결제 환불이 생긴다(4-A, 만료 뒤 승인). 환불이 실패하면 결제가 APPROVED로 남아, 옛 방식(APPROVED amount 합)은 돌려줄 돈까지 제공자에게 정산한다. APPROVED·PARTIAL_CANCELED마다 amount − refunded_amount − refund_pending_amount를 더한다.
- **0원 연장·결제 기능 꺼짐은 결제 행 없이 바로 CONFIRMED이고, `payment_id`는 NULL이다.** 정산 연장료도 0이라 따로 처리할 것이 없다. 이력 changedBy는 대여자다(결제 승인으로 확정하면 null).
- **033 결제 내역에 연장 결제가 어떻게 보이는지만 확인했다(open-issues #44는 건드리지 않음).** EXTENSION 행은 `extensionFee` = 결제 금액이고, 대여료·보증금은 0, 보증금 상태는 null이다. 대여 기간은 대여의 현재 종료일(연장 반영 뒤)로 보인다.

## 알림
- **`RENTAL_EXTENDED`, event_id `RENTAL_EXTENDED:{rentalId}:{extensionId}`, 대여자·제공자 양측이다.** 연장은 대여당 최대 2회라 연장 ID로 구분한다. 양측에 같은 event_id를 쓰는 것은 `OVERDUE:{rentalId}` 선례를 따랐다. 커밋 뒤에 보낸다. 결제 경로는 `PaymentService`·`PaymentReconcileService`가, 0원 경로는 `RentalExtensionService`가 보낸다. 반납 리마인더 키는 endDate를 포함하므로 손대지 않았다.

## 남은 일
- **007 대여 가능 기간 수정(B 담당)은 결제 대기 연장 기간을 존중해야 한다.** 점유를 `RentalOccupancyReader.findOccupied`로 읽으면 자동으로 들어간다. 대여 행만 직접 조회하면 결제 대기 연장 기간을 가능 기간에서 빼 버릴 수 있다.
- **프론트가 연장 결제 화면을 떠났다가 돌아오면 extensionId를 다시 얻을 방법이 없다.** 대여 상세에 결제 대기 연장을 보여 주지 않는다. 15분 뒤 만료되면 다시 요청할 수 있지만, 그 사이에는 409 `RENTAL_EXTENSION_IN_PROGRESS`다. fe 연장 브랜치에서 상세 응답에 넣을지 정한다.
- 알림은 아직 로그 구현이다(REQ-032 전, `LoggingRentalNotificationPort`).
