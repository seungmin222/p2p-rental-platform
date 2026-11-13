// 채팅 목의 계산(순수 함수). 저장소 db = { rooms, messages, lastRead, nextRoomId, nextMessageId }.
// - rooms: { roomId, itemId, ownerId, renterId, createdAt }
// - messages: { messageId, roomId, senderId(SYSTEM은 null), type, content, sentAt } id 오름차순
// - lastRead: { "roomId:memberId": messageId } (chat_room_members.last_read_message_id)
// 규칙은 docs/domains/chat.md·api.md와 같게: 정렬 COALESCE(마지막 메시지 시각, 방 생성 시각) DESC, id DESC,
// 안 읽은 수 = id > 읽음 위치 AND (보낸 사람이 null 이거나 나가 아님), 내역은 id DESC 50건 + Base64(id) 커서.

/** policies.md chat.history.page·chat.message.max(목은 서버 흉내라 화면 상수를 가져오지 않는다) */
export const CHAT_POLICY = { HISTORY_PAGE: 50, MESSAGE_MAX: 1000 };

export function readKey(roomId, memberId) {
  return `${roomId}:${memberId}`;
}

export function isParticipant(room, memberId) {
  return room.ownerId === memberId || room.renterId === memberId;
}

export function counterpartOf(room, memberId) {
  return room.ownerId === memberId ? room.renterId : room.ownerId;
}

function roomMessages(db, roomId) {
  return db.messages.filter((message) => message.roomId === roomId);
}

export function lastMessageOf(db, roomId) {
  const messages = roomMessages(db, roomId);
  return messages.at(-1) ?? null;
}

export function countUnread(db, roomId, memberId) {
  const lastRead = db.lastRead[readKey(roomId, memberId)] ?? 0;
  return roomMessages(db, roomId).filter(
    (message) =>
      message.messageId > lastRead && (message.senderId == null || message.senderId !== memberId),
  ).length;
}

export function countTotalUnread(db, memberId) {
  return db.rooms
    .filter((room) => isParticipant(room, memberId))
    .reduce((sum, room) => sum + countUnread(db, room.roomId, memberId), 0);
}

/** 내 방 목록(정렬만). 응답 모양은 호출부(mocks/chat.js)가 만든다 */
export function listMyRooms(db, memberId) {
  const sortKey = (room) => lastMessageOf(db, room.roomId)?.sentAt ?? room.createdAt;
  return db.rooms
    .filter((room) => isParticipant(room, memberId))
    .sort((a, b) => sortKey(b).localeCompare(sortKey(a)) || b.roomId - a.roomId);
}

export function encodeCursor(messageId) {
  return btoa(String(messageId));
}

/** 잘못된 커서면 undefined(400) */
export function decodeCursor(cursor) {
  try {
    const value = atob(cursor);
    return /^\d+$/.test(value) ? Number(value) : undefined;
  } catch {
    return undefined;
  }
}

/** 내역 한 페이지: id < before 중 최신 50건(최신→과거) */
export function pageMessages(db, roomId, before) {
  const older = roomMessages(db, roomId)
    .filter((message) => before == null || message.messageId < before)
    .reverse();
  const items = older.slice(0, CHAT_POLICY.HISTORY_PAGE);
  const hasNext = older.length > items.length;
  return {
    items,
    hasNext,
    nextCursor: hasNext ? encodeCursor(items.at(-1).messageId) : null,
  };
}

/** 메시지 SEND 검사(서버 ChatMessageService와 같은 순서). 통과하면 null, 아니면 에러 코드 */
export function validateSend(body) {
  if (!body || body.type !== 'TEXT' || typeof body.content !== 'string') {
    return 'COMMON_INVALID_INPUT';
  }
  if (body.content.trim() === '' || [...body.content].length > CHAT_POLICY.MESSAGE_MAX) {
    return 'COMMON_INVALID_INPUT';
  }
  return null;
}

/** 읽음: 그 방 메시지여야 하고, 커질 때만 바꾼다. 바뀌었으면 true */
export function applyRead(db, roomId, memberId, lastMessageId) {
  const exists = db.messages.some(
    (message) => message.roomId === roomId && message.messageId === lastMessageId,
  );
  if (!exists) {
    return { error: 'COMMON_INVALID_INPUT' };
  }
  const key = readKey(roomId, memberId);
  if ((db.lastRead[key] ?? 0) >= lastMessageId) {
    return { changed: false };
  }
  db.lastRead[key] = lastMessageId;
  return { changed: true };
}
