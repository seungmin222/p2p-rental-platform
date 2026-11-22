import { describe, expect, it } from 'vitest';

import { isPaymentExpired, refundOnCancel, requiresPayment } from './rentalData';

describe('requiresPayment', () => {
  it.each([
    [0, 0, false],
    [5000, 0, true],
    [0, 50000, true],
    [5000, 50000, true],
  ])('일 대여료 %i, 보증금 %i → %s', (pricePerDay, deposit, expected) => {
    expect(requiresPayment(pricePerDay, deposit)).toBe(expected);
  });
});

describe('refundOnCancel', () => {
  it('대여 예정 취소는 결제 금액 − 수수료', () => {
    expect(refundOnCancel('RESERVED', { totalAmount: 80000, cancelFee: 9000 })).toBe(71000);
  });

  it('결제 대기 취소는 환불이 없다', () => {
    expect(refundOnCancel('PENDING_PAYMENT', { totalAmount: 80000, cancelFee: 0 })).toBe(0);
  });
});

describe('isPaymentExpired', () => {
  const pending = { status: 'PENDING_PAYMENT', paymentExpiresAt: '2026-10-29T10:15:00' };
  const expiry = Date.parse('2026-10-29T10:15:00+09:00');

  it('만료 시각부터 만료', () => {
    expect(isPaymentExpired(pending, expiry - 1)).toBe(false);
    expect(isPaymentExpired(pending, expiry)).toBe(true);
  });

  it('결제 대기가 아니면 만료가 아니다', () => {
    expect(isPaymentExpired({ status: 'RESERVED', paymentExpiresAt: null }, expiry)).toBe(false);
  });
});
