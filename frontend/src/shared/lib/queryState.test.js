import { describe, expect, it } from 'vitest';

import { getRefetchErrorKey, isInitialLoadError } from './queryState';

const loaded = { isError: false, data: { id: 1 }, errorUpdatedAt: 0 };
const firstLoadFailed = { isError: true, data: undefined, errorUpdatedAt: 100 };
const refetchFailed = (at) => ({ isError: true, data: { id: 1 }, errorUpdatedAt: at });

describe('isInitialLoadError', () => {
  it('데이터 없이 실패했을 때만 true', () => {
    expect(isInitialLoadError(firstLoadFailed)).toBe(true);
    expect(isInitialLoadError(refetchFailed(100))).toBe(false);
    expect(isInitialLoadError(loaded)).toBe(false);
    expect(isInitialLoadError({ isError: false, data: undefined })).toBe(false);
    expect(isInitialLoadError(undefined)).toBe(false);
  });

  it('데이터가 null(빈 응답)이어도 데이터가 있는 것으로 본다', () => {
    expect(isInitialLoadError({ isError: true, data: null })).toBe(false);
  });
});

describe('getRefetchErrorKey', () => {
  it('재조회 실패가 없으면 null', () => {
    expect(getRefetchErrorKey([])).toBeNull();
    expect(getRefetchErrorKey([loaded, firstLoadFailed, undefined])).toBeNull();
  });

  it('데이터가 있는 실패의 errorUpdatedAt, 여럿이면 가장 최근 것', () => {
    expect(getRefetchErrorKey([loaded, refetchFailed(200)])).toBe(200);
    expect(getRefetchErrorKey([refetchFailed(300), refetchFailed(200)])).toBe(300);
  });

  it('같은 실패를 다시 계산해도 같은 값(토스트가 반복되지 않음)', () => {
    const query = refetchFailed(200);
    expect(getRefetchErrorKey([query])).toBe(getRefetchErrorKey([{ ...query }]));
  });

  it('다시 실패하면 값이 바뀐다', () => {
    expect(getRefetchErrorKey([refetchFailed(400)])).not.toBe(
      getRefetchErrorKey([refetchFailed(200)]),
    );
  });
});
