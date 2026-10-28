import { describe, expect, it } from 'vitest';

import { addDays, compareDate } from '../lib/date';
import {
  DAY_STATUS,
  rangePosition,
  SELECT_REJECT_REASON,
  selectDate,
  toStatusMap,
} from './calendarSelection';

const { AVAILABLE, BOOKED, UNAVAILABLE } = DAY_STATUS;

/** from~to 전부 AVAILABLE, overrides로 일부 상태 변경 */
function makeDays(from, to, overrides = {}) {
  const days = [];
  for (let d = from; compareDate(d, to) <= 0; d = addDays(d, 1)) {
    days.push({ date: d, status: overrides[d] ?? AVAILABLE });
  }
  return days;
}

function options(days, { minDays = 1, maxDays = 30 } = {}) {
  return { statusByDate: toStatusMap(days), minDays, maxDays };
}

const start = (startDate) => ({ startDate, endDate: null });
const range = (startDate, endDate) => ({ startDate, endDate });

describe('selectDate', () => {
  // 10월·11월 두 달을 합친 days(호출부가 캐시에서 합쳐 넘기는 모양)
  const twoMonths = makeDays('2026-10-01', '2026-11-30', { '2026-10-14': BOOKED });
  const opts = options(twoMonths);

  it('선택이 없으면 새 시작일', () => {
    expect(selectDate(null, '2026-10-05', opts)).toEqual({ value: start('2026-10-05') });
  });

  it('같은 날을 두 번 누르면 1일 범위', () => {
    expect(selectDate(start('2026-10-05'), '2026-10-05', opts)).toEqual({
      value: range('2026-10-05', '2026-10-05'),
    });
  });

  it('minDays가 1보다 크면 같은 날은 TOO_SHORT, 시작일 유지', () => {
    expect(
      selectDate(start('2026-10-05'), '2026-10-05', options(twoMonths, { minDays: 2 })),
    ).toEqual({ value: start('2026-10-05'), reason: SELECT_REJECT_REASON.TOO_SHORT });
  });

  it('앞선 날을 누르면 시작일을 바꾼다', () => {
    expect(selectDate(start('2026-10-10'), '2026-10-06', opts)).toEqual({
      value: start('2026-10-06'),
    });
  });

  it('30일은 허용, 31일은 TOO_LONG + 누른 날이 새 시작일', () => {
    expect(selectDate(start('2026-10-15'), '2026-11-13', opts)).toEqual({
      value: range('2026-10-15', '2026-11-13'),
    });
    expect(selectDate(start('2026-10-15'), '2026-11-14', opts)).toEqual({
      value: start('2026-11-14'),
      reason: SELECT_REJECT_REASON.TOO_LONG,
    });
  });

  it('사이에 BOOKED가 있으면 거부 + 누른 날이 새 시작일, 이어서 범위 완성', () => {
    const rejected = selectDate(start('2026-10-12'), '2026-10-16', opts);
    expect(rejected).toEqual({
      value: start('2026-10-16'),
      reason: SELECT_REJECT_REASON.RANGE_INCLUDES_UNAVAILABLE,
    });
    expect(selectDate(rejected.value, '2026-10-18', opts)).toEqual({
      value: range('2026-10-16', '2026-10-18'),
    });
  });

  it('BOOKED를 누르면 기존 시작일 유지', () => {
    expect(selectDate(start('2026-10-12'), '2026-10-14', opts)).toEqual({
      value: start('2026-10-12'),
      reason: SELECT_REJECT_REASON.DAY_NOT_AVAILABLE,
    });
  });

  it('BOOKED 바로 앞까지는 허용', () => {
    expect(selectDate(start('2026-10-12'), '2026-10-13', opts)).toEqual({
      value: range('2026-10-12', '2026-10-13'),
    });
  });

  it('달을 넘는 범위', () => {
    expect(selectDate(start('2026-10-30'), '2026-11-02', opts)).toEqual({
      value: range('2026-10-30', '2026-11-02'),
    });
  });

  it('days에 없는 달은 UNAVAILABLE로 본다', () => {
    const october = makeDays('2026-10-01', '2026-10-31');
    // 끝 날짜가 days에 없음
    expect(selectDate(start('2026-10-30'), '2026-11-02', options(october))).toEqual({
      value: start('2026-10-30'),
      reason: SELECT_REJECT_REASON.DAY_NOT_AVAILABLE,
    });
    // 끝 날짜만 있고 사이가 비어 있음 → 범위 거부라 누른 날이 새 시작일
    const gap = [...october, { date: '2026-11-02', status: AVAILABLE }];
    expect(selectDate(start('2026-10-30'), '2026-11-02', options(gap))).toEqual({
      value: start('2026-11-02'),
      reason: SELECT_REJECT_REASON.RANGE_INCLUDES_UNAVAILABLE,
    });
  });

  it('AVAILABLE이 아닌 날을 누르면 DAY_NOT_AVAILABLE, 값은 그대로', () => {
    expect(selectDate(null, '2026-10-14', opts)).toEqual({
      value: null,
      reason: SELECT_REJECT_REASON.DAY_NOT_AVAILABLE,
    });
    const days = makeDays('2026-10-01', '2026-10-31', { '2026-10-02': UNAVAILABLE });
    expect(selectDate(range('2026-10-05', '2026-10-06'), '2026-10-02', options(days))).toEqual({
      value: range('2026-10-05', '2026-10-06'),
      reason: SELECT_REJECT_REASON.DAY_NOT_AVAILABLE,
    });
  });

  it('범위가 완성된 뒤 누르면 새 시작일', () => {
    expect(selectDate(range('2026-10-05', '2026-10-08'), '2026-10-03', opts)).toEqual({
      value: start('2026-10-03'),
    });
    expect(selectDate(range('2026-10-05', '2026-10-08'), '2026-10-20', opts)).toEqual({
      value: start('2026-10-20'),
    });
  });

  it('고른 뒤 시작일이 BOOKED로 바뀌었으면 범위를 거부하고 누른 날부터 다시', () => {
    const refetched = makeDays('2026-10-01', '2026-10-31', { '2026-10-05': BOOKED });
    expect(selectDate(start('2026-10-05'), '2026-10-07', options(refetched))).toEqual({
      value: start('2026-10-07'),
      reason: SELECT_REJECT_REASON.RANGE_INCLUDES_UNAVAILABLE,
    });
  });
});

describe('rangePosition', () => {
  it('시작일만 있거나 1일 범위면 single', () => {
    expect(rangePosition(start('2026-10-05'), '2026-10-05')).toBe('single');
    expect(rangePosition(start('2026-10-05'), '2026-10-06')).toBe(null);
    expect(rangePosition(range('2026-10-05', '2026-10-05'), '2026-10-05')).toBe('single');
  });

  it('범위의 시작·사이·끝', () => {
    const value = range('2026-10-30', '2026-11-02');
    expect(rangePosition(value, '2026-10-29')).toBe(null);
    expect(rangePosition(value, '2026-10-30')).toBe('start');
    expect(rangePosition(value, '2026-11-01')).toBe('middle');
    expect(rangePosition(value, '2026-11-02')).toBe('end');
    expect(rangePosition(value, '2026-11-03')).toBe(null);
    expect(rangePosition(null, '2026-10-30')).toBe(null);
  });
});
