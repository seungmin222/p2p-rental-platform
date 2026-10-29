import { describe, expect, it } from 'vitest';

import { getRentalSteps } from './rentalSteps';

const states = (steps) => steps.map((step) => step.state);

describe('getRentalSteps', () => {
  it('결제 켜짐이면 첫 단계 "결제", 꺼짐이면 "예약"', () => {
    expect(getRentalSteps('RESERVED', { isPaymentEnabled: true }).map((s) => s.label)).toEqual([
      '결제',
      '대여 예정',
      '대여 중',
      '반납 완료',
      '거래 완료',
    ]);
    expect(getRentalSteps('RESERVED', { isPaymentEnabled: false })[0].label).toBe('예약');
  });

  it.each([
    ['PENDING_PAYMENT', ['current', 'upcoming', 'upcoming', 'upcoming', 'upcoming']],
    ['RESERVED', ['done', 'current', 'upcoming', 'upcoming', 'upcoming']],
    ['RENTING', ['done', 'done', 'current', 'upcoming', 'upcoming']],
    ['OVERDUE', ['done', 'done', 'current', 'upcoming', 'upcoming']],
    ['RETURNED', ['done', 'done', 'done', 'current', 'upcoming']],
    ['COMPLETED', ['done', 'done', 'done', 'done', 'done']],
  ])('%s', (status, expected) => {
    expect(states(getRentalSteps(status))).toEqual(expected);
  });

  it('OVERDUE만 "대여 중" 단계가 isDanger', () => {
    expect(getRentalSteps('OVERDUE').map((s) => s.isDanger)).toEqual([
      false,
      false,
      true,
      false,
      false,
    ]);
    expect(getRentalSteps('RENTING').some((s) => s.isDanger)).toBe(false);
  });

  it('CANCELED는 스텝바 대신 취소 정보(null)', () => {
    expect(getRentalSteps('CANCELED')).toBeNull();
  });
});
