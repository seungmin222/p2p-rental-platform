import { describe, expect, it } from 'vitest';

import { MOCK_ME } from './item';
import {
  addBusinessDays,
  buildPaymentHistory,
  buildSettlements,
  filterPaymentHistory,
  minusMonths,
  readHistoryQuery,
  sumSettlements,
} from './paymentHistoryLogic';
import { createRentalSeed } from './rentalSeed';

const TODAY = '2026-11-21';
const ITEMS = {
  101: { itemId: 101, name: '텐트', thumbnailUrl: null, isDeleted: false },
  104: { itemId: 104, name: '빔 프로젝터', thumbnailUrl: null, isDeleted: false },
};
const itemOf = (itemId) => ITEMS[itemId] ?? { itemId, name: `물품 ${itemId}`, thumbnailUrl: null };

function rental(extra = {}) {
  return {
    rentalId: 1,
    itemId: 101,
    renterId: MOCK_ME,
    ownerId: 2,
    status: 'RESERVED',
    startDate: '2026-11-03',
    endDate: '2026-11-05',
    pricePerDay: 15000,
    deposit: 50000,
    createdAt: '2026-11-01T10:00:00',
    completedAt: null,
    canceledAt: null,
    cancelType: null,
    cancelFee: 0,
    ...extra,
  };
}

describe('날짜 계산', () => {
  it('영업일 더하기는 주말을 건너뛴다(금 → 화)', () => {
    expect(addBusinessDays('2026-11-20', 2)).toBe('2026-11-24');
    expect(addBusinessDays('2026-11-21', 2)).toBe('2026-11-24');
  });

  it('minusMonths는 없는 날이면 그 달 마지막 날', () => {
    expect(minusMonths('2026-11-21', 3)).toBe('2026-08-21');
    expect(minusMonths('2026-05-31', 3)).toBe('2026-02-28');
    expect(minusMonths('2026-02-15', 3)).toBe('2025-11-15');
  });
});

describe('readHistoryQuery', () => {
  it('기본값: to 오늘, from = to − 3개월', () => {
    expect(readHistoryQuery(new URLSearchParams(), TODAY)).toEqual({
      type: 'ALL',
      from: '2026-08-21',
      to: TODAY,
    });
  });

  it('기본값을 채운 뒤 from > to면 field to', () => {
    expect(readHistoryQuery(new URLSearchParams('from=2026-11-22'), TODAY).error.field).toBe('to');
  });

  it('형식 오류', () => {
    expect(readHistoryQuery(new URLSearchParams('type=X'), TODAY).error.field).toBe('type');
    expect(readHistoryQuery(new URLSearchParams('to=2026-13-01'), TODAY).error.field).toBe('to');
  });
});

describe('buildPaymentHistory', () => {
  it('결제 대기·미승인 취소는 빼고, 금액은 대여료 + 보증금', () => {
    const rentals = [
      rental({ rentalId: 1 }),
      rental({ rentalId: 2, status: 'PENDING_PAYMENT' }),
      rental({ rentalId: 3, status: 'CANCELED', cancelType: 'PAYMENT_EXPIRED' }),
    ];
    const payments = buildPaymentHistory(rentals, [], MOCK_ME, itemOf).map((e) => e.payment);
    expect(payments.map((p) => p.paymentId)).toEqual([1]);
    expect(payments[0]).toMatchObject({
      amount: 95000,
      rentalFee: 45000,
      deposit: 50000,
      paymentStatus: 'APPROVED',
      depositStatus: 'FROZEN',
    });
  });

  it('주문이 있으면 주문 금액을 쓰고, 승인되지 않은 주문만 있으면 뺀다', () => {
    const rentals = [rental({ rentalId: 1, endDate: '2026-11-07' }), rental({ rentalId: 2 })];
    const orders = [
      {
        rentalId: 1,
        type: 'RENTAL',
        status: 'APPROVED',
        amount: 95000,
        createdAt: '2026-11-02T09:00:00',
      },
      { rentalId: 2, type: 'RENTAL', status: 'FAILED', amount: 95000 },
      { rentalId: 1, type: 'EXTENSION', extensionId: 7, status: 'APPROVED', amount: 30000 },
    ];
    const payments = buildPaymentHistory(rentals, orders, MOCK_ME, itemOf).map((e) => e.payment);
    expect(payments.map((p) => [p.paymentId, p.paymentType, p.amount])).toEqual([
      [1, 'RENTAL', 95000],
      [100007, 'EXTENSION', 30000],
    ]);
    expect(payments[1]).toMatchObject({ extensionFee: 30000, depositStatus: null });
  });

  it('수수료 있는 취소는 부분 환불(대여료·보증금 − 수수료), 보증금 반환', () => {
    const [entry] = buildPaymentHistory(
      [rental({ status: 'CANCELED', cancelType: 'RENTER', cancelFee: 13500 })],
      [],
      MOCK_ME,
      itemOf,
    );
    expect(entry.rentalCanceled).toBe(true);
    expect(entry.payment).toMatchObject({
      paymentStatus: 'PARTIAL_CANCELED',
      depositStatus: 'REFUNDED',
      refundedAmount: 81500,
    });
  });

  it('완료: 연체료·차감을 빼고 반환, 남은 게 없으면 전액 차감', () => {
    const rentals = [
      rental({ rentalId: 10, status: 'COMPLETED' }),
      rental({ rentalId: 41, status: 'COMPLETED' }),
      rental({ rentalId: 45, status: 'COMPLETED' }),
    ];
    const byId = Object.fromEntries(
      buildPaymentHistory(rentals, [], MOCK_ME, itemOf).map((e) => [
        e.payment.paymentId,
        e.payment,
      ]),
    );
    expect(byId[10]).toMatchObject({
      paymentStatus: 'PARTIAL_CANCELED',
      depositStatus: 'REFUNDED',
      overdueFee: 15000,
      refundedAmount: 15000,
    });
    expect(byId[10].deduction.amount).toBe(20000);
    expect(byId[41]).toMatchObject({ depositStatus: 'FORFEITED', refundedAmount: 0 });
    expect(byId[45]).toMatchObject({ depositStatus: 'REFUND_FAILED', depositRefundPending: true });
  });
});

describe('filterPaymentHistory', () => {
  const rentals = [
    rental({ rentalId: 1, createdAt: '2026-11-01T10:00:00' }),
    rental({
      rentalId: 2,
      status: 'CANCELED',
      cancelType: 'RENTER',
      createdAt: '2026-10-01T10:00:00',
    }),
    rental({ rentalId: 3, status: 'COMPLETED', deposit: 0, createdAt: '2026-08-01T10:00:00' }),
  ];
  const entries = buildPaymentHistory(rentals, [], MOCK_ME, itemOf);
  const ids = (query) => filterPaymentHistory(entries, query).map((p) => p.paymentId);
  const ALL_TIME = { from: '2026-01-01', to: TODAY };

  it('type(#44 임시 해석)', () => {
    expect(ids({ ...ALL_TIME, type: 'ALL' })).toEqual([1, 2, 3]);
    expect(ids({ ...ALL_TIME, type: 'PAYMENT' })).toEqual([1, 2, 3]);
    expect(ids({ ...ALL_TIME, type: 'REFUND' })).toEqual([2]);
    expect(ids({ ...ALL_TIME, type: 'DEPOSIT' })).toEqual([2]);
  });

  it('기간은 결제 날짜, 양끝 포함', () => {
    expect(ids({ type: 'ALL', from: '2026-10-01', to: '2026-11-01' })).toEqual([1, 2]);
  });
});

describe('buildSettlements', () => {
  it('완료·수수료 취소만, 지급액 = gross − 수수료 + 차감액', () => {
    const rentals = [
      rental({
        rentalId: 30,
        ownerId: MOCK_ME,
        renterId: 3,
        itemId: 104,
        status: 'COMPLETED',
        pricePerDay: 20000,
        deposit: 100000,
        completedAt: '2026-11-13T10:00:00',
      }),
      rental({
        rentalId: 34,
        ownerId: MOCK_ME,
        renterId: 3,
        status: 'CANCELED',
        cancelType: 'RENTER',
        cancelFee: 12000,
        canceledAt: '2026-11-20T11:20:00',
      }),
      rental({ rentalId: 31, ownerId: MOCK_ME, renterId: 3, status: 'CANCELED', cancelFee: 0 }),
      rental({ rentalId: 26, ownerId: MOCK_ME, renterId: 3, status: 'RENTING' }),
    ];
    const settlements = buildSettlements(rentals, [], MOCK_ME, itemOf, TODAY);
    expect(settlements.map((s) => s.settlementId)).toEqual([34, 30]);
    expect(settlements[0]).toMatchObject({
      source: 'CANCEL_FEE',
      grossAmount: 12000,
      feeAmount: 1200,
      payoutAmount: 10800,
      scheduledDate: '2026-11-24',
      status: 'SCHEDULED',
      paidAt: null,
    });
    expect(settlements[1]).toMatchObject({
      source: 'COMPLETED',
      grossAmount: 60000,
      feeAmount: 6000,
      deductionAmount: 30000,
      payoutAmount: 84000,
      scheduledDate: '2026-11-17',
      status: 'PAID',
      paidAt: '2026-11-17T00:30:00',
    });
    expect(sumSettlements(settlements)).toEqual({
      scheduledTotal: 10800,
      heldTotal: 0,
      paidTotal: 84000,
    });

    // 분쟁(REQ-063): 지급 예정은 보류, 이미 지급된 정산은 그대로
    const disputed = buildSettlements(rentals, [], MOCK_ME, itemOf, TODAY, new Set([34, 30]));
    expect(disputed.map((s) => s.status)).toEqual(['HELD', 'PAID']);
    expect(sumSettlements(disputed)).toEqual({
      scheduledTotal: 0,
      heldTotal: 10800,
      paidTotal: 84000,
    });
  });

  it('연장료는 APPROVED·PARTIAL_CANCELED 결제의 받은 금액만 더한다(REQ-039)', () => {
    const rentals = [
      rental({
        rentalId: 30,
        ownerId: MOCK_ME,
        renterId: 3,
        itemId: 104,
        status: 'COMPLETED',
        pricePerDay: 20000,
        deposit: 100000,
        completedAt: '2026-11-13T10:00:00',
      }),
    ];
    const orders = [
      { rentalId: 30, type: 'EXTENSION', extensionId: 1, status: 'APPROVED', amount: 20000 },
      {
        rentalId: 30,
        type: 'EXTENSION',
        extensionId: 2,
        status: 'PARTIAL_CANCELED',
        amount: 10000,
      },
      { rentalId: 30, type: 'EXTENSION', extensionId: 3, status: 'CANCELED', amount: 40000 },
    ];
    const [settlement] = buildSettlements(rentals, orders, MOCK_ME, itemOf, TODAY);
    // 대여료 60,000 + 연장료 20,000 + 10,000(부분 취소, 목은 환불액 0)
    expect(settlement.grossAmount).toBe(90000);
  });

  it('시드: 33은 분쟁 보류, 2페이지가 넘는 정산', () => {
    const { rentals } = createRentalSeed();
    const settlements = buildSettlements(rentals, [], MOCK_ME, itemOf, TODAY);
    expect(settlements.find((s) => s.rentalId === 33).status).toBe('HELD');
    expect(settlements.some((s) => s.source === 'CANCEL_FEE')).toBe(true);
    expect(settlements.length).toBeGreaterThan(20);
  });
});
