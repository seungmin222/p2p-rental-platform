import { describe, expect, it } from 'vitest';

import { isWindowClosedCode, toTossPaymentRequest } from './tossPayment';

describe('toTossPaymentRequest', () => {
  it('주문을 카드 결제 요청으로 바꾸고 복귀 주소에 오리진을 넣는다', () => {
    const order = { orderId: 'order-1', amount: 35000, orderName: '텐트', customerKey: 'c' };
    expect(toTossPaymentRequest(order, 'http://localhost:5173')).toEqual({
      method: 'CARD',
      amount: { currency: 'KRW', value: 35000 },
      orderId: 'order-1',
      orderName: '텐트',
      successUrl: 'http://localhost:5173/payments/success',
      failUrl: 'http://localhost:5173/payments/fail',
    });
  });
});

describe('isWindowClosedCode', () => {
  it('구매자 취소만 닫힘으로 본다', () => {
    expect(isWindowClosedCode('PAY_PROCESS_CANCELED')).toBe(true);
    expect(isWindowClosedCode('USER_CANCEL')).toBe(true);
    expect(isWindowClosedCode('REJECT_CARD_COMPANY')).toBe(false);
    expect(isWindowClosedCode(null)).toBe(false);
  });
});
