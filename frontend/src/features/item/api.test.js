import { describe, expect, it } from 'vitest';

import { toItemSummary, toMyItem } from './api';

describe('toItemSummary', () => {
  const detail = {
    itemId: 101,
    name: '4인용 거실형 텐트',
    images: [
      { imageId: 1, url: '/files/1.jpg', thumbnailUrl: '/files/1_thumb.jpg' },
      { imageId: 2, url: '/files/2.jpg', thumbnailUrl: '/files/2_thumb.jpg' },
    ],
    pricePerDay: 15000,
    deposit: 50000,
    owner: { memberId: 2, nickname: '캠핑조아', profileImageUrl: null, regionName: '연수동' },
    regionName: '송도동',
    isOwner: false,
  };

  it('대표 이미지(0번)의 썸네일과 제공자 정보를 꺼낸다', () => {
    expect(toItemSummary(detail)).toEqual({
      itemId: 101,
      name: '4인용 거실형 텐트',
      thumbnailUrl: '/files/1_thumb.jpg',
      pricePerDay: 15000,
      deposit: 50000,
      ownerNickname: '캠핑조아',
      regionName: '연수동',
      isOwner: false,
    });
  });

  it('이미지가 없으면 썸네일 null', () => {
    expect(toItemSummary({ ...detail, images: [] })).toMatchObject({ thumbnailUrl: null });
  });
});

describe('toMyItem', () => {
  it('MyItemResponse에서 카드가 쓰는 필드만 옮긴다', () => {
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
      isRenting: false,
      regionName: '송도동',
      status: 'AVAILABLE',
      bumpedAt: '2026-10-20T10:00:00',
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
