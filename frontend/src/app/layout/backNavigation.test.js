import { describe, expect, it } from 'vitest';

import { canGoBackInApp } from './backNavigation';

describe('canGoBackInApp', () => {
  it('첫 기록(idx 0)이면 false — replace로 탭만 바꿔도 idx는 0', () => {
    expect(canGoBackInApp({ usr: null, key: 'abc123', idx: 0 })).toBe(false);
  });

  it('앱 안에서 이동해 쌓인 기록이면 true', () => {
    expect(canGoBackInApp({ usr: null, key: 'def456', idx: 2 })).toBe(true);
  });

  it('idx가 없으면 false', () => {
    expect(canGoBackInApp(null)).toBe(false);
    expect(canGoBackInApp(undefined)).toBe(false);
    expect(canGoBackInApp({})).toBe(false);
  });
});
