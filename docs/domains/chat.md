# 채팅 (chat)

담당: 신수정(B) · 화면: SCR-09, 10 · 엔티티: chat_rooms, chat_messages, (chat_room_members 제안)

## 구조
- Spring WebSocket + STOMP, 엔드포인트 `/ws` (SockJS 폴백은 선택)
- **RabbitMQ STOMP 브로커 릴레이** (`enableStompBrokerRelay`, RabbitMQ `rabbitmq_stomp` 플러그인 필요) → 서버가 여러 대여도 구독자에게 전달
- 인증: STOMP CONNECT 헤더 `Authorization: Bearer <AT>` → `ChannelInterceptor`에서 검증, Principal = memberId
- 구독 인가: `SUBSCRIBE /sub/chat-rooms/{id}` 시 참여자 여부 검사, 아니면 거부
- 목적지(제안): SEND `/pub/chat-rooms/{id}`(메시지), `/pub/chat-rooms/{id}/read`(읽음) · SUB `/sub/chat-rooms/{id}`, `/user/queue/chat-rooms`(목록 갱신 이벤트), `/user/queue/notifications`(알림)

## REQ-026 채팅방 생성 [2 필수]
`POST /api/chat-rooms { itemId }`
- (item, renter) 당 1개. 있으면 기존 방 반환(200), 없으면 생성(201). UK(item_id, renter_id)로 동시 생성 경합 방지
- 본인 물품이면 400. 비회원은 프론트에서 로그인 유도
- 생성 시 item.chatCount + 1
- 차단 관계면 생성 불가(REQ-056 이후)

## REQ-027 실시간 메시지 송수신 [2 필수]
- SEND payload `{ type: "TEXT", content }` (≤1,000자)
- 서버: 참여자 검증 → DB 저장 → room.lastMessageAt·preview 갱신 → `/sub/chat-rooms/{id}`로 브로드캐스트(브로커 릴레이 경유) → 상대방 `/user/queue/chat-rooms`에 목록 갱신 이벤트
- 메시지 응답에 서버 생성 id·sentAt 포함. 클라이언트는 임시 id로 낙관적 표시 후 교체
- 연결 끊김: 클라이언트 자동 재연결 + 재연결 시 마지막 메시지 id 이후를 REST로 보충 조회
- **다중 서버 검증**: 인스턴스 2개에서 A는 서버1, B는 서버2에 붙어도 메시지 전달되는지 통합 테스트

## REQ-028 / REQ-029 목록·내역·읽음 [2]
- `GET /api/chat-rooms`: 상대 프로필 사진·닉네임·동네, 물품 썸네일(삭제 시 "삭제된 물품"), 마지막 메시지·시각, 안 읽은 수. lastMessageAt DESC
- `GET /api/chat-rooms/{id}/messages?cursor=`: 최신 50건, 커서로 이전 조회
- 읽음: 방 입장 또는 `/pub/chat-rooms/{id}/read` → 상대가 보낸 미읽음 메시지 readAt 기록 → 상대에게 읽음 이벤트
- 하단 탭 전체 안 읽은 수: `GET /api/chat-rooms/unread-count` + 실시간 이벤트로 갱신
- 구현 제안: 참여자별 `last_read_message_id`를 두면 안 읽은 수 = `count(id > last_read AND sender != me)`
- 예외: 참여자 아님 403, 상대 탈퇴 → "탈퇴한 사용자" + 전송 비활성화
- 축소 순서상 028·029는 022 다음으로 축소 가능하지만, 채팅방에 들어갈 진입점(목록)은 최소한으로라도 필요

## 채팅방 상단 대여 상태 (SCR-10)
- 방 정보 응답에 해당 (item, renter) 조합의 최근 대여 건 요약(id, status, 기간) 포함 → 상단 바에 상태 표시 + 예약/대여 상세 버튼
- 대여 상태 변경·차감 제안 등은 SYSTEM 메시지로 방에 남긴다(sender=null, type=SYSTEM)

## 3순위
- **REQ-050 사진 전송**: REST 업로드 → S3 → type=IMAGE 메시지 발행
- **REQ-051 약속 잡기**: 인계·반납 일시·장소 등록(type=APPOINTMENT), 약속 전 알림. 노쇼 판정(REQ-062)의 근거
- 금지어 필터(REQ-066) 적용 지점: 메시지 저장 전

## 테스트 포인트
- 비참여자 SUBSCRIBE·SEND 거부
- 2 인스턴스 간 전달
- 동시 채팅방 생성 시 1개만
- 안 읽은 수 정확성
