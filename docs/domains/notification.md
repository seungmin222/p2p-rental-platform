# 알림 (notification)

담당: 신수정(B) (연체 알림 트리거는 A) · 화면: SCR-11, 상단 알림 아이콘 · 엔티티: notifications, keywords, notification_settings

## 설계 결정
- 별도 브로커 추가 없음. **채팅과 같은 WebSocket(STOMP) 연결의 개인 큐** `/user/queue/notifications` 사용, 다중 서버 전달은 RabbitMQ 브로커 릴레이
- **DB 저장이 기준, 실시간 전달은 부가 경로**. 실시간 실패 시 재전송하지 않는다(재접속 시 목록 API)
- Redis Pub/Sub·SSE는 검토 후 채택하지 않음
- 다른 도메인은 알림을 직접 만들지 않고 `NotificationService.send(memberId, type, target, eventId, args)` 또는 도메인 이벤트 리스너를 통해서만 발행한다. 트랜잭션 커밋 후 발행(`AFTER_COMMIT`)

## REQ-032 거래 활동 알림 [2]
- 이벤트: `state-machines.md` §5 `NotificationType` 전체(3순위 표시 항목 제외). 수신자는 아래 표
- 저장 필드: member_id, type, content(표시 문구, 종류별 임시 문구 open-issues #50), target_type(RENTAL/ITEM/CHAT_ROOM/REVIEW), target_id(→ 클릭 시 이동), event_id(중복 방지, **(member_id, event_id) 유니크**: 양측에 같은 키를 보내도 회원마다 1건, open-issues #49), is_read
- event_id 규칙: `{TYPE}:{targetId}[:{추가키}]` 예) `OVERDUE:123`, `RETURN_REQUESTED:123`, `DEDUCTION_PROPOSED:123:{proposalId}`
- API: 목록(최신순 20건 커서), 개별 읽음, 모두 읽음, 안 읽은 수(`GET /api/notifications/unread-count`)
- 알림 설정(REQ-053) 구현 후에는 수신 거부 종류 미발송
- 실시간 전달(`/user/queue/notifications`)은 채팅(REQ-026~029)의 STOMP 설정과 함께 연결한다. 그 전에는 저장만 하고 목록·안 읽은 수 API로 확인한다(`NotificationPushPort` 빈 구현)

## REQ-031 연체 알림 [2 필수]
연체 배치(rental.md REQ-023)가 대여자·제공자 각각에게 `OVERDUE` 발행. event_id `OVERDUE:{rentalId}`로 매시 재검사에도 1회만.

## 수신자 매핑 (기본)
| 이벤트 | 대여자 | 제공자 |
| --- | --- | --- |
| 예약 확정/결제 완료 | ✔ | ✔ |
| 예약 취소 | 상대방 | 상대방 |
| 인계 확인 | ✔ | |
| 인계 리마인더 | ✔ | ✔ |
| 양측 동의 취소 요청 (`CANCEL_AGREEMENT_REQUESTED`) | 상대방 | 상대방 |
| 양측 동의 취소 결과 (`CANCEL_AGREEMENT_RESULT`) | ✔ | ✔ |
| 반납 리마인더 (`RETURN_REMINDER`, 예정일 하루 전) | ✔ | |
| 미반납 신고 (`LOST_REPORTED`) | ✔ | |
| 반납 요청 | | ✔ |
| 상태 확인 결과·보증금 반환/보류 (`INSPECTION_RESULT`·`DEPOSIT_REFUNDED`·`DEPOSIT_HELD`) | ✔ | |
| 차감 제안 (`DEDUCTION_PROPOSED`) | ✔ | |
| 차감 수락·자동 수락·관리자 확정 (`DEDUCTION_ACCEPTED`) | ✔ | ✔ |
| 차감 거절 (`DEDUCTION_REJECTED`) | | ✔ |
| 연체 | ✔ | ✔ |
| 대여 연장 | ✔ | ✔ |
| 후기 도착 | 받는 사람 | 받는 사람 |
| AI 비교 완료 | ✔ | ✔ |

수신자 표의 구현 해석(feat/notification, 명세에 세부가 없던 부분):
- 예약 확정/결제 완료: 결제 없이 바로 RESERVED면 `RESERVATION_CONFIRMED`, 결제 승인으로 RESERVED가 되면 `PAYMENT_COMPLETED`. 한 예약에 둘 중 하나만 간다
- 예약 취소: 단독 취소는 RESERVED였을 때만 상대방에게(결제 대기 취소·만료는 보내지 않음). 인계 없음 자동 취소는 양측
- 양측 동의 취소: 수락·거절 모두 양측에 `CANCEL_AGREEMENT_RESULT`(수락 때 `RESERVATION_CANCELED`는 따로 보내지 않음). 48시간 만료는 보내지 않는다
- 반납 요청: 제공자 대리 접수면 보내지 않는다
- 상태 확인 결과: 제공자 판정·자동 확인 모두 대여자에게. 보증금 반환(`DEPOSIT_REFUNDED`)은 PG 환불이 성공했을 때만(반환액 0원·보증금 없음은 보내지 않음)
- 인계 리마인더(`HANDOVER_REMINDER`): 리마인더 배치(rental.md REQ-017) 미구현이라 아직 보내지 않는다

## 3순위
- **REQ-052 키워드 알림**: 물품 등록 시 같은 동네 범위 키워드 구독자에게 `KEYWORD_MATCHED` (등록 트랜잭션 밖, 비동기)
- **REQ-053 알림 설정**: 종류별 on/off
- **REQ-061 웹 푸시**: Web Push(VAPID). 앱 외부 수신
