import { describe, expect, it } from 'vitest';

import {
  compareLatest,
  decodeCursor,
  encodeCursor,
  listFeed,
  parseFeedQuery,
  parseSearchQuery,
  searchMatches,
  searchPage,
} from './itemLogic';

const TODAY = '2026-10-30';

function item(itemId, patch = {}) {
  return {
    itemId,
    name: `물품 ${itemId}`,
    description: '설명',
    categoryId: 1,
    regionId: 1101,
    pricePerDay: 1000,
    deposit: 0,
    bumpedAt: '2026-10-29T10:00:00',
    isDeleted: false,
    availablePeriods: [{ startDate: '2026-10-30', endDate: '2026-12-31' }],
    ...patch,
  };
}

function query(text) {
  return new URLSearchParams(text);
}

describe('커서', () => {
  it('(bumpedAt, id)를 인코딩하고 되돌린다', () => {
    const cursor = encodeCursor({ bumpedAt: '2026-10-29T10:00:00', itemId: 7 });
    expect(decodeCursor(cursor)).toEqual({ bumpedAt: '2026-10-29T10:00:00', itemId: 7 });
  });

  it('깨진 커서는 null', () => {
    expect(decodeCursor('###')).toBeNull();
    expect(decodeCursor(btoa('abc|1'))).toBeNull();
    expect(decodeCursor(btoa('2026-10-29T10:00:00|x'))).toBeNull();
  });
});

describe('compareLatest', () => {
  it('bumpedAt 내림차순, 같으면 id 내림차순', () => {
    const items = [
      item(1, { bumpedAt: '2026-10-29T09:00:00' }),
      item(2, { bumpedAt: '2026-10-29T10:00:00' }),
      item(3, { bumpedAt: '2026-10-29T10:00:00' }),
    ];
    expect(items.sort(compareLatest).map((i) => i.itemId)).toEqual([3, 2, 1]);
  });
});

describe('parseFeedQuery', () => {
  it('regionId가 없으면 400 details regionId', () => {
    const result = parseFeedQuery(query(''));
    expect(result.errors.map((e) => e.field)).toEqual(['regionId']);
  });

  it('숫자가 아닌 regionId·categoryId·깨진 cursor를 각각 알린다', () => {
    const result = parseFeedQuery(query('regionId=abc&categoryId=x&cursor=@@'));
    expect(result.errors.map((e) => e.field)).toEqual(['regionId', 'categoryId', 'cursor']);
  });

  it('정상 값은 query로', () => {
    expect(parseFeedQuery(query('regionId=1101&categoryId=2')).query).toEqual({
      regionId: 1101,
      categoryId: 2,
      cursor: null,
    });
  });
});

describe('listFeed', () => {
  // 같은 bumpedAt이 여럿인 경계: id 25~19가 모두 같은 시각
  const items = Array.from({ length: 25 }, (_, index) =>
    item(index + 1, {
      bumpedAt:
        index >= 18 ? '2026-10-29T10:00:00' : `2026-10-28T${String(index).padStart(2, '0')}:00:00`,
    }),
  );

  it('지정 동네·카테고리만, 삭제된 물품은 제외', () => {
    const mixed = [
      item(1),
      item(2, { regionId: 1102 }),
      item(3, { categoryId: 2 }),
      item(4, { isDeleted: true }),
    ];
    expect(
      listFeed(mixed, { regionId: 1101, categoryId: null, cursor: null }).items.map(
        (i) => i.itemId,
      ),
    ).toEqual([3, 1]);
    expect(
      listFeed(mixed, { regionId: 1101, categoryId: 2, cursor: null }).items.map((i) => i.itemId),
    ).toEqual([3]);
  });

  it('첫 페이지는 size건, hasNext·nextCursor', () => {
    const page = listFeed(items, { regionId: 1101, categoryId: null, cursor: null }, 10);
    expect(page.items).toHaveLength(10);
    expect(page.hasNext).toBe(true);
    expect(decodeCursor(page.nextCursor)).toEqual({
      bumpedAt: page.items[9].bumpedAt,
      itemId: page.items[9].itemId,
    });
  });

  it('같은 bumpedAt 경계에서도 빠지거나 겹치지 않고 끝까지 이어진다', () => {
    const seen = [];
    let cursor = null;
    let guard = 0;
    for (;;) {
      const page = listFeed(items, { regionId: 1101, categoryId: null, cursor }, 4);
      seen.push(...page.items.map((i) => i.itemId));
      if (!page.hasNext) {
        break;
      }
      cursor = decodeCursor(page.nextCursor);
      guard += 1;
      expect(guard).toBeLessThan(20);
    }
    expect(seen).toHaveLength(25);
    expect(new Set(seen).size).toBe(25);
    expect([...seen].sort(compareLatest)).toEqual(
      seen
        .map((id) => items.find((i) => i.itemId === id))
        .sort(compareLatest)
        .map((i) => i.itemId),
    );
  });

  it('마지막 페이지는 hasNext false, nextCursor null — 딱 size건이어도', () => {
    const exact = items.slice(0, 4);
    const page = listFeed(exact, { regionId: 1101, categoryId: null, cursor: null }, 4);
    expect(page).toMatchObject({ hasNext: false, nextCursor: null });
    expect(page.items).toHaveLength(4);
  });

  it('결과가 없으면 빈 목록', () => {
    expect(listFeed(items, { regionId: 9999, categoryId: null, cursor: null })).toEqual({
      items: [],
      hasNext: false,
      nextCursor: null,
    });
  });
});

describe('parseSearchQuery', () => {
  it('정상 조건을 읽는다', () => {
    const { query: q } = parseSearchQuery(
      query(
        'regionId=1101&keyword=%20텐트%20&categoryId=1&minPrice=1000&maxPrice=5000&startDate=2026-11-03&endDate=2026-11-05&availableOnly=true&sort=PRICE_ASC&page=2',
      ),
    );
    expect(q).toEqual({
      regionId: 1101,
      keyword: '텐트',
      categoryId: 1,
      minPrice: 1000,
      maxPrice: 5000,
      startDate: '2026-11-03',
      endDate: '2026-11-05',
      availableOnly: true,
      sort: 'PRICE_ASC',
      page: 2,
    });
  });

  it('시작일만 있으면 400 details endDate', () => {
    expect(parseSearchQuery(query('regionId=1&startDate=2026-11-03')).errors).toEqual([
      expect.objectContaining({ field: 'endDate' }),
    ]);
  });

  it('시작일 > 종료일이면 400 details endDate', () => {
    expect(
      parseSearchQuery(query('regionId=1&startDate=2026-11-05&endDate=2026-11-03')).errors,
    ).toEqual([expect.objectContaining({ field: 'endDate' })]);
  });

  it('minPrice > maxPrice면 400 details minPrice', () => {
    expect(parseSearchQuery(query('regionId=1&minPrice=5000&maxPrice=1000')).errors).toEqual([
      expect.objectContaining({ field: 'minPrice' }),
    ]);
  });

  it('regionId 없음·모르는 정렬·음수 page를 알린다', () => {
    const fields = parseSearchQuery(query('sort=DISTANCE&page=-1')).errors.map((e) => e.field);
    expect(fields).toEqual(['regionId', 'sort', 'page']);
  });
});

function search(items, text, occupying = []) {
  const { query: q, errors } = parseSearchQuery(query(`regionId=1101&${text}`));
  expect(errors).toEqual([]);
  return searchMatches(items, occupying, q, TODAY).map((i) => i.itemId);
}

describe('searchMatches', () => {
  const items = [
    item(1, {
      name: '원터치 텐트',
      description: '캠핑용',
      bumpedAt: '2026-10-27T10:00:00',
      pricePerDay: 12000,
      categoryId: 1,
    }),
    item(2, {
      name: '캠핑 의자',
      description: '텐트 옆에 두기 좋아요',
      bumpedAt: '2026-10-29T10:00:00',
      pricePerDay: 3000,
      categoryId: 1,
    }),
    item(3, {
      name: '드릴',
      description: '공구',
      bumpedAt: '2026-10-28T10:00:00',
      pricePerDay: 5000,
      categoryId: 2,
    }),
    item(4, {
      name: '텐트 팩',
      description: '',
      bumpedAt: '2026-10-26T10:00:00',
      pricePerDay: 0,
      categoryId: 1,
    }),
    item(5, { name: '삭제된 텐트', isDeleted: true }),
    item(6, { name: '다른 동네 텐트', regionId: 1102 }),
  ];

  it('키워드는 이름·설명에 부분 일치, 정확도순은 이름 일치 > 설명 일치 > 최신순', () => {
    // 이름 일치 1(10-27)·4(10-26) 중 최신 1 → 4, 그다음 설명 일치 2
    expect(search(items, 'keyword=텐트')).toEqual([1, 4, 2]);
  });

  it('삭제된 물품·다른 동네는 제외', () => {
    expect(search(items, '')).not.toContain(5);
    expect(search(items, '')).not.toContain(6);
  });

  it('최신순', () => {
    expect(search(items, 'keyword=텐트&sort=LATEST')).toEqual([2, 1, 4]);
  });

  it('낮은 가격순 — 무료(0원)가 먼저', () => {
    expect(search(items, 'keyword=텐트&sort=PRICE_ASC')).toEqual([4, 2, 1]);
  });

  it('카테고리와 가격 범위(양 끝 포함)', () => {
    expect(search(items, 'categoryId=2')).toEqual([3]);
    expect(search(items, 'minPrice=3000&maxPrice=5000&sort=LATEST')).toEqual([2, 3]);
    expect(search(items, 'minPrice=0&maxPrice=0')).toEqual([4]);
  });

  it('조건이 맞는 물품이 없으면 빈 배열', () => {
    expect(search(items, 'keyword=없는물품')).toEqual([]);
  });

  describe('기간·대여 가능만', () => {
    const periodItems = [
      item(1, { availablePeriods: [{ startDate: '2026-11-01', endDate: '2026-11-10' }] }),
      item(2, {
        availablePeriods: [
          { startDate: '2026-11-01', endDate: '2026-11-04' },
          { startDate: '2026-11-06', endDate: '2026-11-10' },
        ],
      }),
      item(3, { availablePeriods: [{ startDate: '2026-10-30', endDate: '2026-12-31' }] }),
    ];

    it('기간 전체가 가능 기간 하나에 들어가야 한다(경계 날짜 포함)', () => {
      expect(search(periodItems, 'startDate=2026-11-03&endDate=2026-11-05&sort=LATEST')).toEqual([
        3, 1,
      ]);
      expect(search(periodItems, 'startDate=2026-11-01&endDate=2026-11-10&sort=LATEST')).toEqual([
        3, 1,
      ]);
      expect(search(periodItems, 'startDate=2026-11-01&endDate=2026-11-11&sort=LATEST')).toEqual([
        3,
      ]);
    });

    it('점유 대여와 하루라도 겹치면 제외(경계 날짜도 겹침)', () => {
      const occupying = [{ itemId: 3, startDate: '2026-11-05', endDate: '2026-11-08' }];
      expect(
        search(periodItems, 'startDate=2026-11-03&endDate=2026-11-05&sort=LATEST', occupying),
      ).toEqual([1]);
      expect(
        search(periodItems, 'startDate=2026-11-01&endDate=2026-11-04&sort=LATEST', occupying),
      ).toEqual([3, 2, 1]);
    });

    it('대여 가능만(기간 없음): 오늘 가능 기간 안이고 오늘 예약이 없어야 한다', () => {
      // 1·2는 오늘(10-30)이 가능 기간 밖
      expect(search(periodItems, 'availableOnly=true')).toEqual([3]);
      const todayBooked = [{ itemId: 3, startDate: '2026-10-29', endDate: '2026-10-30' }];
      expect(search(periodItems, 'availableOnly=true', todayBooked)).toEqual([]);
    });

    it('availableOnly=false는 거르지 않는다', () => {
      expect(search(periodItems, 'availableOnly=false&sort=LATEST')).toEqual([3, 2, 1]);
    });
  });
});

describe('searchPage', () => {
  const many = Array.from({ length: 45 }, (_, index) => item(index + 1));
  function page(number) {
    const { query: q } = parseSearchQuery(query(`regionId=1101&sort=LATEST&page=${number}`));
    return searchPage(many, [], q, TODAY);
  }

  it('20건씩 자르고 totalElements·hasNext를 채운다', () => {
    expect(page(0)).toMatchObject({ page: 0, size: 20, totalElements: 45, hasNext: true });
    expect(page(0).items).toHaveLength(20);
    expect(page(2)).toMatchObject({ page: 2, totalElements: 45, hasNext: false });
    expect(page(2).items).toHaveLength(5);
  });

  it('범위를 넘은 페이지는 빈 목록', () => {
    expect(page(9)).toMatchObject({ items: [], hasNext: false });
  });
});
