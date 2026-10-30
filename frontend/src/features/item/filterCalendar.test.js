import { describe, expect, it } from 'vitest';

import { selectDate, toStatusMap } from '../../shared/ui/calendarSelection';
import { buildFilterDays, buildFilterDaysInRange } from './filterCalendar';

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

describe('buildFilterDaysInRange', () => {
  const TODAY = '2026-10-30';

  it('시작 달부터 끝 달까지 합친다', () => {
    const days = buildFilterDaysInRange('2026-10', '2026-11', TODAY);
    expect(days).toHaveLength(61);
    expect(days[0].date).toBe('2026-10-01');
    expect(days[60].date).toBe('2026-11-30');
  });

  it('달을 넘긴 기간을 고를 수 있다(10/31 → 11/2)', () => {
    const options = {
      statusByDate: toStatusMap(buildFilterDaysInRange('2026-10', '2026-11', TODAY)),
      minDays: 1,
      maxDays: 30,
    };
    const start = selectDate(null, '2026-10-31', options);
    expect(start.reason).toBeUndefined();

    const range = selectDate(start.value, '2026-11-02', options);

    expect(range.reason).toBeUndefined();
    expect(range.value).toEqual({ startDate: '2026-10-31', endDate: '2026-11-02' });
  });

  it('최대 일수는 달을 넘겨도 적용된다', () => {
    const options = {
      statusByDate: toStatusMap(buildFilterDaysInRange('2026-10', '2026-12', TODAY)),
      minDays: 1,
      maxDays: 30,
    };
    const start = selectDate(null, '2026-11-01', options);
    expect(selectDate(start.value, '2026-12-01', options).reason).toBe('TOO_LONG');
    expect(selectDate(start.value, '2026-11-30', options).reason).toBeUndefined();
  });
});
