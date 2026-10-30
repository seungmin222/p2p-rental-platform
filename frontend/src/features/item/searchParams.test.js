import { describe, expect, it } from 'vitest';

import {
  countSheetFilters,
  hasSearchCondition,
  parsePriceInput,
  parseSearchParams,
  toApiParams,
  toSearchParams,
  validatePriceRange,
} from './searchParams';

function parse(query) {
  return parseSearchParams(new URLSearchParams(query));
}

const EMPTY = {
  keyword: '',
  categoryId: null,
  sort: 'ACCURACY',
  minPrice: null,
  maxPrice: null,
  startDate: null,
  endDate: null,
  availableOnly: false,
};

describe('parseSearchParams', () => {
  it('아무것도 없으면 비어 있고 정렬은 기본 ACCURACY', () => {
    expect(parse('')).toEqual(EMPTY);
  });

  it('모든 조건을 읽는다', () => {
    expect(
      parse(
        'q=텐트&category=1&sort=PRICE_ASC&minPrice=1000&maxPrice=20000&startDate=2026-11-03&endDate=2026-11-05&availableOnly=true',
      ),
    ).toEqual({
      keyword: '텐트',
      categoryId: 1,
      sort: 'PRICE_ASC',
      minPrice: 1000,
      maxPrice: 20000,
      startDate: '2026-11-03',
      endDate: '2026-11-05',
      availableOnly: true,
    });
  });

  it('키워드는 앞뒤 공백을 지운다 — 공백뿐이면 빈 키워드', () => {
    expect(parse('q=%20%20텐트%20').keyword).toBe('텐트');
    expect(parse('q=%20%20').keyword).toBe('');
  });

  it('모르는 정렬·카테고리·가격은 없는 것으로 본다', () => {
    expect(parse('sort=DISTANCE&category=abc&minPrice=-1&maxPrice=1.5')).toEqual(EMPTY);
    expect(parse('category=0').categoryId).toBeNull();
  });

  it('가격은 일 대여료 상한(1,000,000원)까지, 0원도 받는다', () => {
    expect(parse('minPrice=0&maxPrice=1000000')).toMatchObject({
      minPrice: 0,
      maxPrice: 1_000_000,
    });
    expect(parse('maxPrice=1000001').maxPrice).toBeNull();
  });

  it('최소가 최대보다 크면 둘 다 버린다(서버는 400)', () => {
    expect(parse('minPrice=5000&maxPrice=1000')).toMatchObject({ minPrice: null, maxPrice: null });
    expect(parse('minPrice=1000&maxPrice=1000')).toMatchObject({ minPrice: 1000, maxPrice: 1000 });
  });

  it('기간은 둘 다 있고 시작 ≤ 종료일 때만 쓴다', () => {
    expect(parse('startDate=2026-11-03')).toMatchObject({ startDate: null, endDate: null });
    expect(parse('endDate=2026-11-03')).toMatchObject({ startDate: null, endDate: null });
    expect(parse('startDate=2026-11-05&endDate=2026-11-03')).toMatchObject({
      startDate: null,
      endDate: null,
    });
    expect(parse('startDate=2026-11-03&endDate=2026-11-03')).toMatchObject({
      startDate: '2026-11-03',
      endDate: '2026-11-03',
    });
  });

  it('없는 날짜는 거른다', () => {
    expect(parse('startDate=2026-02-30&endDate=2026-03-02')).toMatchObject({ startDate: null });
    expect(parse('startDate=2026-13-01&endDate=2026-13-02')).toMatchObject({ startDate: null });
  });

  it('availableOnly는 true일 때만 켠다', () => {
    expect(parse('availableOnly=1').availableOnly).toBe(false);
    expect(parse('availableOnly=true').availableOnly).toBe(true);
  });
});

describe('toSearchParams', () => {
  it('기본값은 넣지 않는다', () => {
    expect(toSearchParams(EMPTY).toString()).toBe('');
  });

  it('읽은 조건을 다시 쓰면 같은 조건이 된다', () => {
    const filters = {
      keyword: '텐트',
      categoryId: 1,
      sort: 'LATEST',
      minPrice: 0,
      maxPrice: 20000,
      startDate: '2026-11-03',
      endDate: '2026-11-05',
      availableOnly: true,
    };
    expect(parseSearchParams(toSearchParams(filters))).toEqual(filters);
  });

  it('기간은 시작·종료가 둘 다 있을 때만 쓴다', () => {
    expect(toSearchParams({ ...EMPTY, startDate: '2026-11-03' }).toString()).toBe('');
  });
});

describe('hasSearchCondition', () => {
  it('아무 조건도 없으면 조회하지 않는다', () => {
    expect(hasSearchCondition(EMPTY)).toBe(false);
  });

  it('정렬만 바꾼 것은 조건이 아니다', () => {
    expect(hasSearchCondition({ ...EMPTY, sort: 'LATEST' })).toBe(false);
  });

  it.each([
    ['키워드', { keyword: '텐트' }],
    ['카테고리', { categoryId: 1 }],
    ['최소 가격 0원', { minPrice: 0 }],
    ['최대 가격', { maxPrice: 1000 }],
    ['기간', { startDate: '2026-11-03', endDate: '2026-11-05' }],
    ['대여 가능만', { availableOnly: true }],
  ])('%s만 있어도 조회한다', (_name, patch) => {
    expect(hasSearchCondition({ ...EMPTY, ...patch })).toBe(true);
  });
});

describe('countSheetFilters', () => {
  it('정렬(기본 아님)·가격·기간·대여 가능만을 센다 — 카테고리·키워드는 세지 않는다', () => {
    expect(countSheetFilters({ ...EMPTY, keyword: '텐트', categoryId: 1 })).toBe(0);
    expect(countSheetFilters({ ...EMPTY, sort: 'LATEST' })).toBe(1);
    expect(countSheetFilters({ ...EMPTY, minPrice: 1000, maxPrice: 2000 })).toBe(1);
    expect(
      countSheetFilters({
        ...EMPTY,
        sort: 'PRICE_ASC',
        maxPrice: 2000,
        startDate: '2026-11-03',
        endDate: '2026-11-05',
        availableOnly: true,
      }),
    ).toBe(4);
  });
});

describe('toApiParams', () => {
  it('regionId만 있고 나머지는 보내지 않는다', () => {
    expect(toApiParams(EMPTY, 1101)).toEqual({ regionId: 1101 });
  });

  it('openapi GET /api/items 파라미터 이름으로 옮긴다', () => {
    expect(
      toApiParams(
        {
          keyword: '텐트',
          categoryId: 1,
          sort: 'PRICE_ASC',
          minPrice: 1000,
          maxPrice: 20000,
          startDate: '2026-11-03',
          endDate: '2026-11-05',
          availableOnly: true,
        },
        1101,
      ),
    ).toEqual({
      regionId: 1101,
      keyword: '텐트',
      categoryId: 1,
      sort: 'PRICE_ASC',
      minPrice: 1000,
      maxPrice: 20000,
      startDate: '2026-11-03',
      endDate: '2026-11-05',
      availableOnly: true,
    });
  });

  it('최소 가격 0원은 보낸다(null과 구분)', () => {
    expect(toApiParams({ ...EMPTY, minPrice: 0 }, 1101)).toEqual({ regionId: 1101, minPrice: 0 });
  });
});

describe('parsePriceInput', () => {
  it('비우면 조건 없음', () => {
    expect(parsePriceInput('')).toEqual({ value: null, error: null });
    expect(parsePriceInput('  ')).toEqual({ value: null, error: null });
  });

  it('숫자를 읽는다 — 0원·쉼표·상한 포함', () => {
    expect(parsePriceInput('0').value).toBe(0);
    expect(parsePriceInput('12,000').value).toBe(12000);
    expect(parsePriceInput('1000000').value).toBe(1_000_000);
  });

  it('숫자가 아니거나 상한을 넘으면 오류', () => {
    expect(parsePriceInput('abc').error).toMatch('0 ~ 1,000,000원');
    expect(parsePriceInput('-1').error).not.toBeNull();
    expect(parsePriceInput('1.5').error).not.toBeNull();
    expect(parsePriceInput('1000001').error).not.toBeNull();
  });
});

describe('validatePriceRange', () => {
  it('둘 다 비어 있어도 유효', () => {
    expect(validatePriceRange('', '')).toMatchObject({
      minPrice: null,
      maxPrice: null,
      isValid: true,
    });
  });

  it('최소 ≤ 최대면 유효(같아도)', () => {
    expect(validatePriceRange('1000', '1000')).toMatchObject({
      minPrice: 1000,
      maxPrice: 1000,
      isValid: true,
    });
  });

  it('최소 > 최대면 최대 칸에 오류', () => {
    const result = validatePriceRange('5000', '1000');
    expect(result.isValid).toBe(false);
    expect(result.maxError).not.toBeNull();
    expect(result.minError).toBeNull();
  });

  it('한쪽만 잘못돼도 그 칸에 오류', () => {
    const result = validatePriceRange('x', '1000');
    expect(result.isValid).toBe(false);
    expect(result.minError).not.toBeNull();
  });
});
