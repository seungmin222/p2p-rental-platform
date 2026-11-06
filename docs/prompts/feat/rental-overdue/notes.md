# feat/rental-overdue 결정 기록 (REQ-023, REQ-031)

## 연체료
- **계산은 `rental/service/OverdueFeeCalculator`가 맡고, 기준일을 인자로 받는다.** 반납 요청은 반납 요청일, 대여 상세는 오늘을 넘긴다. REQ-049 미반납 신고(open-issues P4)도 신고일을 넘겨 같은 함수를 쓰면 된다.
- **상태가 아니라 날짜로 계산한다.** 연체 배치(00:05)가 돌기 전에 RENTING인 채로 반납해도 금액이 같아야 하기 때문이다. 반납 요청은 RENTING·OVERDUE 모두 계산기를 거친다.
- **보증금 0원이면 `overdue_fee = 0`으로 저장한다.** 근거는 policies.md "무료 대여" 항목이다: 보증금 0원이면 연체료 징수를 건너뛰고 신뢰 점수에만 반영한다. 연체 일수는 `returnedAt − endDate`로 다시 구할 수 있으므로 REQ-054는 이 값을 쓴다.
- 1순위 모드(결제 끔)에서는 스냅샷 보증금이 있으면 연체료가 계산·기록된다. 동결된 결제가 없어 `prepareDepositReturn`이 PG를 부르지 않으므로 징수되지는 않는다.
- 정책 키는 `app.policy.overdue-fee.rate-percent: 150`이다(policies.md `overdue-fee.rate`). `cancel-fee.rate-percent`와 같은 정수 % 형식이고, 1일 연체료는 `floor(일 대여료 × 150 / 100)`을 먼저 절사한 뒤 일수를 곱한다.
- **상세 응답에는 `overdueFee` 필드 하나만 둔다.** OVERDUE이거나 RENTING인데 예정일이 지났으면 오늘 기준 현재까지 연체료(미확정)이고, 그 외 상태는 `rentals.overdue_fee`(반납 전 0, 반납 후 확정값)다. 확정 여부는 프론트가 status로 구분한다. 필드를 둘로 나누거나 null을 쓰면 프론트 분기만 늘기 때문이다.
- 연체료가 보증금 이상이면 기존 `PaymentRefundTxService.prepareDepositReturn`이 반환액 ≤ 0으로 FORFEITED 처리하고 COMPLETED로 끝낸다. 코드 변경 없이 동작한다. 상태 확인이 '이상'인 연체 건은 보증금이 FROZEN으로 남으므로, 연체료 선차감은 REQ-049가 처리한다.

## 연체 배치
- 두 cron(`overdue-detect-cron` 00:05, `overdue-recheck-cron` 매시 정각)을 한 메서드에 붙이고 락 이름 `rental.overdue-detect` 하나를 공유한다. 두 회차가 겹쳐도 한 번만 돈다.
- 건별 트랜잭션에서 행 락(`findByIdForUpdate`)으로 다시 읽고 조건을 다시 본다(멱등). 반납 요청은 행 락 없이 @Version으로 막으므로, 겹치면 반납 요청 쪽이 409로 끝나고 다시 시도하면 OVERDUE 상태에서 반납된다.
- "오늘"은 `TimeConfig`의 `Clock`(Asia/Seoul) 기준 `now.toLocalDate()`다.

## 알림 (REQ-031 발행까지)
- **`rental/service/RentalNotificationPort`만 두고, 저장·WebSocket은 REQ-032가 구현한다.** notifications 테이블은 B 담당이라 이 브랜치에서는 만들지 않는다(마이그레이션 없음).
- **event_id 생성은 이 브랜치가, 중복 방지(event_id 유니크)는 032 구현이 책임진다.** 생성 규칙: `OVERDUE:{rentalId}`, `RETURN_REMINDER:{rentalId}:{endDate}`(포트의 static 메서드). 032 구현은 같은 event_id가 다시 오면 저장하지 않고 예외 없이 무시해야 한다(포트 주석에 명시).
- 포트 enum `Type`·`TargetType`은 state-machines.md §5 코드 이름을 그대로 쓴다. 032는 `name()`으로 자기 enum에 매핑한다. 표시 문구는 032가 type으로 만든다.
- **발행은 전환 트랜잭션이 커밋된 뒤에 한다**(`RentalRefundPort.execute`와 같은 방식, notification.md AFTER_COMMIT). 커밋과 발행 사이에 서버가 멈추면 그 연체 알림은 빠진다. "실시간 실패 시 재전송하지 않는다"와 같은 수준으로 받아들였다.
- 운영 빈 `LoggingRentalNotificationPort`는 로그만 남긴다. **032가 실제 구현을 넣을 때 삭제한다.** 테스트는 `support/FakeRentalNotificationPort`(@Primary)로 발행 내용을 검증한다.
- 반납 리마인더 배치(`ReturnReminderJob`)는 상태를 바꾸지 않으므로 다시 돌면 같은 event_id로 다시 발행한다. 2인스턴스 중복 실행은 별도 락 `rental.return-reminder`로 막는다.
