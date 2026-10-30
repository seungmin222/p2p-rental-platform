import { describe, expect, it } from 'vitest';

import { toItemCard, toItemDetail, toItemSummary, toMyItem } from './api';

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

describe('toItemCard', () => {
  const summary = {
    itemId: 201,
    name: '원터치 텐트',
    thumbnailUrl: '/files/t.jpg',
    regionName: '송도1동',
    bumpedAt: '2026-10-30T09:00:00',
    pricePerDay: 12000,
    deposit: 60000,
    isRenting: true,
    wishCount: 3,
    chatCount: 2,
  };

  it('카드가 쓰는 필드만 옮긴다 — 관심·채팅 수는 2순위라 옮기지 않는다', () => {
    expect(toItemCard(summary)).toEqual({
      itemId: 201,
      name: '원터치 텐트',
      thumbnailUrl: '/files/t.jpg',
      regionName: '송도1동',
      bumpedAt: '2026-10-30T09:00:00',
      pricePerDay: 12000,
      deposit: 60000,
      isRenting: true,
    });
  });

  it('썸네일이 없으면 null, isRenting이 없으면 false', () => {
    expect(toItemCard({ ...summary, thumbnailUrl: undefined, isRenting: undefined })).toMatchObject(
      {
        thumbnailUrl: null,
        isRenting: false,
      },
    );
  });
});

describe('toItemDetail', () => {
  const DESCRIPTION = ['첫 줄', '둘째 줄'].join(String.fromCharCode(10));
  const detail = {
    itemId: 101,
    name: '4인용 거실형 텐트',
    images: [
      { imageId: 1, url: '/files/1.jpg', thumbnailUrl: '/files/1_thumb.jpg' },
      { imageId: 2, url: '/files/2.jpg', thumbnailUrl: '/files/2_thumb.jpg' },
    ],
    category: { categoryId: 1, name: '캠핑·아웃도어' },
    condition: 'LIGHTLY_USED',
    description: DESCRIPTION,
    pricePerDay: 15000,
    deposit: 50000,
    address: '인천 연수구 연수동 청능대로 105',
    latitude: 37.4,
    longitude: 126.6,
    regionName: '연수1동',
    status: 'AVAILABLE',
    bumpedAt: '2026-10-27T10:00:00',
    viewCount: 312,
    wishCount: 28,
    chatCount: 11,
    isWished: false,
    owner: { memberId: 2, nickname: '캠핑조아', profileImageUrl: null, regionName: '연수1동' },
    isOwner: false,
  };

  it('화면이 쓰는 필드만 옮긴다 — 좌표·조회·관심·채팅 수는 쓰지 않는다', () => {
    const result = toItemDetail(detail);
    expect(result).toEqual({
      itemId: 101,
      name: '4인용 거실형 텐트',
      images: [
        { imageId: 1, url: '/files/1.jpg' },
        { imageId: 2, url: '/files/2.jpg' },
      ],
      categoryName: '캠핑·아웃도어',
      condition: 'LIGHTLY_USED',
      description: DESCRIPTION,
      pricePerDay: 15000,
      deposit: 50000,
      address: '인천 연수구 연수동 청능대로 105',
      regionName: '연수1동',
      bumpedAt: '2026-10-27T10:00:00',
      owner: { memberId: 2, nickname: '캠핑조아', profileImageUrl: null, regionName: '연수1동' },
      isOwner: false,
    });
  });

  it('이미지가 없으면 빈 배열, isOwner는 true일 때만 true', () => {
    expect(toItemDetail({ ...detail, images: undefined }).images).toEqual([]);
    expect(toItemDetail({ ...detail, isOwner: true }).isOwner).toBe(true);
    expect(toItemDetail({ ...detail, isOwner: undefined }).isOwner).toBe(false);
  });
});
