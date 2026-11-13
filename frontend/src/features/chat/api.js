// 채팅 REST 호출. 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
// STOMP 목적지·본문은 docs/api.md "STOMP 메시지"(Swagger에 없음) — 목적지 문자열은 constants.js CHAT_DESTINATIONS.
import { apiClient } from '../../shared/api';

/** POST /api/chat-rooms { itemId } → ChatRoomCreateResponse (새로 만들면 201, 있으면 200) */
export function createChatRoom(itemId) {
  return apiClient.post('/chat-rooms', { itemId });
}

/** GET /api/chat-rooms → ChatRoomSummaryResponse[] (페이지 없이 전체, 최근 순) */
export function fetchChatRooms() {
  return apiClient.get('/chat-rooms');
}

/** GET /api/chat-rooms/{roomId} → ChatRoomDetailResponse */
export function fetchChatRoom(roomId) {
  return apiClient.get(`/chat-rooms/${roomId}`);
}

/** GET /api/chat-rooms/{roomId}/messages?cursor= → CursorResponse<ChatMessageResponse> (items 최신→과거) */
export function fetchChatMessages(roomId, cursor) {
  return apiClient.get(`/chat-rooms/${roomId}/messages`, {
    params: cursor == null ? undefined : { cursor },
  });
}

/** GET /api/chat-rooms/unread-count → ChatUnreadCountResponse { unreadCount } */
export function fetchChatUnreadCount() {
  return apiClient.get('/chat-rooms/unread-count');
}
