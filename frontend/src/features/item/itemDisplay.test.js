import { describe, expect, it } from 'vitest';

import { getCardMeta, getDepositText, getPriceParts } from './itemDisplay';

describe('getPriceParts', () => {
  it('금액과 단위를 나눈다', () => {
    expect(getPriceParts(15000)).toEqual({ amount: '15,000원', unit: '/일' });
  });

  it('0원은 무료', () => {
    expect(getPriceParts(0)).toEqual({ amount: '무료', unit: '' });
  });

  it('숫자가 아니면 빈 값 — "NaN원"을 그리지 않는다', () => {
    expect(getPriceParts(undefined)).toEqual({ amount: '', unit: '' });
    expect(getPriceParts(null)).toEqual({ amount: '', unit: '' });
  });
});

describe('getDepositText', () => {
  it('보증금이 있으면 "보증금 N원"', () => {
    expect(getDepositText(50000)).toBe('보증금 50,000원');
  });

  it('0원이면 보증금 줄을 그리지 않는다', () => {
    expect(getDepositText(0)).toBeNull();
  });

  it('숫자가 아니면 그리지 않는다', () => {
    expect(getDepositText(undefined)).toBeNull();
  });
});

describe('getCardMeta', () => {
  const now = new Date('2026-10-30T10:00:00+09:00');

  it('동네 · 경과 시간', () => {
    expect(getCardMeta('송도1동', '2026-10-30T09:57:00', now)).toBe('송도1동 · 3분 전');
    expect(getCardMeta('송도1동', '2026-10-28T10:00:00', now)).toBe('송도1동 · 2일 전');
  });

  it('시각을 읽을 수 없으면 동네만', () => {
    expect(getCardMeta('송도1동', undefined, now)).toBe('송도1동');
  });
});
