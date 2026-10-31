# feat/payment-core 결정 기록

## 요약
- API: `POST /api/payments`, `POST /api/payments/confirm`. 내부 호출: 취소 환불, 보증금 반환. 배치: 결제 만료, 인계 없음 자동 취소, 환불 재시도, PG 조정.
- 주요 파일: `payment/service/PaymentService`·`PaymentTxService`·`PaymentRefundService`·`PaymentRefundTxService`·`PaymentReconcileService`, `payment/client/PaymentGateway`·`TossPaymentGateway`, `payment/batch/*`, `rental/service/RentalRefundPort`·`RentalPaymentTxService`·`RentalCancelTxService`, `rental/batch/NoHandoverCancelJob`, `V7__create_payment_tables.sql`, 테스트 `support/FakePaymentGateway`·`PaymentFixture`.
- 남은 일: 장애 주입 결과표(`docs/experiments/payment.md`)와 결제 알림(REQ-032)은 이 브랜치 범위 밖이다. 토스 실제 호출은 로컬에서 수동 확인이 필요하다. 동시성 테스트 공백(늦은 승인 vs 만료 배치 동시 실행, 환불 중 상태 확인)은 14주차 장애 주입 실험에서 다룬다.
- 다음 브랜치: `feat/rental-overdue`는 `requestReturn`에서 `overdue_fee`만 채우면 된다. REQ-049는 아래 "상태 확인 이상"에서 잇고, REQ-039는 `PaymentRefundTxService`의 주석 두 곳(취소 수수료, 보증금 반환 완료)과 PG 없이 완료되는 `RentalInspectionTxService.judge`에 정산 생성을 붙인다. 연장 결제(038)는 `Payment`에 팩토리와 주문 API의 extensionId를 더한다.

## 범위
- **인계 없음 자동 취소(REQ-017)를 포함했다.** 결제를 켜면 인계 없는 RESERVED의 돈이 양측 동의 외에 돌아갈 길이 없어서다. 주기는 policies.md 매일 09:00.
- **알림·정산은 만들지 않았다.** 리스너 없는 이벤트는 쓰임이 정해질 때 만든다(rental-reserve 결정과 같음).

## 트랜잭션·락
- **PG 호출은 `*TxService` 밖에서 한다.** 취소·상태 확인은 기존 `RentalReservationTxService` 패턴대로 바깥 서비스(비트랜잭션) + TxService로 나눴다. AFTER_COMMIT 리스너는 후속 쓰기에 REQUIRES_NEW가 필요하고 응답에 결과를 담지 못해 쓰지 않았다.
- **rental → payment 직접 의존을 만들지 않고 `RentalRefundPort`를 rental에 뒀다.** prepare가 같은 트랜잭션에 묶여야 해서(대여 전이와 환불 기록이 같이 커밋돼야 서버가 죽어도 환불이 남는다) 이벤트로 끊을 수 없다. payment → rental 한 방향만 남는다.
- **모든 쓰기는 대여 행 락 → 결제 행 락 순서다.** 락 전에 일반 SELECT를 하지 않는다. MySQL REPEATABLE READ에서 첫 일반 읽기가 스냅샷을 만들어, 락을 기다린 뒤에도 옛 값을 읽기 때문이다(그래서 orderId → rentalId 조회는 별도 트랜잭션).
- **상태 확인·자동 확인을 @Version에서 행 락으로 바꿨다.** 보증금 반환을 기다리면 대여가 바뀌지 않아 버전으로는 동시 판정을 못 막는다. 늦은 쪽은 기다린 뒤 `RENTAL_INVALID_TRANSITION`(또는 자동 확인이면 건너뜀)이다.
- **금액 불일치는 커밋한 뒤 400을 던진다.** 트랜잭션 안에서 던지면 FAILED·CANCELED가 롤백된다.

## 승인
- **TX2도 만료 시각을 본다.** 상태만 보면, 승인이 도는 사이 만료 시각이 지나 다른 사람이 예약한 기간을 확정해 중복 예약이 된다. 시각이 지났으면 PG가 승인했어도 전액 취소한다.
- **PG 오류면 FAILED, 재결제는 새 주문이다.** 대여는 PENDING_PAYMENT를 유지한다. READY 주문은 재사용해 결제창을 다시 열 수 있다.
- **응답 유실이면 orderId로 한 번 조회한다.** 안 보이면 처리 중일 수 있어 실패로 처리하지 않고 503 + READY·시도 PENDING으로 둔다.
- **만료·취소 때 시도 PENDING인 READY 주문은 닫지 않는다.** PG에 승인됐을 수 있어 조정 배치가 판단한다.

## 환불
- **`refund_pending_amount`로 재시도 대상을 찾는다.** depositStatus만으로는 보증금 0원 결제의 취소 환불 실패(NONE 유지)와 TX1 직후 서버 중단을 찾을 수 없다.
- **재시도는 금액을 다시 계산하지 않고 마지막 시도의 금액·키를 쓴다.** 값이 달라지면 같은 키의 의미가 깨진다.
- **첫 시도는 재시도 횟수에 세지 않는다.** "최대 5회"는 재시도 5회이므로 PG 호출은 최대 6번이고, 5번째 재시도가 실패하면 `needs_admin_check`다.
- **재시도 대상은 `last_refund_attempt_at`이 간격(10분) 이상 지난 것만이다.** 처음에는 `updated_at`을 봤는데, 이 값은 PG 응답 뒤(TX2)에 찍혀서 10분 정각 회차에 대상이 못 되고 한 회차씩 밀렸다. 진행 중인 첫 시도와 겹치지 않는 효과는 같고, 겹쳐도 같은 키라 중복 환불은 없다.

## 연체료
- **반환액은 `rentals.overdue_fee`를 읽고, 지금은 항상 0이다.** 값을 확정하는 곳이 REQ-023의 `requestReturn`인데 아직 없기 때문이다. `feat/rental-overdue`는 결제 코드를 고칠 필요가 없다. 테스트는 ReflectionTestUtils로 값을 넣어 차감·FORFEITED를 확인한다.

## 상태 확인 이상 (REQ-049 전)
- **보증금이 FROZEN이면 판정만 기록하고 RETURNED·FROZEN을 유지한다.** 새 전이가 아니라 RETURNED 유지다. 1순위처럼 완료하면 돌려줄 길이 없는 돈이 남는다.
- **REQ-049가 이 상태에서 HELD로 가는 전이를 추가한다.** 그때까지 이 건은 자동 상태 확인에서도 빠지므로(판정됨) **prod 플래그는 049 머지 전까지 꺼 둔다.**
- 동결 보증금이 없으면(결제 없음·보증금 0원) 처리할 금액이 없어 기존처럼 COMPLETED다. 프론트 목(`VITE_PAYMENT_ENABLED=true`)은 이 경우에도 RETURNED로 두므로 연동 때 맞춘다.

## 플래그
- **`payment-enabled`는 예약의 초기 상태만 정한다.** 환불·반환은 승인된 결제 행으로 갈린다. 플래그를 꺼도 받은 돈은 돌려줘야 한다. 그래서 결제 테스트 대부분은 PENDING_PAYMENT를 직접 저장하고, 플래그를 켠 컨텍스트는 `PaymentEnabledFlowIntegrationTest` 하나다.
- **`TOSS_SECRET_KEY`는 결제 플래그를 꺼도 승인된 결제가 남아 있는 동안은 환불에 필요하다.** 기동 검사는 플래그가 켜졌을 때만 키를 요구하므로, 끄면서 키까지 지우면 환불이 실패로 남는다.

## 토스 연동
- **read-timeout 30초, connect 3초(기술 설정, application.yml).** 토스 문서는 일반 승인·취소의 권장값을 두지 않는다. 60초 권장은 자동결제 승인·거래 조회·정산 조회에만 있어 그대로 옮기지 않았다. 30초를 넘겨도 Unknown → 같은 키 재요청·조회로 안전하므로, 요청 스레드를 오래 잡지 않는 쪽을 택했다.
- **`reconciliation.lookback-hours` 24시간은 초기 가정이다**(policies.md). 진행 중 요청과 겹치지 않도록 `payment.expire-minutes`(15분) 동안 안 바뀐 결제만 본다.
- **Toss의 `IDEMPOTENT_REQUEST_PROCESSING`·`ALREADY_PROCESSED_PAYMENT`는 Unknown으로 다룬다.** 결과는 재요청이나 조회로 확인한다.
- **`customerKey`는 회원 ID에서 만든 UUID다.** 저장하지 않아도 회원마다 같은 값이고 ID를 그대로 내보내지 않는다.
- **`VITE_TOSS_CLIENT_KEY`는 frontend/.env.example의 이름을 그대로 infra/.env.example에도 넣었다.** Vite는 infra/.env를 읽지 않아 frontend/.env.local에도 넣어야 한다.
