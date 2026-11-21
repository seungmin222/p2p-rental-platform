import { describe, expect, it } from 'vitest';

import {
  formatDateTime,
  getPaymentHistoryView,
  getRangeLabel,
  getSettlementView,
  readHistoryFilter,
  toHistoryParams,
  validateHistoryRange,
  writeHistoryFilter,
} from './paymentHistory';

const TODAY = '2026-11-21';

function payment(extra = {}) {
  return {
    paymentId: 1,
    rentalId: 10,
    item: { itemId: 101, name: '텐트', thumbnailUrl: null, isDeleted: false },
    startDate: '2026-11-03',
    endDate: '2026-11-05',
    paymentType: 'RENTAL',
    paymentStatus: 'APPROVED',
    depositStatus: 'FROZEN',
    amount: 80000,
    rentalFee: 30000,
    deposit: 50000,
    extensionFee: 0,
    refundedAmount: 0,
    depositRefundPending: false,
    overdueFee: 0,
    cancelFee: 0,
    deduction: null,
    createdAt: '2026-11-01T14:20:05',
    ...extra,
  };
}

function settlement(extra = {}) {
  return {
    settlementId: 1,
    rentalId: 10,
    item: { itemId: 104, name: '빔 프로젝터', thumbnailUrl: null, isDeleted: false },
    startDate: '2026-11-03',
    endDate: '2026-11-05',
    source: 'COMPLETED',
    status: 'SCHEDULED',
    grossAmount: 45000,
    feeAmount: 4500,
    deductionAmount: 0,
    payoutAmount: 40500,
    scheduledDate: '2026-11-10',
    paidAt: null,
    ...extra,
  };
}

describe('readHistoryFilter', () => {
  it('비어 있으면 ALL·서버 기본 기간', () => {
    expect(readHistoryFilter(new URLSearchParams())).toEqual({ type: 'ALL', from: null, to: null });
  });

  it('모르는 type은 ALL, 날짜가 아니거나 없는 날은 버린다', () => {
    const params = new URLSearchParams('type=FOO&from=2026-02-30&to=abc');
    expect(readHistoryFilter(params)).toEqual({ type: 'ALL', from: null, to: null });
  });

  it('from > to면 둘 다 버린다', () => {
    const params = new URLSearchParams('type=REFUND&from=2026-11-10&to=2026-11-01');
    expect(readHistoryFilter(params)).toEqual({ type: 'REFUND', from: null, to: null });
  });

  it('맞는 값은 그대로', () => {
    const params = new URLSearchParams('type=DEPOSIT&from=2026-10-01&to=2026-11-01');
    expect(readHistoryFilter(params)).toEqual({
      type: 'DEPOSIT',
      from: '2026-10-01',
      to: '2026-11-01',
    });
  });
});

describe('writeHistoryFilter·toHistoryParams', () => {
  it('기본값은 주소·요청에 넣지 않고 다른 쿼리(tab)는 둔다', () => {
    const next = writeHistoryFilter(
      new URLSearchParams('tab=payments&type=REFUND&from=2026-10-01'),
      {
        type: 'ALL',
        from: null,
        to: null,
      },
    );
    expect(next.toString()).toBe('tab=payments');
    expect(toHistoryParams({ type: 'ALL', from: null, to: null }, 0)).toEqual({ page: 0 });
  });

  it('값이 있으면 넣는다', () => {
    const filter = { type: 'PAYMENT', from: '2026-10-01', to: '2026-11-01' };
    expect(writeHistoryFilter(new URLSearchParams(), filter).toString()).toBe(
      'type=PAYMENT&from=2026-10-01&to=2026-11-01',
    );
    expect(toHistoryParams(filter, 2)).toEqual({ page: 2, ...filter });
  });
});

describe('validateHistoryRange', () => {
  it('비었거나 순서가 맞으면 null', () => {
    expect(validateHistoryRange({ from: '', to: '' }, TODAY)).toBeNull();
    expect(validateHistoryRange({ from: '2026-11-01', to: '2026-11-01' }, TODAY)).toBeNull();
    expect(validateHistoryRange({ from: '', to: '2027-01-01' }, TODAY)).toBeNull();
  });

  it('from > to', () => {
    expect(validateHistoryRange({ from: '2026-11-02', to: '2026-11-01' }, TODAY)).toBe(
      '시작일이 종료일보다 늦어요',
    );
  });

  it('to를 비우면 오늘과 비교한다(서버가 오늘로 채운다)', () => {
    expect(validateHistoryRange({ from: '2026-11-22', to: '' }, TODAY)).toBe(
      '시작일이 오늘보다 늦어요',
    );
    expect(validateHistoryRange({ from: TODAY, to: '' }, TODAY)).toBeNull();
  });
});

describe('getRangeLabel', () => {
  it('경우별 문구', () => {
    expect(getRangeLabel({ from: null, to: null })).toBe('최근 3개월');
    expect(getRangeLabel({ from: '2026-10-01', to: null })).toBe('2026.10.01 ~ 오늘');
    expect(getRangeLabel({ from: '2026-10-01', to: '2026-11-01' })).toBe('2026.10.01 ~ 2026.11.01');
    expect(getRangeLabel({ from: null, to: '2026-11-01' })).toBe('2026.11.01까지 3개월');
  });
});

describe('formatDateTime', () => {
  it('서버 일시 → 점 표기, 아니면 빈 문자열', () => {
    expect(formatDateTime('2026-11-01T14:20:05')).toBe('2026.11.01 14:20');
    expect(formatDateTime(null)).toBe('');
  });
});

describe('getPaymentHistoryView', () => {
  it('대여 결제: 대여료·보증금 줄, 보증금 상태 배지', () => {
    const view = getPaymentHistoryView(payment());
    expect(view.kindLabel).toBe('대여료·보증금');
    expect(view.status).toEqual({ label: '결제 완료', tone: 'green' });
    expect(view.deposit).toEqual({ label: '보증금 동결', tone: 'blue' });
    expect(view.rows.map((row) => row.label)).toEqual(['대여료', '보증금']);
    expect(view.amount).toBe('80,000원');
    expect(view.paidAt).toBe('2026.11.01 14:20');
  });

  it('연체료·차감·환불이 있으면 줄을 더하고 차감 사유를 안내한다', () => {
    const view = getPaymentHistoryView(
      payment({
        paymentStatus: 'PARTIAL_CANCELED',
        depositStatus: 'REFUNDED',
        overdueFee: 15000,
        deduction: { amount: 20000, grade: 'C', reason: '렌즈 흠집' },
        refundedAmount: 15000,
      }),
    );
    expect(view.status.label).toBe('부분 환불');
    expect(view.deposit.label).toBe('보증금 반환');
    expect(view.rows).toEqual([
      { label: '대여료', value: '30,000원' },
      { label: '보증금', value: '50,000원' },
      { label: '연체료', value: '−15,000원', tone: 'danger' },
      { label: '보증금 차감', value: '−20,000원', tone: 'danger' },
      { label: '환불 금액', value: '15,000원' },
    ]);
    expect(view.notes).toEqual(['차감 사유: 렌즈 흠집']);
  });

  it('취소 환불: 수수료·환불 금액, 반환 처리 중 안내', () => {
    const view = getPaymentHistoryView(
      payment({
        paymentStatus: 'CANCELED',
        depositStatus: 'REFUND_FAILED',
        cancelFee: 9000,
        refundedAmount: 71000,
        depositRefundPending: true,
      }),
    );
    expect(view.status.label).toBe('환불 완료');
    expect(view.deposit).toEqual({ label: '보증금 반환 실패', tone: 'red' });
    expect(view.rows.slice(2)).toEqual([
      { label: '취소 수수료', value: '9,000원' },
      { label: '환불 금액', value: '71,000원' },
    ]);
    expect(view.notes).toEqual(['환불을 처리하고 있어요']);
  });

  it('연장 결제: 연장료만, 보증금 상태 없음', () => {
    const view = getPaymentHistoryView(
      payment({
        paymentType: 'EXTENSION',
        depositStatus: null,
        amount: 8000,
        rentalFee: 0,
        deposit: 0,
        extensionFee: 8000,
      }),
    );
    expect(view.kindLabel).toBe('연장료');
    expect(view.deposit).toBeNull();
    expect(view.rows).toEqual([{ label: '연장료', value: '8,000원' }]);
  });

  it('삭제된 물품', () => {
    const view = getPaymentHistoryView(
      payment({ item: { itemId: 1, name: '자전거', thumbnailUrl: null, isDeleted: true } }),
    );
    expect(view.itemName).toBe('삭제된 물품');
    expect(view.isItemDeleted).toBe(true);
  });
});

describe('getSettlementView', () => {
  it('지급 예정: 예정일', () => {
    const view = getSettlementView(settlement());
    expect(view.status).toEqual({ label: '지급 예정', tone: 'blue' });
    expect(view.sourceLabel).toBe('거래 완료');
    expect(view.dateText).toBe('11월 10일 지급 예정');
    expect(view.payout).toBe('40,500원');
    expect(view.rows).toEqual([
      { label: '대여료·연장료·연체료', value: '45,000원' },
      { label: '플랫폼 수수료', value: '−4,500원' },
    ]);
  });

  it('지급 완료: 지급 일시, 차감액 줄', () => {
    const view = getSettlementView(
      settlement({ status: 'PAID', paidAt: '2026-11-10T00:30:00', deductionAmount: 20000 }),
    );
    expect(view.dateText).toBe('2026.11.10 00:30 지급');
    expect(view.rows.at(-1)).toEqual({ label: '보증금 차감액', value: '+20,000원' });
  });

  it('보류·취소 수수료', () => {
    const view = getSettlementView(settlement({ status: 'HELD', source: 'CANCEL_FEE' }));
    expect(view.status.label).toBe('보류');
    expect(view.sourceLabel).toBe('취소 수수료');
    expect(view.rows[0].label).toBe('취소 수수료');
    expect(view.dateText).toBe('분쟁이 끝나면 지급돼요');
  });
});
