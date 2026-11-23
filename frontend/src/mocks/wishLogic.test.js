import { describe, expect, it } from 'vitest';

import {
  checkAddWish,
  listMyWishes,
  listRecentItemIds,
  nextWishCount,
  recordView,
  setWish,
  toWishItemResponse,
  wishItemStatus,
} from './wishLogic';

describe('관심 목', () => {
  it('추가 검사: 없음·삭제 404 → 본인 물품 400', () => {
    expect(checkAddWish(null, 1)).toBe('ITEM_NOT_FOUND');
    expect(checkAddWish({ ownerId: 2, isDeleted: true }, 1)).toBe('ITEM_NOT_FOUND');
    expect(checkAddWish({ ownerId: 1, isDeleted: false }, 1)).toBe('WISH_OWN_ITEM');
    expect(checkAddWish({ ownerId: 2, isDeleted: false }, 1)).toBeNull();
  });

  it('추가·해제는 멱등이다(이미 그 상태면 changed false)', () => {
    const wishes = [{ memberId: 1, itemId: 10, wishedAt: '2026-11-22T10:00:00' }];

    expect(setWish(wishes, { memberId: 1, itemId: 10, wished: true }).changed).toBe(false);
    expect(setWish(wishes, { memberId: 1, itemId: 11, wished: false }).changed).toBe(false);
    const added = setWish(wishes, { memberId: 1, itemId: 11, wished: true, wishedAt: 'x' });
    expect(added.changed).toBe(true);
    expect(added.wishes).toHaveLength(2);
    const removed = setWish(wishes, { memberId: 1, itemId: 10, wished: false });
    expect(removed).toEqual({ wishes: [], changed: true });
  });

  it('관심 수는 0 아래로 내려가지 않는다', () => {
    expect(nextWishCount(0, false)).toBe(0);
    expect(nextWishCount(2, false)).toBe(1);
    expect(nextWishCount(2, true)).toBe(3);
  });

  it('내 관심은 최근 추가순', () => {
    const wishes = [
      { memberId: 1, itemId: 1, wishedAt: '2026-11-20T10:00:00' },
      { memberId: 2, itemId: 2, wishedAt: '2026-11-23T10:00:00' },
      { memberId: 1, itemId: 3, wishedAt: '2026-11-22T10:00:00' },
    ];

    expect(listMyWishes(wishes, 1).map((wish) => wish.itemId)).toEqual([3, 1]);
  });

  it('삭제·숨김 항목은 itemId·status·wishedAt 외 필드를 비운다', () => {
    const wish = { itemId: 7, wishedAt: '2026-11-22T10:00:00', hiddenMock: true };

    expect(wishItemStatus(null, wish)).toBe('DELETED');
    expect(wishItemStatus({ isDeleted: false }, wish)).toBe('HIDDEN');
    expect(wishItemStatus({ isDeleted: false }, { itemId: 7 })).toBe('AVAILABLE');
    expect(toWishItemResponse(wish, 'HIDDEN', null)).toEqual({
      itemId: 7,
      name: null,
      thumbnailUrl: null,
      regionName: null,
      pricePerDay: 0,
      deposit: 0,
      isRenting: false,
      wishCount: 0,
      chatCount: 0,
      status: 'HIDDEN',
      wishedAt: '2026-11-22T10:00:00',
    });
  });
});

describe('최근 본 상품 목', () => {
  it('같은 물품은 시각만 갱신하고, 회원당 최신 max건만 남긴다', () => {
    let views = [{ memberId: 2, itemId: 9, viewedAt: '2026-11-01T00:00:00' }];
    views = recordView(views, { memberId: 1, itemId: 1, viewedAt: '2026-11-20T10:00:00' }, 2);
    views = recordView(views, { memberId: 1, itemId: 2, viewedAt: '2026-11-21T10:00:00' }, 2);
    views = recordView(views, { memberId: 1, itemId: 1, viewedAt: '2026-11-22T10:00:00' }, 2);

    expect(listRecentItemIds(views, 1)).toEqual([1, 2]);

    views = recordView(views, { memberId: 1, itemId: 3, viewedAt: '2026-11-23T10:00:00' }, 2);
    expect(listRecentItemIds(views, 1)).toEqual([3, 1]);
    expect(listRecentItemIds(views, 2)).toEqual([9]);
  });
});
