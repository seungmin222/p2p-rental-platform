import { describe, expect, it } from 'vitest';

import { getNotificationPath } from './notificationTarget';

describe('getNotificationPath', () => {
  it('대상 종류별 화면', () => {
    expect(getNotificationPath({ targetType: 'RENTAL', targetId: 3 })).toBe('/rentals/3');
    expect(getNotificationPath({ targetType: 'ITEM', targetId: 7 })).toBe('/items/7');
    expect(getNotificationPath({ targetType: 'CHAT_ROOM', targetId: 9 })).toBe('/chats/9');
  });

  it('후기 받음(REVIEW_RECEIVED)은 대상이 RENTAL이라 대여 상세로 간다(REQ-034)', () => {
    expect(
      getNotificationPath({ type: 'REVIEW_RECEIVED', targetType: 'RENTAL', targetId: 14 }),
    ).toBe('/rentals/14');
  });

  it('대상 id가 없거나 화면이 없는 종류면 null', () => {
    expect(getNotificationPath({ targetType: 'RENTAL', targetId: null })).toBeNull();
    expect(getNotificationPath({ targetType: 'REVIEW', targetId: 1 })).toBeNull();
    expect(getNotificationPath({ targetType: 'UNKNOWN', targetId: 1 })).toBeNull();
    expect(getNotificationPath({})).toBeNull();
  });
});
