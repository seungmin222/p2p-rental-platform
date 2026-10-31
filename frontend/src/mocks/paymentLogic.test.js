import { describe, expect, it } from 'vitest';

import { calcTotalAmount } from '../features/rental/rentalData';
import { findMockItem, listMockItems } from './item';
import {
  createMockOrder,
  hasFrozenDeposit,
  toConfirmResponse,
  toMockCustomerKey,
  toOrderResponse,
  TOSS_CUSTOMER_KEY_PATTERN,
  TOSS_ORDER_ID_PATTERN,
  TOSS_ORDER_NAME_MAX,
} from './paymentLogic';

const rentalOf = (item) => ({
  rentalId: 1,
  pricePerDay: item.pricePerDay,
  deposit: item.deposit,
});

describe('목 주문은 토스 결제창 형식을 지킨다', () => {
  it('crypto.randomUUID 주문번호가 orderId 형식(6~64자)에 맞는다', () => {
    expect(crypto.randomUUID()).toMatch(TOSS_ORDER_ID_PATTERN);
  });

  it.each([1, 2, 4, 999999])('회원 %i의 customerKey가 형식(22~50자)에 맞고 회원마다 같다', (id) => {
    expect(toMockCustomerKey(id)).toMatch(TOSS_CUSTOMER_KEY_PATTERN);
    expect(toMockCustomerKey(id)).toBe(toMockCustomerKey(id));
  });

  it('회원이 다르면 customerKey도 다르다', () => {
    expect(toMockCustomerKey(1)).not.toBe(toMockCustomerKey(2));
  });

  it('긴 물품 이름은 orderName 상한으로 자른다', () => {
    const order = createMockOrder(rentalOf(findMockItem(101)), 1, '가'.repeat(150), 'order-1');
    expect(order.orderName).toHaveLength(TOSS_ORDER_NAME_MAX);
  });

  it('응답에 결제창이 쓰는 값만 담는다', () => {
    const order = createMockOrder(rentalOf(findMockItem(101)), 3, '텐트', 'order-1');
    expect(toOrderResponse(order, 1)).toEqual({
      orderId: 'order-1',
      amount: 95000,
      orderName: '텐트',
      customerKey: toMockCustomerKey(1),
    });
  });
});

describe('목 금액은 예약 화면 금액과 같다', () => {
  const payable = listMockItems().filter((item) => item.pricePerDay + item.deposit > 0);

  it.each(payable.map((item) => [item.itemId, item]))('물품 %i', (_, item) => {
    for (const days of [1, 3, 30]) {
      const order = createMockOrder(rentalOf(item), days, item.name, 'order-1');
      expect(order.amount).toBe(calcTotalAmount(item.pricePerDay, days, item.deposit));
      expect(Number.isInteger(order.amount)).toBe(true);
    }
  });
});

describe('toConfirmResponse', () => {
  it('PaymentConfirmResponse 필드만 돌려준다', () => {
    const order = createMockOrder(rentalOf(findMockItem(101)), 1, '텐트', 'order-1');
    expect(Object.keys(toConfirmResponse(order)).sort()).toEqual([
      'amount',
      'depositStatus',
      'orderId',
      'rentalId',
      'status',
    ]);
  });
});

describe('hasFrozenDeposit', () => {
  const rental = { rentalId: 1, deposit: 50000 };

  it('결제가 꺼져 있거나 보증금이 0원이면 없다', () => {
    expect(hasFrozenDeposit(rental, [], false)).toBe(false);
    expect(hasFrozenDeposit({ rentalId: 1, deposit: 0 }, [], true)).toBe(false);
  });

  it('승인된 주문이 있으면 그 상태를 따른다', () => {
    const order = (depositStatus) => ({ rentalId: 1, status: 'APPROVED', depositStatus });
    expect(hasFrozenDeposit(rental, [order('FROZEN')], true)).toBe(true);
    expect(hasFrozenDeposit(rental, [order('REFUNDED')], true)).toBe(false);
  });

  it('주문이 없는 시드 대여는 결제된 것으로 본다', () => {
    expect(hasFrozenDeposit(rental, [], true)).toBe(true);
  });
});
