import { describe, expect, it } from 'vitest';

import {
  estimateCancelFee,
  findInspection,
  formatDateTime,
  formatShortDate,
  formatShortRange,
  isOverdueCancelWaiver,
  mergeCalendarDays,
  overdueDays,
  toChecklist,
} from './rentalData';

describe('mergeCalendarDays', () => {
  it('여러 달을 날짜순 하나로 합치고, 아직 없는 달(undefined)은 건너뛴다', () => {
    const october = {
      month: '2026-10',
      days: [
        { date: '2026-10-31', status: 'AVAILABLE' },
        { date: '2026-10-30', status: 'BOOKED' },
      ],
    };
    const november = { month: '2026-11', days: [{ date: '2026-11-01', status: 'UNAVAILABLE' }] };

    expect(mergeCalendarDays([november, undefined, october])).toEqual([
      { date: '2026-10-30', status: 'BOOKED' },
      { date: '2026-10-31', status: 'AVAILABLE' },
      { date: '2026-11-01', status: 'UNAVAILABLE' },
    ]);
  });

  it('같은 날짜는 뒤의 응답이 이긴다', () => {
    const before = { days: [{ date: '2026-10-30', status: 'AVAILABLE' }] };
    const after = { days: [{ date: '2026-10-30', status: 'BOOKED' }] };
    expect(mergeCalendarDays([before, after])).toEqual([{ date: '2026-10-30', status: 'BOOKED' }]);
  });

  it('비어 있으면 빈 배열', () => {
    expect(mergeCalendarDays([])).toEqual([]);
  });
});

describe('findInspection', () => {
  const handover = { inspectionId: 1, phase: 'HANDOVER' };
  const ret = { inspectionId: 2, phase: 'RETURN' };

  it('phase로 고른다', () => {
    expect(findInspection([handover, ret], 'HANDOVER')).toBe(handover);
    expect(findInspection([handover, ret], 'RETURN')).toBe(ret);
  });

  it('기록이 없으면 null, 아직 못 불러왔으면 undefined', () => {
    expect(findInspection([handover], 'RETURN')).toBeNull();
    expect(findInspection(undefined, 'RETURN')).toBeUndefined();
  });
});

describe('overdueDays', () => {
  it.each([
    ['2026-10-29', '2026-10-28', 0], // 예정일 전
    ['2026-10-29', '2026-10-29', 0], // 예정일 당일
    ['2026-10-29', '2026-10-30', 1],
    ['2026-10-30', '2026-11-02', 3], // 달 넘김
  ])('endDate %s, 오늘 %s → %i일', (endDate, today, expected) => {
    expect(overdueDays(endDate, today)).toBe(expected);
  });
});

describe('isOverdueCancelWaiver', () => {
  const reserved = (overrides) => ({
    status: 'RESERVED',
    myRole: 'RENTER',
    startDate: '2026-11-03',
    previousRentalOverdue: true,
    ...overrides,
  });

  it('대여자이고 시작일 ≤ 오늘이며 앞 대여 연체면 true', () => {
    expect(isOverdueCancelWaiver(reserved(), '2026-11-03')).toBe(true);
    expect(isOverdueCancelWaiver(reserved(), '2026-11-05')).toBe(true);
  });

  it.each([
    ['시작일 전', reserved(), '2026-11-02'],
    ['제공자', reserved({ myRole: 'OWNER' }), '2026-11-03'],
    ['앞 대여 연체 아님', reserved({ previousRentalOverdue: false }), '2026-11-03'],
    ['RESERVED 아님', reserved({ status: 'RENTING' }), '2026-11-03'],
  ])('%s면 false', (_, rental, today) => {
    expect(isOverdueCancelWaiver(rental, today)).toBe(false);
  });
});

describe('estimateCancelFee', () => {
  const reserved = (overrides) => ({
    status: 'RESERVED',
    myRole: 'RENTER',
    startDate: '2026-11-03',
    rentalFee: 45000,
    ...overrides,
  });

  it.each([
    ['2026-10-31', 0], // 3일 전: 무료
    ['2026-11-01', 13500], // 2일 전: 30%
    ['2026-11-02', 13500], // 전날
    ['2026-11-03', 0], // 당일: 단독 취소 대상 아님
  ])('대여자, 오늘 %s → %i원', (today, expected) => {
    expect(estimateCancelFee(reserved(), today)).toBe(expected);
  });

  it('원 미만 절사', () => {
    expect(estimateCancelFee(reserved({ rentalFee: 10001 }), '2026-11-02')).toBe(3000);
  });

  it('제공자 취소·결제 대기는 수수료 없음', () => {
    expect(estimateCancelFee(reserved({ myRole: 'OWNER' }), '2026-11-02')).toBe(0);
    expect(estimateCancelFee(reserved({ status: 'PENDING_PAYMENT' }), '2026-11-02')).toBe(0);
  });
});

describe('toChecklist', () => {
  it('5개 키를 모두 boolean으로 채운다(고르지 않은 값은 false)', () => {
    expect(
      toChecklist({ NO_EXTERIOR_DAMAGE: true, ALL_PARTS_INCLUDED: false, WORKS_NORMALLY: null }),
    ).toEqual({
      NO_EXTERIOR_DAMAGE: true,
      ALL_PARTS_INCLUDED: false,
      NO_CONTAMINATION: false,
      WORKS_NORMALLY: false,
      CONSUMABLES_RESTORED: false,
    });
  });
});

describe('날짜 표기', () => {
  it('formatShortRange', () => {
    expect(formatShortRange('2026-10-09', '2026-10-11')).toBe('10/9 ~ 10/11');
    expect(formatShortRange('2026-10-09', '2026-10-09')).toBe('10/9');
  });

  it('서버 일시(오프셋 없음)는 서울 시각으로', () => {
    expect(formatDateTime('2026-10-31T09:05:00')).toBe('10월 31일 09:05');
    expect(formatDateTime('2026-10-30T15:30:00Z')).toBe('10월 31일 00:30');
    expect(formatShortDate('2026-10-09T18:00:00')).toBe('10/9');
    expect(formatDateTime(null)).toBe('');
  });
});
