// 채팅 캐시를 실시간 이벤트로 고치는 순수 함수(TanStack Query setQueryData updater로 쓴다). 원본을 바꾸지 않는다.
// 내역 캐시는 useInfiniteQuery 데이터 { pages: [{ items(최신→과거), hasNext, nextCursor }], pageParams }.

/** 목록 정렬 기준: COALESCE(lastMessageAt, ...) 최신순. 목록 응답이 이미 정렬돼 있어 맨 앞으로 옮기기만 한다 */
function moveToFront(rooms, index, room) {
  return [room, ...rooms.slice(0, index), ...rooms.slice(index + 1)];
}

/**
 * /user/queue/chat-rooms 이벤트 { roomId, lastMessageId, lastMessagePreview, lastMessageAt, unreadCount } 를 목록에 반영.
 * 목록에 없는 방이면 null(호출부가 목록을 다시 불러온다). 목록을 아직 안 받았으면 그대로(undefined).
 */
export function applyRoomUpdate(rooms, event) {
  if (!rooms) {
    return rooms;
  }
  const index = rooms.findIndex((room) => room.roomId === event.roomId);
  if (index < 0) {
    return null;
  }
  return moveToFront(rooms, index, {
    ...rooms[index],
    lastMessagePreview: event.lastMessagePreview,
    lastMessageAt: event.lastMessageAt,
    unreadCount: event.unreadCount,
  });
}

/** 내가 보낸 메시지(방 토픽으로 돌아온 것)를 목록의 마지막 메시지로. 나에게는 목록 이벤트가 오지 않는다 */
export function applyOwnMessage(rooms, message) {
  if (!rooms) {
    return rooms;
  }
  const index = rooms.findIndex((room) => room.roomId === message.roomId);
  if (index < 0) {
    return null;
  }
  return moveToFront(rooms, index, {
    ...rooms[index],
    lastMessagePreview: message.content,
    lastMessageAt: message.sentAt,
  });
}

/** 읽음을 보낸 방의 안 읽은 수를 0으로 */
export function markRoomRead(rooms, roomId) {
  if (!rooms) {
    return rooms;
  }
  return rooms.map((room) =>
    room.roomId === roomId && room.unreadCount !== 0 ? { ...room, unreadCount: 0 } : room,
  );
}

/** 내역 캐시에 있는 가장 큰 메시지 id. 없으면 null */
export function getLatestMessageId(data) {
  let latest = null;
  for (const page of data?.pages ?? []) {
    for (const message of page.items ?? []) {
      if (latest === null || message.messageId > latest) {
        latest = message.messageId;
      }
    }
  }
  return latest;
}

function hasMessage(data, messageId) {
  return data.pages.some((page) => (page.items ?? []).some((m) => m.messageId === messageId));
}

/**
 * 받은 메시지들을 첫 페이지(최신 쪽)에 넣는다. 이미 있는 id는 건너뛰고, 첫 페이지는 id 내림차순을 유지한다.
 * 내역을 아직 안 받았으면 그대로(undefined). 바뀐 것이 없으면 같은 객체.
 */
export function mergeMessages(data, messages) {
  if (!data || data.pages.length === 0) {
    return data;
  }
  const fresh = [];
  for (const message of messages) {
    if (
      !hasMessage(data, message.messageId) &&
      !fresh.some((m) => m.messageId === message.messageId)
    ) {
      fresh.push(message);
    }
  }
  if (fresh.length === 0) {
    return data;
  }
  const [first, ...rest] = data.pages;
  const items = [...fresh, ...(first.items ?? [])].sort((a, b) => b.messageId - a.messageId);
  return { ...data, pages: [{ ...first, items }, ...rest] };
}

/**
 * 재연결 보충: 첫 페이지부터 받은 페이지들(items 최신→과거)에서 knownLatestId보다 큰 메시지만 고른다.
 * done은 더 받을 필요가 없는지(이미 가진 id까지 내려왔거나 마지막 페이지).
 * 가진 메시지가 없으면(knownLatestId null) 첫 페이지만 쓴다 — 이전 페이지는 위로 스크롤할 때 받는다.
 */
export function collectNewerMessages(page, knownLatestId) {
  const items = page?.items ?? [];
  if (knownLatestId === null) {
    return { newer: items, done: true };
  }
  const newer = items.filter((m) => m.messageId > knownLatestId);
  const reachedKnown = items.some((m) => m.messageId <= knownLatestId);
  return { newer, done: reachedKnown || !page?.hasNext };
}
