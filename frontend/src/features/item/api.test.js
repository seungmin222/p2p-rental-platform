import { describe, expect, it } from 'vitest';

import { toMyItem } from './api';

describe('toMyItem', () => {
  it('가정한 응답 필드를 카드 모양으로 옮긴다', () => {
    const item = {
      itemId: 103,
      name: '캠핑 의자 2개',
      thumbnailUrl: '/files/103.jpg',
      pricePerDay: 3000,
      deposit: 10000,
      viewCount: 12,
      wishCount: 3,
      chatCount: 2,
      upcomingRentalCount: 1,
      extra: 'ignored',
    };
    expect(toMyItem(item)).toEqual({
      itemId: 103,
      name: '캠핑 의자 2개',
      thumbnailUrl: '/files/103.jpg',
      pricePerDay: 3000,
      deposit: 10000,
      viewCount: 12,
      wishCount: 3,
      chatCount: 2,
      upcomingRentalCount: 1,
    });
  });

  it('없는 카운터는 0, 썸네일 없음은 null', () => {
    expect(toMyItem({ itemId: 1, name: 'a', pricePerDay: 0, deposit: 0 })).toMatchObject({
      thumbnailUrl: null,
      viewCount: 0,
      wishCount: 0,
      chatCount: 0,
      upcomingRentalCount: 0,
    });
  });
});
