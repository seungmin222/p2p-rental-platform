import { describe, expect, it } from 'vitest';

import { markReadInList, prependNotification } from './notificationCache';

function n(notificationId, isRead = false) {
  return { notificationId, isRead, type: 'OVERDUE', targetType: 'RENTAL', targetId: 1 };
}

function data(...pages) {
  return {
    pages: pages.map((items) => ({ items, hasNext: false, nextCursor: null })),
    pageParams: [],
  };
}

describe('prependNotification', () => {
  it('첫 페이지 맨 앞에 넣는다', () => {
    const next = prependNotification(data([n(2)], [n(1)]), n(3));
    expect(next.pages[0].items.map((item) => item.notificationId)).toEqual([3, 2]);
    expect(next.pages[1].items.map((item) => item.notificationId)).toEqual([1]);
  });

  it('이미 있는 알림이나 목록이 없으면 undefined', () => {
    expect(prependNotification(data([n(2)], [n(1)]), n(1))).toBeUndefined();
    expect(prependNotification(undefined, n(1))).toBeUndefined();
  });
});

describe('읽음 반영', () => {
  it('하나만 읽음', () => {
    const next = markReadInList(data([n(2), n(1)]), 1);
    expect(next.pages[0].items.map((item) => item.isRead)).toEqual([false, true]);
  });

  it('목록이 없으면 undefined', () => {
    expect(markReadInList(undefined, 1)).toBeUndefined();
  });
});
