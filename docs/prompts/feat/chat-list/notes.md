# feat/chat-list 결정 기록 (REQ-028·029)

## 요약
- API: `GET /api/chat-rooms`(전체·정렬 COALESCE(last_message_at, created_at)), `GET /api/chat-rooms/{id}`(상대·물품·최근 대여 건·counterpartLastReadMessageId), `GET /api/chat-rooms/{id}/messages?cursor=`(50건), `GET /api/chat-rooms/unread-count`, STOMP `/pub/chat-rooms/{id}/read` → 상대 `/user/queue/chat-reads`.
- 새 파일: `chat/service/ChatQueryService`, `ChatReadService`, `chat/repository/ChatMessageCursor`, `chat/dto/ChatRoom{Summary,Detail}Response` 등, 테스트 `ChatListIntegrationTest`.
- 목록 갱신 이벤트(`/user/queue/chat-rooms`)에 unreadCount·totalUnreadCount를 추가했다. GET 내역 조회는 읽음을 바꾸지 않는다.
- 남은 일: 탈퇴 상대에게 보내기 서버 차단(REQ-043), 프론트 채팅 목록·방 화면 연동.

## 정해진 값 (2026-11-13 사용자 승인)
- **목록은 페이지 없이 전체 반환.** 실시간 이벤트로 순서가 계속 바뀌는 목록이라 page 방식이면 항목이 밀리고, policies.md에 채팅 목록 크기 값이 없다.
- **메시지가 없는 방도 목록에 넣고 `COALESCE(last_message_at, created_at) DESC, id DESC`로 정렬.** 대여자가 만든 방을 다시 찾을 수 있게.
- **안 읽은 수에 SYSTEM 메시지(sender_id NULL)를 센다.** 명세식 `sender_id != me`는 SQL에서 NULL을 빼 버리지만 대여 상태 변경 안내는 안 읽은 것으로 보여야 한다. chat.md·erd.md 식을 `(sender_id IS NULL OR sender_id != me)`로 고쳤다.
- **최근 대여 건은 상태와 상관없이 (물품, 대여자)의 id 최대 건(CANCELED 포함).**

## 읽음 (029)
- **REST 내역 조회는 읽음을 바꾸지 않는다.** 클라이언트가 표시한 뒤 `/pub/chat-rooms/{id}/read { lastMessageId }`를 보낸다. GET에 부수효과를 두지 않고, 이전 페이지를 볼 때 읽음이 잘못 바뀌지 않게.
- **lastMessageId는 그 방에 저장된 메시지여야 한다**(아니면 COMMON_INVALID_INPUT). 큰 값을 받으면 앞으로 올 메시지까지 읽음 처리되기 때문.
- **내 chat_room_members 행을 FOR UPDATE로 잠그고 엔티티 `markRead`(커질 때만).** 엔티티 비교만으로는 동시 요청이 작은 값을 나중에 커밋해 되돌릴 수 있다(동시 20건 테스트로 확인).
- **읽음 이벤트는 새 개인 큐 `/user/queue/chat-reads`로 상대에게만** `{ roomId, memberId, lastReadMessageId }`. 방 토픽은 메시지 형태(`ChatMessageResponse`)만 유지해 본문 형태가 섞이지 않게. 값이 안 바뀌면 이벤트 없음. 거부는 메시지 SEND와 같이 `/user/queue/errors`(clientMessageId null).
- **방 정보에 `counterpartLastReadMessageId`를 넣었다**(계획에 없던 추가). 방에 들어온 직후 내 메시지의 '읽음' 표시 기준값이 필요하고, 이후 변화는 `/user/queue/chat-reads`로 받는다.
- **목록 갱신 이벤트에 받는 쪽의 `unreadCount`(그 방)·`totalUnreadCount`를 넣는다**(AFTER_COMMIT 리스너에서 쿼리 2개). 하단 탭 배지를 이 값으로 바꾼다. 내가 다른 기기에서 읽은 것은 실시간으로 맞추지 않는다(화면 진입 때 unread-count 재조회).

## 목록·내역 (028)
- **내역 커서는 알림과 같은 방식**: Base64(messageId), id DESC로 `chat.history.page`(50)건, 다음은 `id < cursor`. 응답 items는 최신→과거. 재연결 보충은 `after` 파라미터 없이 첫 페이지부터 이미 가진 id가 나올 때까지 받는다.
- **쿼리는 방 수와 상관없이 고정**: 방(chat_room_members member_id 인덱스 ⨝ chat_rooms) 1, 방별 안 읽은 수 GROUP BY 1, 회원 3(`MemberQueryService.getPublicSummaries` 새로 추가), 물품 2. 새 인덱스·Flyway 없음(messages (room_id, id), rentals (item_id, …) 기존 인덱스).
- **상대 탈퇴는 `counterpart.isWithdrawn=true`, 닉네임·사진·동네 null**(화면이 "탈퇴한 사용자" 표시·전송 비활성화). 마스킹은 chat 응답에서 하고, member는 `MemberPublicSummary.isWithdrawn`만 알려 준다. 서버에서 탈퇴 상대에게 보내기를 막지는 않았다(탈퇴 043은 3순위).
- **물품 삭제는 `item.isDeleted=true`로 이름·썸네일을 그대로 준다**(대여 화면 RentalItem과 같은 방식, 화면이 "삭제된 물품" 표시).
- **최근 대여 건은 `RentalSummaryReader.findLatest`**(rental 리포지토리는 rental 안에서만).

## 경합 수정 (chat-core에서 넘김)
- **`increaseChatCount`에 `status <> DELETED` 조건, 결과 0이면 `ItemChatCountWriter`가 404 ITEM_NOT_FOUND** → 방·참여자 insert 전체 롤백. 삭제가 방 insert 커밋 뒤에 오면 기존 방이 있는 물품을 지우는 것과 같아 막지 않는다.
