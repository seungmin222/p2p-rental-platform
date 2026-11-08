# feat/notification 결정 기록 (REQ-032, REQ-031 저장 연결)

## 요약
- API: `GET /api/notifications?cursor=`(최신순 `notification.page` 20건, id 커서), `PATCH /api/notifications/{id}/read`(멱등, 없음 404 `NOTIFICATION_NOT_FOUND`, 남의 알림 403 `NOTIFICATION_NOT_OWNER`), `PATCH /api/notifications/read-all`(`updatedCount`), `GET /api/notifications/unread-count`. 마이그레이션 `V14__create_notifications.sql`.
- 주요 파일: `notification/service/NotificationService`(`send` 단일 진입점·읽음)·`NotificationTxService`(저장, REQUIRES_NEW)·`NotificationQueryService`·`NotificationContentTemplate`(임시 문구)·`NotificationPushPort`·`NoopNotificationPushPort`·`RentalNotificationAdapter`(`RentalNotificationPort` 구현), `rental/service/RentalNotificationPublisher`·`RentalNotificationEventListener`, `rental/event/RentalNotificationEvent`. `LoggingRentalNotificationPort` 삭제. 테스트 `support/FakeRentalNotificationPort`는 기록 + 실제 구현 위임으로 바뀌었다.
- 발행 지점: 기존 포트 호출(OVERDUE·RETURN_REMINDER·LOST_REPORTED·DEPOSIT_HELD·DEDUCTION_*·RENTAL_EXTENDED·AI_COMPARE_DONE)은 그대로 실구현에 연결. 새로 RESERVATION_CONFIRMED·PAYMENT_COMPLETED·RESERVATION_CANCELED·CANCEL_AGREEMENT_REQUESTED·CANCEL_AGREEMENT_RESULT·HANDOVER_CONFIRMED·RETURN_REQUESTED·INSPECTION_RESULT·DEPOSIT_REFUNDED (수신자 해석은 notification.md "수신자 표의 구현 해석").
- 남은 일(REQ-026~029 채팅): STOMP 설정 때 `NotificationPushPort`를 `/user/queue/notifications` 전송 구현으로 바꾸고 `NoopNotificationPushPort`를 삭제한다.
- 남은 일(REQ-017): 인계 리마인더 배치(매일 09:00, rental.md)가 없어 `HANDOVER_REMINDER`를 보내지 않는다. 배치를 만들면 `RentalNotificationPort`에 타입·키(`HANDOVER_REMINDER:{rentalId}`)를 더해 양측에 보낸다.
- 남은 일(REQ-034): 후기 구현 때 `REVIEW_RECEIVED`(target_type REVIEW 또는 RENTAL)를 `NotificationService.send`로 보낸다.
- 남은 일(REQ-053): `NotificationService.isEnabled`가 알림 설정을 볼 자리다(지금은 항상 true).
- 미결: open-issues #50(알림 문구 임시).

## 중복 방지 키 (open-issues #49)
- **유니크를 `(member_id, event_id)`로 바꿨다.** erd.md는 event_id 단일 유니크였지만 `OVERDUE:{rentalId}`·`AI_COMPARE_DONE:{rentalId}`를 양측에 같은 키로 보낸다(#48 사용자 결정). 키에 수신자를 넣는 안은 notification.md의 event_id 규칙 예시와 포트의 키 생성 메서드, 기존 테스트를 모두 바꿔야 해서 택하지 않았다.
- **저장은 "있는지 확인 → INSERT"이고, 동시에 들어와 유니크 위반이 나면 다시 확인해 중복이면 조용히 넘긴다.** 처음 계획한 `INSERT ... ON DUPLICATE KEY UPDATE id = id`는 Connector/J 기본값(found rows)에서 중복도 1행으로 세어 저장 여부를 알 수 없었다(테스트에서 확인). INSERT IGNORE는 FK·길이 오류까지 삼켜 쓰지 않았다. 동시 8건 테스트로 1건만 저장되는 것을 고정했다.
- 새 키: 한 대여에 한 번인 알림은 `{TYPE}:{rentalId}`, 양측 동의 취소는 요청별 `{TYPE}:{rentalId}:{agreementId}`(거절 뒤 다시 요청할 수 있어서).

## 트랜잭션
- **새 발행 지점은 상태 전이 트랜잭션 안에서 `RentalNotificationPublisher`로 이벤트만 남기고, `@TransactionalEventListener(AFTER_COMMIT)`가 포트로 보낸다**(backend/CLAUDE.md 도메인 간 후속 동작 규칙). 결제 승인처럼 결제 트랜잭션에 참여하는 `RentalPaymentTxService`에서 남겨도 바깥 트랜잭션 커밋 뒤에 보낸다. 롤백되면 보내지 않는다.
- 기존 지점(Service가 TxService 커밋 뒤 포트 직접 호출)은 그대로 뒀다. 동작이 같고 바꿀 이유가 없다.
- `NotificationTxService.insertIfAbsent`는 REQUIRES_NEW다. AFTER_COMMIT 단계에서 끝난 트랜잭션에 참여하면 쓰기가 커밋되지 않는다. `NotificationService.send`는 트랜잭션 밖(NOT_SUPPORTED)에서 저장을 부르고 예외를 모두 잡는다(포트 계약: 던지지 않는다). 실시간 전달은 저장 커밋 뒤, 새로 저장됐을 때만 한다.
- 받아들인 한계: 커밋과 리스너 사이에 서버가 멈추면 그 알림은 빠진다(rental-overdue 브랜치와 같은 수준).
- `RentalNotificationEventListener`도 예외를 잡고 로그만 남긴다(리뷰 반영). 포트 계약이 깨져도 이미 커밋된 상태 전이의 응답이 실패로 보이지 않게 하려는 이중 방어다.
- 어댑터는 `RentalNotificationPort.Type`·`TargetType`을 이름(`valueOf`)으로 `NotificationType`·`NotificationTargetType`에 바꾼다. 두 enum 이름이 어긋나면 런타임에 실패하므로 단위 테스트(`RentalNotificationAdapterTest`)로 고정했다.

## 권한 오류 (리뷰 반영)
- 남의 알림 읽음은 404에서 403 `NOTIFICATION_NOT_OWNER`로 바꿨다. architecture.md「HTTP 상태 코드 사용 규칙」의 403(당사자 아님)에 맞추고 `RENTAL_NOT_PARTICIPANT`와 같은 방식(먼저 id로 찾아 없으면 404, 주인이 다르면 403)이다. 알림 id는 순번이라 존재 여부가 드러나도 숨길 정보가 없다고 봤다.

## 수신자 해석 (명세에 세부가 없던 부분)
- 예약 확정/결제 완료는 한 행이라, 결제 없이 바로 RESERVED면 `RESERVATION_CONFIRMED`, 승인으로 RESERVED면 `PAYMENT_COMPLETED` 하나만 양측에 보낸다.
- 단독 취소는 RESERVED였을 때만 상대방에게 보낸다. 결제 대기 취소는 아직 확정된 예약이 아니라서다. 인계 없음 자동 취소는 양측(rental.md "양측 알림").
- 양측 동의 취소는 수락·거절 모두 양측에 결과를 보낸다. rental.md는 거절만 적었지만 수신자 표는 "결과" 한 행이다. 수락 때 `RESERVATION_CANCELED`를 따로 보내면 같은 일에 알림이 둘이라 보내지 않는다. 만료는 명세에 알림이 없어 보내지 않는다.
- 반납 요청은 제공자 대리 접수면 보내지 않는다(제공자 본인이 한 일).
- `INSPECTION_RESULT`는 제공자 판정·자동 확인 모두 대여자에게. 이상 판정 보류면 `DEPOSIT_HELD`도 함께 간다.
- `DEPOSIT_REFUNDED`는 보증금 반환 PG 환불 성공 때만(`PaymentRefundTxService.applyResult`, completion이 있는 환불). 반환액 0원(FORFEITED)·보증금 없음은 돌려준 돈이 없어 보내지 않는다.

## 문구·실시간
- 문구는 명세에 없어 `NotificationContentTemplate`에 종류마다 고정 문구를 두고 임시로 표시했다(open-issues #50). 물품명 같은 인자는 넣지 않았다.
- 실시간 전달은 포트(`NotificationPushPort`)만 두고 빈 구현을 넣었다. notification.md "DB 저장이 기준, 실시간은 부가 경로"이고 STOMP 연결·인증은 채팅이 만든다.

## 테스트
- `NotificationIntegrationTest`: 같은 키 재발행 1건·다른 회원 같은 키 2건, 동시 8건 1건, 저장 실패(없는 회원) 무예외, 목록 커서(21건 → 20 + 1), 잘못된 커서 400, 개별 읽음(남의 것 403·없음 404·멱등), 모두 읽음, 401.
- 배치 재실행: `OverdueDetectJobTest`(양측 1건씩), `ReturnReminderJobTest`(두 번 돌려도 1건).
- 롤백: `RentalCancelIntegrationTest.noNotificationOnRollback`(Publisher → AFTER_COMMIT 경로, 롤백이면 포트 호출·저장 없음). 결제 대기 단독 취소는 알림 없음(`pendingPaymentCancelNoNotification`).
- `RentalOverdueConcurrencyTest`·`AiCompareIntegrationTest`는 종류로 걸러 보지 않고 기대 알림 집합을 정확히 비교한다(반납 요청의 `RETURN_REQUESTED` 포함, AI 테스트는 준비 단계 알림을 지운 뒤 비교).
- 새 발행 지점은 기존 통합 테스트의 정상 경로에 발행 검증을 더했다(예약·결제 승인·단독 취소·동의 취소 요청/수락/거절·인계·반납 요청·대리 접수·상태 확인·인계 없음 자동 취소·보증금 반환·FORFEITED).
