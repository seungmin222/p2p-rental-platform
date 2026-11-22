import { describe, expect, it } from 'vitest';

import {
  calcOverdueFee,
  deductionPolicyOf,
  isLostReportOpen,
  isProposalClosed,
  latestProposalOf,
  overdueFeeOf,
  settleDeduction,
  validateProposal,
} from './depositLogic';

const rental = (overrides = {}) => ({
  status: 'OVERDUE',
  endDate: '2026-11-10',
  pricePerDay: 5000,
  deposit: 100000,
  ...overrides,
});

describe('calcOverdueFee', () => {
  it('연체 일수 × floor(일 대여료 × 1.5), 보증금 한도', () => {
    expect(calcOverdueFee(rental(), '2026-11-13')).toBe(22500);
    expect(calcOverdueFee(rental({ pricePerDay: 3333 }), '2026-11-11')).toBe(4999);
    expect(calcOverdueFee(rental(), '2026-12-31')).toBe(100000);
  });

  it('예정일 전·당일과 보증금 0원은 0', () => {
    expect(calcOverdueFee(rental(), '2026-11-10')).toBe(0);
    expect(calcOverdueFee(rental({ deposit: 0 }), '2026-11-20')).toBe(0);
  });
});

describe('overdueFeeOf', () => {
  it('OVERDUE·종료일 지난 RENTING은 오늘까지, 그 밖은 확정값', () => {
    expect(overdueFeeOf(rental(), {}, '2026-11-12')).toBe(15000);
    expect(overdueFeeOf(rental({ status: 'RENTING' }), {}, '2026-11-12')).toBe(15000);
    expect(overdueFeeOf(rental({ status: 'RETURNED' }), { overdueFee: 7500 }, '2026-11-20')).toBe(
      7500,
    );
    expect(overdueFeeOf(rental({ status: 'COMPLETED' }), {}, '2026-11-20')).toBe(0);
  });
});

describe('isLostReportOpen', () => {
  it('오늘 > 예정일 + 7일', () => {
    expect(isLostReportOpen(rental(), '2026-11-17')).toBe(false);
    expect(isLostReportOpen(rental(), '2026-11-18')).toBe(true);
  });
});

describe('deductionPolicyOf', () => {
  const base = { deposit: 100000, overdueFee: 30000, categoryId: 3, isLostReport: false };

  it('B 정액(촬영 장비 10,000)', () => {
    expect(deductionPolicyOf({ ...base, grade: 'B' })).toEqual({
      grade: 'B',
      deductionType: 'FIXED',
      deposit: 100000,
      overdueFee: 30000,
      remainingDeposit: 70000,
      maxAmount: 10000,
      receiptRequired: false,
    });
  });

  it('B 공구는 실비 → 상한 = 남은 보증금, 영수증 필요', () => {
    expect(deductionPolicyOf({ ...base, categoryId: 2, grade: 'B' })).toMatchObject({
      deductionType: 'ACTUAL',
      maxAmount: 70000,
      receiptRequired: true,
    });
  });

  it('C = min(floor(보증금 × 상한율), 남은 보증금)', () => {
    expect(deductionPolicyOf({ ...base, grade: 'C' })).toMatchObject({
      deductionType: 'ACTUAL',
      maxAmount: 70000,
    });
    expect(deductionPolicyOf({ ...base, overdueFee: 0, categoryId: 1, grade: 'C' })).toMatchObject({
      maxAmount: 50000,
    });
  });

  it('D = 남은 보증금(전액)', () => {
    expect(deductionPolicyOf({ ...base, grade: 'D' })).toMatchObject({
      deductionType: 'FULL',
      maxAmount: 70000,
      receiptRequired: false,
    });
  });

  it('A·미반납 건의 D 아닌 등급은 GRADE_NOT_ALLOWED, 모르는 값은 입력 오류', () => {
    expect(deductionPolicyOf({ ...base, grade: 'A' })).toEqual({
      error: 'DEDUCTION_GRADE_NOT_ALLOWED',
    });
    expect(deductionPolicyOf({ ...base, isLostReport: true, grade: 'C' })).toEqual({
      error: 'DEDUCTION_GRADE_NOT_ALLOWED',
    });
    expect(deductionPolicyOf({ ...base, grade: 'Z' })).toEqual({ error: 'COMMON_INVALID_INPUT' });
  });
});

describe('validateProposal', () => {
  const policy = deductionPolicyOf({
    deposit: 100000,
    overdueFee: 30000,
    categoryId: 1,
    grade: 'C',
    isLostReport: false,
  });
  const input = { amount: 20000, evidenceCount: 1, receiptCount: 1, isLostReport: false };

  it('통과', () => {
    expect(validateProposal(policy, input)).toBeNull();
  });

  it('남은 보증금 초과 → 상한 초과(details maxAmount) → 증빙 순서', () => {
    expect(validateProposal(policy, { ...input, amount: 70001 })).toEqual({
      code: 'DEDUCTION_EXCEEDS_DEPOSIT',
    });
    expect(validateProposal(policy, { ...input, amount: 50001 })).toEqual({
      code: 'DEDUCTION_EXCEEDS_CAP',
      details: [{ field: 'maxAmount', reason: '50000' }],
    });
    expect(validateProposal(policy, { ...input, receiptCount: 0 })).toEqual({
      code: 'DEDUCTION_EVIDENCE_REQUIRED',
    });
    expect(validateProposal(policy, { ...input, evidenceCount: 0 })).toEqual({
      code: 'DEDUCTION_EVIDENCE_REQUIRED',
    });
  });

  it('미반납 신고 건은 손상 사진 0장도 된다(open-issues #38)', () => {
    const lost = deductionPolicyOf({
      deposit: 100000,
      overdueFee: 0,
      categoryId: 1,
      grade: 'D',
      isLostReport: true,
    });
    expect(
      validateProposal(lost, { amount: 0, evidenceCount: 0, receiptCount: 0, isLostReport: true }),
    ).toBeNull();
  });
});

describe('제안·확정', () => {
  it('기한이 지난 PROPOSED만 닫힘', () => {
    const proposal = { status: 'PROPOSED', respondDeadline: '2026-11-20T10:00:00' };
    expect(isProposalClosed(proposal, '2026-11-20T09:59:59')).toBe(false);
    expect(isProposalClosed(proposal, '2026-11-20T10:00:00')).toBe(true);
    expect(isProposalClosed({ ...proposal, status: 'REJECTED' }, '2026-12-01T00:00:00')).toBe(
      false,
    );
  });

  it('최신 제안은 proposalId가 가장 큰 것', () => {
    const proposals = [
      { proposalId: 1, rentalId: 5 },
      { proposalId: 3, rentalId: 5 },
      { proposalId: 2, rentalId: 6 },
    ];
    expect(latestProposalOf(proposals, 5).proposalId).toBe(3);
    expect(latestProposalOf(proposals, 7)).toBeNull();
  });

  it('반환액 0이면 FORFEITED', () => {
    expect(settleDeduction(100000, 30000, 20000)).toEqual({
      refund: 50000,
      depositStatus: 'REFUNDED',
    });
    expect(settleDeduction(100000, 30000, 70000)).toEqual({
      refund: 0,
      depositStatus: 'FORFEITED',
    });
  });
});
