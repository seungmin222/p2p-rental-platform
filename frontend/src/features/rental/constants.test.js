import { describe, expect, it } from 'vitest';

import { RENTAL_STATUS, RENTAL_TABS } from './constants';

describe('RENTAL_TABS', () => {
  const tabStatuses = Object.values(RENTAL_TABS).flatMap((tab) => tab.statuses);

  it('RentalStatus 7개를 빠짐없이 덮는다', () => {
    expect(new Set(tabStatuses)).toEqual(new Set(Object.keys(RENTAL_STATUS)));
    expect(Object.keys(RENTAL_STATUS)).toHaveLength(7);
  });

  it('한 상태는 한 탭에만 속한다', () => {
    expect(tabStatuses).toHaveLength(new Set(tabStatuses).size);
  });
});
