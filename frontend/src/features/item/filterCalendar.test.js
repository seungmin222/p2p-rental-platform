import { describe, expect, it } from 'vitest';

import { buildFilterDays } from './filterCalendar';

describe('buildFilterDays', () => {
  it('오늘부터는 선택 가능, 지난 날짜는 선택 불가', () => {
    const days = buildFilterDays('2026-10', '2026-10-30');
    expect(days).toHaveLength(31);
    expect(days.find((day) => day.date === '2026-10-29').status).toBe('UNAVAILABLE');
    expect(days.find((day) => day.date === '2026-10-30').status).toBe('AVAILABLE');
    expect(days.find((day) => day.date === '2026-10-31').status).toBe('AVAILABLE');
  });

  it('다음 달은 모두 선택 가능', () => {
    expect(
      buildFilterDays('2026-11', '2026-10-30').every((day) => day.status === 'AVAILABLE'),
    ).toBe(true);
  });

  it('지난 달은 모두 선택 불가', () => {
    expect(
      buildFilterDays('2026-09', '2026-10-30').every((day) => day.status === 'UNAVAILABLE'),
    ).toBe(true);
  });
});
