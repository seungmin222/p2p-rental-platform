# 알림 (notification)

담당: 신수정(B) (연체 알림 트리거는 A) · 화면: SCR-11, 상단 알림 아이콘 · 엔티티: notifications, keywords, notification_settings

## 설계 결정
- 별도 브로커 추가 없음. **채팅과 같은 WebSocket(STOMP) 연결의 개인 큐** `/user/queue/notifications` 사용, 다중 서버 전달은 RabbitMQ 브로커 릴레이
- **DB 저장이 기준, 실시간 전달은 부가 경로**. 실시간 실패 시 재전송하지 않는다(재접속 시 목록 API)
- Redis Pub/Sub·SSE는 검토 후 채택하지 않음
- 다른 도메인은 알림을 직접 만들지 않고 `NotificationService.send(memberId, type, target, eventId, args)` 또는 도메인 이벤트 리스너를 통해서만 발행한다. 트랜잭션 커밋 후 발행(`AFTER_COMMIT`)

## REQ-032 거래 활동 알림 [2]
- 이벤트: `state-machines.md` §5 `NotificationType` 전체(3순위 표시 항목 제외). 수신자는 아래 표
- 저장 필드: member_id, type, content(표시 문구), target_type(RENTAL/ITEM/CHAT_ROOM/REVIEW), target_id(→ 클릭 시 이동), event_id(UK, 중복 방지), is_read
- event_id 규칙: `{TYPE}:{targetId}[:{추가키}]` 예) `OVERDUE:123`, `RETURN_REQUESTED:123`, `DEDUCTION_PROPOSED:123:{proposalId}`
- API: 목록(최신순 20건 커서), 개별 읽음, 모두 읽음, 안 읽은 수(`GET /api/notifications/unread-count`)
- 알림 설정(REQ-053) 구현 후에는 수신 거부 종류 미발송

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
| 상태 확인 결과·보증금 반환/보류 | ✔ | |
| 차감 제안 | ✔ | |
| 차감 수락·자동 수락·관리자 확정 (`DEDUCTION_ACCEPTED`) | ✔ | ✔ |
| 차감 거절 (`DEDUCTION_REJECTED`) | | ✔ |
| 연체 | ✔ | ✔ |
| 대여 연장 | ✔ | ✔ |
| 후기 도착 | 받는 사람 | 받는 사람 |
| AI 비교 완료 | ✔ | ✔ |

## 3순위
- **REQ-052 키워드 알림**: 물품 등록 시 같은 동네 범위 키워드 구독자에게 `KEYWORD_MATCHED` (등록 트랜잭션 밖, 비동기)
- **REQ-053 알림 설정**: 종류별 on/off
- **REQ-061 웹 푸시**: Web Push(VAPID). 앱 외부 수신
