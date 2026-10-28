import { describe, expect, it } from 'vitest';

import {
  formatDailyPrice,
  formatDeposit,
  formatElapsed,
  formatPrice,
  parseServerDateTime,
} from './format';

describe('가격 포맷', () => {
  it('천 단위 구분 기호와 원을 붙인다', () => {
    expect(formatPrice(12000)).toBe('12,000원');
    expect(formatPrice(1234567)).toBe('1,234,567원');
    expect(formatDailyPrice(12000)).toBe('12,000원/일');
    expect(formatDeposit(50000)).toBe('보증금 50,000원');
  });

  it('0원과 음수도 그대로 표기한다', () => {
    expect(formatPrice(0)).toBe('0원');
    expect(formatDailyPrice(0)).toBe('0원/일');
    expect(formatPrice(-3000)).toBe('-3,000원');
  });

  it('보증금 0원은 "보증금 없음"', () => {
    expect(formatDeposit(0)).toBe('보증금 없음');
  });
});

describe('서버 시각 해석', () => {
  it('오프셋이 없으면 서울(+09:00)로 본다', () => {
    expect(parseServerDateTime('2026-10-07T15:30:00').toISOString()).toBe(
      '2026-10-07T06:30:00.000Z',
    );
  });

  it('오프셋이 있으면 그대로 쓴다', () => {
    expect(parseServerDateTime('2026-10-07T15:30:00Z').toISOString()).toBe(
      '2026-10-07T15:30:00.000Z',
    );
    expect(parseServerDateTime('2026-10-07T15:30:00+00:00').toISOString()).toBe(
      '2026-10-07T15:30:00.000Z',
    );
  });
});

describe('경과 시간', () => {
  // 2026-10-07T15:30:00 서울
  const now = new Date('2026-10-07T06:30:00Z');

  it.each([
    ['2026-10-07T15:30:00', '방금 전'],
    ['2026-10-07T15:29:01', '방금 전'],
    ['2026-10-07T15:29:00', '1분 전'],
    ['2026-10-07T14:31:00', '59분 전'],
    ['2026-10-07T14:30:00', '1시간 전'],
    ['2026-10-06T15:31:00', '23시간 전'],
    ['2026-10-06T15:30:00', '1일 전'],
    ['2026-10-01T15:30:00', '6일 전'],
    ['2026-09-30T15:30:00', '1주 전'],
    ['2026-09-08T15:30:00', '4주 전'],
    ['2026-09-07T15:30:00', '1개월 전'],
    ['2025-10-08T15:30:00', '12개월 전'],
    ['2025-10-07T15:30:00', '1년 전'],
  ])('%s → %s', (dateTime, expected) => {
    expect(formatElapsed(dateTime, now)).toBe(expected);
  });

  it('미래 시각(시계 오차)은 "방금 전"', () => {
    expect(formatElapsed('2026-10-07T15:35:00', now)).toBe('방금 전');
  });

  it('오프셋이 있는 시각도 같은 기준으로 계산한다', () => {
    expect(formatElapsed('2026-10-07T06:27:00Z', now)).toBe('3분 전');
  });
});
