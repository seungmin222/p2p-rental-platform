/** policies.md chat.message.max — 메시지 최대 글자 수(코드 포인트, 서버와 같은 기준) */
export const CHAT_MESSAGE_MAX = 1_000;

/** docs/api.md "STOMP 메시지" */
export const CHAT_DESTINATIONS = {
  roomTopic: (roomId) => `/topic/chat-rooms.${roomId}`,
  send: (roomId) => `/pub/chat-rooms/${roomId}`,
  read: (roomId) => `/pub/chat-rooms/${roomId}/read`,
  roomUpdates: '/user/queue/chat-rooms',
  reads: '/user/queue/chat-reads',
  errors: '/user/queue/errors',
};

/** ChatMessageResponse.type */
export const CHAT_MESSAGE_TYPE = { TEXT: 'TEXT', SYSTEM: 'SYSTEM' };

/** 보내는 중 임시 메시지 상태(화면 전용) */
export const PENDING_STATUS = { SENDING: 'sending', FAILED: 'failed' };
