import { describe, expect, it } from 'vitest';

import { isCardListKey, patchCardWishCount, patchDetailWish } from './wishCache';

describe('patchCardWishCount', () => {
  const pages = {
    pages: [
      { items: [{ itemId: 1, wishCount: 3 }], hasNext: true },
      { items: [{ itemId: 2, wishCount: 0 }], hasNext: false },
    ],
    pageParams: [0, 1],
  };

  it('무한 목록에서 그 물품의 관심 수만 바꾸고 다른 페이지는 그대로 둔다', () => {
    const next = patchCardWishCount(pages, 2, 1);

    expect(next.pages[1].items[0]).toEqual({ itemId: 2, wishCount: 1 });
    expect(next.pages[0]).toBe(pages.pages[0]);
    expect(next.pageParams).toBe(pages.pageParams);
  });

  it('배열(최근 본 상품)도 고친다', () => {
    const items = [
      { itemId: 1, wishCount: 3 },
      { itemId: 2, wishCount: 5 },
    ];

    expect(patchCardWishCount(items, 1, 4)).toEqual([
      { itemId: 1, wishCount: 4 },
      { itemId: 2, wishCount: 5 },
    ]);
  });

  it('없는 물품·같은 값·모르는 모양이면 같은 참조를 돌려준다', () => {
    expect(patchCardWishCount(pages, 9, 1)).toBe(pages);
    expect(patchCardWishCount(pages, 1, 3)).toBe(pages);
    const unknown = { itemId: 1 };
    expect(patchCardWishCount(unknown, 1, 2)).toBe(unknown);
    expect(patchCardWishCount(undefined, 1, 2)).toBeUndefined();
  });
});

describe('patchDetailWish', () => {
  it('응답이면 isWished·wishCount를, 낙관적 업데이트면 isWished만 바꾼다', () => {
    const detail = { itemId: 1, isWished: false, wishCount: 3, name: '텐트' };

    expect(patchDetailWish(detail, { isWished: true, wishCount: 4 })).toEqual({
      ...detail,
      isWished: true,
      wishCount: 4,
    });
    expect(patchDetailWish(detail, { isWished: true })).toEqual({ ...detail, isWished: true });
    expect(patchDetailWish(undefined, { isWished: true })).toBeUndefined();
  });
});

describe('isCardListKey', () => {
  it.each([
    [['items', 'feed', { regionId: 1 }], true],
    [['items', 'search', { regionId: 1 }], true],
    [['recent-items', 'me'], true],
    [['wishes', 'me'], true],
    [['members', 3, 'items'], true],
    [['items', 1], false],
    [['items', 'me', { tab: 'AVAILABLE' }], false],
    [['members', 'me'], false],
    [['members', 3, 'reviews'], false],
    [['rentals', 'me'], false],
  ])('%j → %s', (key, expected) => {
    expect(isCardListKey(key)).toBe(expected);
  });
});
