import { describe, expect, it } from 'vitest';

import { getDepositNotice, isPaymentExpired, refundOnCancel } from './rentalData';

describe('getDepositNotice', () => {
  const rental = (status, deposit = 50000) => ({ status, deposit });

  it('정상 확인 후 완료면 보증금 반환 완료', () => {
    expect(getDepositNotice(rental('COMPLETED'), { result: 'NORMAL' })).toEqual({
      kind: 'REFUNDED',
      amount: 50000,
    });
  });

  it('정상 확인인데 RETURNED면 반환 처리 중', () => {
    expect(getDepositNotice(rental('RETURNED'), { result: 'NORMAL' })).toEqual({
      kind: 'REFUNDING',
    });
  });

  it('이상 판정으로 RETURNED면 처리 대기', () => {
    expect(getDepositNotice(rental('RETURNED'), { result: 'ABNORMAL' })).toEqual({
      kind: 'WAITING',
    });
  });

  it.each([
    ['보증금 0원', rental('COMPLETED', 0), { result: 'NORMAL' }],
    ['기록 로딩 중', rental('COMPLETED'), undefined],
    ['기록 없음', rental('COMPLETED'), null],
    ['판정 전', rental('RETURNED'), { result: null }],
    ['이상 판정으로 완료', rental('COMPLETED'), { result: 'ABNORMAL' }],
    ['대여 중', rental('RENTING'), { result: null }],
  ])('%s면 안내 없음', (_, target, inspection) => {
    expect(getDepositNotice(target, inspection)).toBeNull();
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
