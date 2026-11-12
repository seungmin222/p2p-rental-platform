# feat/chat-core 결정 기록 (REQ-026·027)

## 요약
- API: `POST /api/chat-rooms`(생성 201 / 기존 방 200), STOMP `/ws`: SEND `/pub/chat-rooms/{id}`, SUB `/topic/chat-rooms.{id}`·`/user/queue/chat-rooms`·`/user/queue/notifications`·`/user/queue/errors`(본문은 api.md "STOMP 메시지"). Flyway `V17__create_chat_tables.sql`(chat_rooms·chat_room_members·chat_messages).
- 주요 파일: `chat/`(entity·repository·dto·event, service `ChatRoomService`·`ChatRoomTxService`·`ChatMessageService`·`ChatBroadcastListener`·`ChatDestinations`, controller `ChatRoomController`·`ChatMessageController`), `global/config/WebSocketConfig`(RabbitMQ 브로커 릴레이), `global/security/StompAuthChannelInterceptor`, `notification/service/StompNotificationPushPort`, `item/service/ItemChatCountWriter`. 테스트 `support/StompTestClient`, `chat/controller/*IntegrationTest`(다중 서버 포함).
- 남은 일(feat/chat-list, 028·029): `GET /api/chat-rooms`·`/{id}`(방 정보·최근 대여 상태, 이번에 미룸)·`/{id}/messages`(재연결 보충 조회)·`/unread-count`, `/pub/chat-rooms/{id}/read` → `chat_room_members.last_read_message_id` 갱신(커질 때만, 엔티티 메서드 추가) + 읽음 이벤트. 목록 갱신 이벤트(`ChatRoomUpdateResponse`)에 안 읽은 수를 더할지 그때 정한다.
- 남은 일(기타): 대여 상태 변경 SYSTEM 메시지(chat.md SCR-10, `ChatMessageType.SYSTEM`만 있음), 차단(056) 시 생성 불가. 프론트는 STOMP 연결·임시 메시지 교체(clientMessageId)·`/user/queue/errors` 처리가 필요하다.

## 정해진 값 (2026-11-12 사용자 승인)
- **방 구독 목적지는 `/topic/chat-rooms.{roomId}`** (open-issues #14 종료). RabbitMQ STOMP 브로커 릴레이는 `/topic/`·`/queue/`·`/exchange/` 등 정해진 접두사만 받아 `/sub/...`가 거부되고, 토픽 라우팅 키 구분자가 점이다. 서버가 `/sub`를 바꿔 주는 방식은 클라이언트가 받는 MESSAGE의 destination이 달라 헷갈려 버렸다. `/pub/**`·`/user/queue/*`는 그대로다.
- **삭제(DELETED) 물품은 404 `ITEM_NOT_FOUND`, 숨김(HIDDEN)은 방 생성 허용.** chat.md에 규칙이 없었다. 삭제는 물품 상세와 같게, 숨김은 이미 대화 중인 상대와 맞췄다(`ItemQueryService.getChatOwnerId`).
- **`GET /api/chat-rooms/{id}`(방 정보·최근 대여 상태)는 feat/chat-list로 미룬다.** 방 화면은 내역 조회(028)가 있어야 쓸 수 있다.

## 채팅방 생성 (026)
- **동시 생성은 UK(item_id, renter_id) + 기존 방 재조회.** 미리 찾고 없으면 insert, UK 위반(`DataIntegrityViolationException`)이면 먼저 커밋된 방을 200으로 돌려준다. `ChatRoomService`에는 트랜잭션을 걸지 않는다(위반 뒤 새로 읽어야 해서). insert는 `ChatRoomTxService`.
- **chat_count를 방 insert보다 먼저 올린다.** 반대 순서면 동시 요청들이 FK 검사로 물품 행 S 락을 쥔 채 UK 대기에 들어가고, 먼저 넣은 쪽이 카운트 X 락을 기다려 교착(1213)이 났다(동시 생성 테스트에서 확인). 지금은 같은 물품의 생성이 물품 행 X 락에서 줄을 서고, 늦은 쪽은 UK 위반으로 롤백된다(카운트도 되돌아감).
- **chat_room_members 두 행을 방 생성 때 넣는다**(last_read_message_id NULL). 029가 쓰는 확장 지점. 참여자 검사는 chat_rooms의 renter_id·owner_id로 한다(행 하나로 끝나서).

## 메시지 송수신 (027)
- **저장 → 커밋 뒤 전파**(`ChatMessageSentEvent` + AFTER_COMMIT `ChatBroadcastListener`). 롤백된 메시지가 나가지 않게. 전파 실패는 로그만, 재전송 없음(DB 기준, 재연결 시 REST 보충은 028).
- **방 행 FOR UPDATE 안에서 저장한다.** 마지막 메시지(preview·lastMessageAt)가 id 순서와 어긋나지 않게.
- **preview는 content 전체(최대 1,000자)를 저장**하고 자르기는 화면이 한다. 새 정책 수치를 만들지 않으려고.
- **SEND 거부는 연결을 끊지 않고 `/user/queue/errors`로 회신**(`{ clientMessageId, error }`, `@SendToUser(broadcast = false)`로 보낸 세션에만). 클라이언트가 임시 메시지를 실패 표시할 수 있게 clientMessageId를 돌려준다.
- **type은 TEXT만 받는다.** SYSTEM은 서버만 만든다(클라이언트가 보내면 400).
- **수신·발행 순서 보존을 켰다**(`setPreserveReceiveOrder`, `setPreservePublishOrder`). 기본 스레드 풀에서는 같은 세션의 연속 SEND가 다른 순서로 저장됐다(테스트에서 확인).

## STOMP 인증·인가
- **`StompAuthChannelInterceptor`(clientInboundChannel)**: CONNECT는 REST와 같은 `JwtProvider.parseAccessToken`, Principal 이름 = memberId. SUBSCRIBE는 방 토픽이면 참여자 검사, 아니면 정해진 개인 큐 3개만. SEND는 `/pub/`만(브로커 목적지로 바로 보내 검증을 건너뛰는 것 차단). AT 만료는 CONNECT 때만 본다.
- **거부는 예외 대신 ERROR 프레임을 직접 보내고 프레임을 버린다.** 수신 순서 보존을 켜면 인바운드 처리가 비동기라 인터셉터 예외가 클라이언트에 전달되지 않았다. ERROR의 `message` 헤더는 에러 코드이고, ERROR 뒤 서버가 연결을 닫는다.
- **WebSocket 핸드셰이크 `/ws`는 permitAll**(SecurityConfig). 같은 출처만 허용(기본값).

## 브로커 릴레이·테스트
- **릴레이는 AMQP와 같은 접속 정보(`RabbitConnectionDetails`)를 쓰고 포트만 `app.stomp.relay-port`**(`RABBITMQ_STOMP_PORT`, 기본 61613). `.env.example`에는 이미 키가 있었다.
- **다중 서버**: `setUserDestinationBroadcast`·`setUserRegistryBroadcast`로 다른 서버에 붙은 회원의 `/user` 목적지도 전달된다.
- **`reactor-netty-core` 추가**(릴레이 TCP 클라이언트). 버전은 Spring Boot BOM이 정한다.
- **테스트도 실제 릴레이**: Testcontainers RabbitMQ에 compose와 같은 `rabbitmq-plugins enable --offline rabbitmq_stomp`를 넣고 61613을 연다. 단순 브로커로는 목적지 접두사 문제와 서버 간 전달을 검증할 수 없다.
- **다중 서버 테스트는 두 번째 애플리케이션 컨텍스트**(`SpringApplicationBuilder`, 같은 컨테이너). 접속 정보는 명령행 인자로 넘긴다(`properties()`는 기본값이라 application.yml에 덮인다).

## 알림 실시간 전달 (032 남은 일)
- `StompNotificationPushPort`가 `/user/queue/notifications`로 보내고 예외를 삼킨다. `NoopNotificationPushPort` 삭제.
