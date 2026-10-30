import { describe, expect, it } from 'vitest';

import { parseItemId } from './itemId';

describe('parseItemId', () => {
  it('양의 정수를 읽는다 — 안전한 정수의 끝까지', () => {
    expect(parseItemId('1')).toBe(1);
    expect(parseItemId('9007199254740991')).toBe(Number.MAX_SAFE_INTEGER);
  });

  it('안전한 정수를 넘는 긴 id는 null — 다른 id나 1e+23으로 요청하지 않는다', () => {
    expect(parseItemId('9007199254740992')).toBeNull();
    expect(parseItemId('99999999999999999999999')).toBeNull();
  });

  it.each(['0', '01', '007', '-1', '1.0', '1.5', '1e3', 'abc', '', ' ', ' 1', '1 '])(
    '%j는 null',
    (value) => {
      expect(parseItemId(value)).toBeNull();
    },
  );

  it('값이 없으면 null', () => {
    expect(parseItemId(undefined)).toBeNull();
  });
});
