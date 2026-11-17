import { describe, expect, it } from 'vitest';

import {
  countUnreadNotifications,
  decodeCursor,
  markAllRead,
  NOTIFICATION_PAGE,
  pageNotifications,
} from './notificationLogic';

function notification(notificationId, memberId = 1, isRead = false) {
  return {
    notificationId,
    memberId,
    type: 'OVERDUE',
    content: '반납 예정일이 지났어요.',
    targetType: 'RENTAL',
    targetId: 8,
    isRead,
    createdAt: '2026-11-08T09:00:00',
  };
}

describe('pageNotifications', () => {
  const all = [
    ...Array.from({ length: NOTIFICATION_PAGE + 5 }, (_, i) => notification(i + 1)),
    notification(100, 2),
  ];

  it('내 알림만 최신순 20건, 다음 커서로 나머지', () => {
    const first = pageNotifications(all, 1);
    expect(first.items).toHaveLength(NOTIFICATION_PAGE);
    expect(first.items[0].notificationId).toBe(NOTIFICATION_PAGE + 5);
    expect(first.hasNext).toBe(true);
    expect(first.items[0]).not.toHaveProperty('memberId');

    const second = pageNotifications(all, 1, decodeCursor(first.nextCursor));
    expect(second.items.map((item) => item.notificationId)).toEqual([5, 4, 3, 2, 1]);
    expect(second.hasNext).toBe(false);
    expect(second.nextCursor).toBeNull();
  });

  it('잘못된 커서는 undefined', () => {
    expect(decodeCursor('###')).toBeUndefined();
  });
});

describe('읽음', () => {
  it('안 읽은 수와 모두 읽음', () => {
    const all = [notification(1), notification(2, 1, true), notification(3, 2)];
    expect(countUnreadNotifications(all, 1)).toBe(1);
    expect(markAllRead(all, 1)).toBe(1);
    expect(countUnreadNotifications(all, 1)).toBe(0);
    expect(countUnreadNotifications(all, 2)).toBe(1);
  });
});
