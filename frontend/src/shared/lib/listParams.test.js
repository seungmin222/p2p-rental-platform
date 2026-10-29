import { describe, expect, it } from 'vitest';

import { flattenPages, getNextPageParam, normalizeTab } from './listParams';

function page(number, items, hasNext) {
  return { items, page: number, size: 20, totalElements: 99, hasNext };
}

describe('getNextPageParam', () => {
  it('hasNext면 다음 페이지 번호', () => {
    expect(getNextPageParam(page(0, [], true))).toBe(1);
    expect(getNextPageParam(page(3, [], true))).toBe(4);
  });

  it('마지막 페이지면 undefined', () => {
    expect(getNextPageParam(page(1, [], false))).toBeUndefined();
  });
});

describe('flattenPages', () => {
  it('페이지 순서대로 이어 붙인다', () => {
    const data = { pages: [page(0, [1, 2], true), page(1, [3], false)], pageParams: [0, 1] };
    expect(flattenPages(data)).toEqual([1, 2, 3]);
  });

  it('mapItem으로 항목을 바꾼다', () => {
    const data = { pages: [page(0, [{ id: 1 }], true), page(1, [{ id: 2 }], false)] };
    expect(flattenPages(data, (item) => item.id)).toEqual([1, 2]);
  });

  it('데이터가 없으면 undefined, 빈 페이지면 빈 배열', () => {
    expect(flattenPages(undefined)).toBeUndefined();
    expect(flattenPages({ pages: [page(0, [], false)] })).toEqual([]);
  });
});

describe('normalizeTab', () => {
  const tabs = { UPCOMING: {}, ONGOING: {}, DONE: {} };

  it('아는 탭은 그대로', () => {
    expect(normalizeTab('DONE', tabs, 'ONGOING')).toBe('DONE');
  });

  it('없거나 모르는 값이면 기본 탭', () => {
    expect(normalizeTab(null, tabs, 'ONGOING')).toBe('ONGOING');
    expect(normalizeTab('ALL', tabs, 'ONGOING')).toBe('ONGOING');
    expect(normalizeTab('done', tabs, 'ONGOING')).toBe('ONGOING');
    expect(normalizeTab('toString', tabs, 'ONGOING')).toBe('ONGOING');
  });
});
