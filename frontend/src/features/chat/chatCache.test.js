import { describe, expect, it } from 'vitest';

import {
  applyOwnMessage,
  applyRoomUpdate,
  collectNewerMessages,
  getLatestMessageId,
  markRoomRead,
  mergeMessages,
} from './chatCache';

const rooms = [
  { roomId: 1, lastMessagePreview: 'a', lastMessageAt: '2026-11-13T10:00:00', unreadCount: 0 },
  { roomId: 2, lastMessagePreview: 'b', lastMessageAt: '2026-11-13T09:00:00', unreadCount: 1 },
  { roomId: 3, lastMessagePreview: null, lastMessageAt: null, unreadCount: 0 },
];

function message(messageId, extra = {}) {
  return { messageId, roomId: 1, senderId: 2, type: 'TEXT', content: `m${messageId}`, ...extra };
}

function history(...pages) {
  return { pages: pages.map((ids) => ({ items: ids.map((id) => message(id)) })), pageParams: [] };
}

describe('applyRoomUpdate', () => {
  it('그 방의 마지막 메시지·안 읽은 수를 고치고 맨 앞으로 옮긴다', () => {
    const next = applyRoomUpdate(rooms, {
      roomId: 3,
      lastMessageId: 9,
      lastMessagePreview: '새 메시지',
      lastMessageAt: '2026-11-13T11:00:00',
      unreadCount: 2,
      totalUnreadCount: 3,
    });
    expect(next.map((room) => room.roomId)).toEqual([3, 1, 2]);
    expect(next[0]).toMatchObject({ lastMessagePreview: '새 메시지', unreadCount: 2 });
    expect(rooms[2].unreadCount).toBe(0);
  });

  it('목록에 없는 방이면 null(다시 불러오게)', () => {
    expect(applyRoomUpdate(rooms, { roomId: 99 })).toBeNull();
  });

  it('목록을 아직 안 받았으면 그대로', () => {
    expect(applyRoomUpdate(undefined, { roomId: 1 })).toBeUndefined();
  });
});

describe('applyOwnMessage · markRoomRead', () => {
  it('내 메시지는 안 읽은 수를 그대로 두고 마지막 메시지만 바꾼다', () => {
    const next = applyOwnMessage(rooms, message(5, { roomId: 2, sentAt: '2026-11-13T12:00:00' }));
    expect(next[0]).toMatchObject({ roomId: 2, lastMessagePreview: 'm5', unreadCount: 1 });
  });

  it('읽은 방의 안 읽은 수를 0으로', () => {
    expect(markRoomRead(rooms, 2)[1].unreadCount).toBe(0);
  });
});

describe('mergeMessages', () => {
  it('새 메시지는 첫 페이지에 id 내림차순으로, 이미 있는 id는 건너뛴다', () => {
    const data = history([5, 4], [3, 2]);
    const next = mergeMessages(data, [message(7), message(4), message(6)]);
    expect(next.pages[0].items.map((m) => m.messageId)).toEqual([7, 6, 5, 4]);
    expect(next.pages[1]).toBe(data.pages[1]);
  });

  it('더할 것이 없으면 같은 객체', () => {
    const data = history([5, 4]);
    expect(mergeMessages(data, [message(5)])).toBe(data);
  });

  it('내역을 아직 안 받았으면 그대로', () => {
    expect(mergeMessages(undefined, [message(1)])).toBeUndefined();
  });
});

describe('getLatestMessageId · collectNewerMessages', () => {
  it('가장 큰 id', () => {
    expect(getLatestMessageId(history([5, 4], [3]))).toBe(5);
    expect(getLatestMessageId(history([]))).toBeNull();
  });

  it('가진 id까지 내려오면 끝', () => {
    const page = { items: [message(9), message(8), message(5)], hasNext: true };
    expect(collectNewerMessages(page, 5)).toEqual({
      newer: [message(9), message(8)],
      done: true,
    });
  });

  it('가진 id가 안 나오면 다음 페이지가 있는 동안 계속', () => {
    const page = { items: [message(9), message(8)], hasNext: true };
    expect(collectNewerMessages(page, 5).done).toBe(false);
    expect(collectNewerMessages({ ...page, hasNext: false }, 5).done).toBe(true);
  });

  it('가진 메시지가 없으면 첫 페이지만', () => {
    const page = { items: [message(2), message(1)], hasNext: true };
    expect(collectNewerMessages(page, null)).toEqual({ newer: page.items, done: true });
  });
});
