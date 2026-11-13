import { describe, expect, it } from 'vitest';

import {
  applyRead,
  countTotalUnread,
  countUnread,
  decodeCursor,
  encodeCursor,
  listMyRooms,
  pageMessages,
  validateSend,
} from './chatLogic';

function createDb() {
  const messages = [];
  for (let id = 1; id <= 60; id += 1) {
    messages.push({
      messageId: id,
      roomId: 1,
      senderId: id % 2 === 0 ? 1 : 2,
      type: 'TEXT',
      content: `m${id}`,
      sentAt: `2026-11-10T10:${String(id % 60).padStart(2, '0')}:00`,
    });
  }
  messages.push({
    messageId: 61,
    roomId: 2,
    senderId: null,
    type: 'SYSTEM',
    content: '예약이 확정됐어요',
    sentAt: '2026-11-12T10:00:00',
  });
  return {
    rooms: [
      { roomId: 1, itemId: 101, ownerId: 2, renterId: 1, createdAt: '2026-11-01T10:00:00' },
      { roomId: 2, itemId: 106, ownerId: 4, renterId: 1, createdAt: '2026-11-02T10:00:00' },
      { roomId: 3, itemId: 105, ownerId: 2, renterId: 1, createdAt: '2026-11-13T09:00:00' },
      { roomId: 4, itemId: 103, ownerId: 1, renterId: 3, createdAt: '2026-11-13T08:00:00' },
      { roomId: 5, itemId: 102, ownerId: 2, renterId: 3, createdAt: '2026-11-13T08:00:00' },
    ],
    messages,
    lastRead: { '1:1': 50 },
  };
}

describe('listMyRooms', () => {
  it('참여한 방만, 마지막 메시지(없으면 생성) 시각 최신순', () => {
    expect(listMyRooms(createDb(), 1).map((room) => room.roomId)).toEqual([3, 4, 2, 1]);
  });
});

describe('countUnread', () => {
  it('읽음 위치 뒤의 상대 메시지와 SYSTEM 메시지를 센다', () => {
    const db = createDb();
    // 51~60 중 상대(홀수) 5건
    expect(countUnread(db, 1, 1)).toBe(5);
    expect(countUnread(db, 2, 1)).toBe(1);
    expect(countTotalUnread(db, 1)).toBe(6);
  });
});

describe('pageMessages', () => {
  it('최신 50건씩, 커서로 이전', () => {
    const db = createDb();
    const first = pageMessages(db, 1, null);
    expect(first.items[0].messageId).toBe(60);
    expect(first.items).toHaveLength(50);
    expect(first.hasNext).toBe(true);
    const second = pageMessages(db, 1, decodeCursor(first.nextCursor));
    expect(second.items.map((m) => m.messageId)).toEqual([10, 9, 8, 7, 6, 5, 4, 3, 2, 1]);
    expect(second).toMatchObject({ hasNext: false, nextCursor: null });
  });

  it('잘못된 커서는 undefined', () => {
    expect(decodeCursor('###')).toBeUndefined();
    expect(decodeCursor(encodeCursor(7))).toBe(7);
  });
});

describe('validateSend · applyRead', () => {
  it('TEXT만, 공백만·1,000자 초과는 거부', () => {
    expect(validateSend({ type: 'TEXT', content: 'hi' })).toBeNull();
    expect(validateSend({ type: 'SYSTEM', content: 'hi' })).toBe('COMMON_INVALID_INPUT');
    expect(validateSend({ type: 'TEXT', content: '   ' })).toBe('COMMON_INVALID_INPUT');
    expect(validateSend({ type: 'TEXT', content: 'a'.repeat(1001) })).toBe('COMMON_INVALID_INPUT');
  });

  it('읽음은 그 방 메시지만, 커질 때만', () => {
    const db = createDb();
    expect(applyRead(db, 1, 1, 61)).toEqual({ error: 'COMMON_INVALID_INPUT' });
    expect(applyRead(db, 1, 1, 40)).toEqual({ changed: false });
    expect(applyRead(db, 1, 1, 55)).toEqual({ changed: true });
    expect(countUnread(db, 1, 1)).toBe(2);
  });
});
