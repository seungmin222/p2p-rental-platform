import { describe, expect, it } from 'vitest';

import {
  canReportLost,
  depositReturnAmount,
  getDeductionGrades,
  getDepositView,
  getProposalState,
  isAdminDecisionDue,
  isLostReportHold,
  parseDeductionAmount,
  validateDeductionImages,
} from './deposit';

const TODAY = '2026-11-20';
// 2026-11-20T12:00:00 서울
const NOW = new Date('2026-11-20T12:00:00+09:00').getTime();

function rental(overrides = {}) {
  return {
    rentalId: 1,
    status: 'RETURNED',
    myRole: 'RENTER',
    startDate: '2026-11-01',
    endDate: '2026-11-05',
    deposit: 50000,
    depositStatus: 'FROZEN',
    depositHeldAt: null,
    deduction: null,
    latestDeductionProposal: null,
    overdueFee: 0,
    refundedAmount: 0,
    depositRefundPending: false,
    ...overrides,
  };
}

function proposal(overrides = {}) {
  return {
    proposalId: 7,
    status: 'PROPOSED',
    grade: 'C',
    amount: 20000,
    reason: '폴대 휨',
    proposedAt: '2026-11-19T10:00:00',
    respondDeadline: '2026-11-22T10:00:00',
    respondedAt: null,
    evidenceImageUrls: ['/a.jpg'],
    receiptImageUrls: [],
    ...overrides,
  };
}

describe('isLostReportHold', () => {
  it('HELD이고 RETURN 기록이 판정 전이면 미반납 신고 건(open-issues #57)', () => {
    expect(isLostReportHold(rental({ depositStatus: 'HELD' }), { result: null })).toBe(true);
  });

  it.each([
    ['이상 판정', 'HELD', { result: 'ABNORMAL' }],
    ['기록 못 불러옴', 'HELD', undefined],
    ['기록 없음', 'HELD', null],
    ['보류 아님', 'FROZEN', { result: null }],
  ])('%s → false', (_, depositStatus, record) => {
    expect(isLostReportHold(rental({ depositStatus }), record)).toBe(false);
  });
});

describe('canReportLost', () => {
  const overdue = (myRole, endDate) => rental({ status: 'OVERDUE', myRole, endDate });

  it('제공자, 반납 예정일 + 7일이 지나면(8일째) 보인다', () => {
    expect(canReportLost(overdue('OWNER', '2026-11-12'), TODAY)).toBe(true);
  });

  it('정확히 7일째는 아직 숨긴다(오늘 > 예정일 + 7일)', () => {
    expect(canReportLost(overdue('OWNER', '2026-11-13'), TODAY)).toBe(false);
  });

  it('대여자·OVERDUE 아님은 숨긴다', () => {
    expect(canReportLost(overdue('RENTER', '2026-11-01'), TODAY)).toBe(false);
    expect(
      canReportLost(rental({ status: 'RENTING', myRole: 'OWNER', endDate: '2026-11-01' }), TODAY),
    ).toBe(false);
  });
});

describe('getProposalState', () => {
  it.each([
    [null, 'NONE'],
    [proposal(), 'OPEN'],
    [proposal({ respondDeadline: '2026-11-20T12:00:00' }), 'CLOSED'],
    [proposal({ status: 'REJECTED' }), 'REJECTED'],
    [proposal({ status: 'ACCEPTED' }), 'DONE'],
    [proposal({ status: 'AUTO_ACCEPTED' }), 'DONE'],
    [proposal({ status: 'ADMIN_DECIDED' }), 'DONE'],
  ])('%o → %s', (target, expected) => {
    expect(getProposalState(target, NOW)).toBe(expected);
  });
});

describe('isAdminDecisionDue', () => {
  it('보류 후 7일이 지나면 true', () => {
    expect(
      isAdminDecisionDue(
        rental({ depositStatus: 'HELD', depositHeldAt: '2026-11-13T12:00:00' }),
        NOW,
      ),
    ).toBe(true);
    expect(
      isAdminDecisionDue(
        rental({ depositStatus: 'HELD', depositHeldAt: '2026-11-13T12:00:01' }),
        NOW,
      ),
    ).toBe(false);
  });

  it('보류가 아니면 false', () => {
    expect(isAdminDecisionDue(rental({ depositHeldAt: '2026-11-01T00:00:00' }), NOW)).toBe(false);
  });
});

describe('getDeductionGrades', () => {
  it('A는 없고, 미반납 신고 건은 D만', () => {
    expect(getDeductionGrades(false)).toEqual(['B', 'C', 'D']);
    expect(getDeductionGrades(true)).toEqual(['D']);
  });
});

describe('parseDeductionAmount', () => {
  it.each([
    ['30,000', 50000, 30000],
    ['0', 50000, 0],
    ['50000', 50000, 50000],
    ['99999', undefined, 99999],
  ])('%s (상한 %s) → %i', (text, max, value) => {
    expect(parseDeductionAmount(text, max)).toEqual({ value, error: null });
  });

  it.each([
    ['', '차감 금액을 입력해 주세요'],
    ['-1', '숫자로 입력해 주세요'],
    ['1.5', '숫자로 입력해 주세요'],
    ['50001', '50,000원까지 제안할 수 있어요'],
  ])('%s → 오류', (text, error) => {
    expect(parseDeductionAmount(text, 50000)).toEqual({ value: null, error });
  });
});

describe('validateDeductionImages', () => {
  it('손상 사진은 1장 이상, 미반납 신고 건은 0장도 된다(open-issues #38)', () => {
    expect(
      validateDeductionImages({
        evidenceCount: 0,
        receiptCount: 0,
        isLostReport: false,
        receiptRequired: false,
      }),
    ).toEqual({ evidenceImages: '손상 사진을 1장 이상 올려 주세요' });
    expect(
      validateDeductionImages({
        evidenceCount: 0,
        receiptCount: 0,
        isLostReport: true,
        receiptRequired: false,
      }),
    ).toBeNull();
  });

  it('실비(receiptRequired)면 영수증 1장 이상', () => {
    expect(
      validateDeductionImages({
        evidenceCount: 1,
        receiptCount: 0,
        isLostReport: false,
        receiptRequired: true,
      }),
    ).toEqual({ receiptImages: '견적서·영수증 사진을 1장 이상 올려 주세요' });
  });
});

describe('depositReturnAmount', () => {
  it('보증금 − 연체료 − 차감, 0 미만은 0', () => {
    expect(depositReturnAmount(rental({ overdueFee: 15000, deduction: { amount: 20000 } }))).toBe(
      15000,
    );
    expect(depositReturnAmount(rental({ overdueFee: 50000, deduction: { amount: 1 } }))).toBe(0);
  });
});

describe('getDepositView', () => {
  it.each([null, 'NONE'])('depositStatus %s면 카드 없음', (depositStatus) => {
    expect(getDepositView(rental({ depositStatus }), null, TODAY, NOW)).toBeNull();
  });

  it('FROZEN 대여자: 보관 안내, 보증금 줄만', () => {
    const view = getDepositView(
      rental({ status: 'RENTING', endDate: '2026-11-25' }),
      null,
      TODAY,
      NOW,
    );
    expect(view.title).toBe('보증금을 보관하고 있어요. 반납을 확인하면 돌려드려요');
    expect(view.rows).toEqual([{ label: '보증금', value: '50,000원' }]);
    expect(view.notes).toEqual([]);
    expect(view.proposal).toBeNull();
  });

  it('OVERDUE: "현재까지 연체료"와 보증금에서 먼저 빠진다는 안내', () => {
    const view = getDepositView(
      rental({ status: 'OVERDUE', myRole: 'OWNER', overdueFee: 15000 }),
      undefined,
      TODAY,
      NOW,
    );
    expect(view.title).toBe('대여자의 보증금을 플랫폼이 보관하고 있어요');
    expect(view.rows[1]).toEqual({ label: '현재까지 연체료', value: '−15,000원', tone: 'danger' });
    expect(view.notes).toEqual(['연체료는 보증금에서 먼저 빠져요']);
  });

  it('FROZEN이고 반환 처리 중이면 그 안내', () => {
    const view = getDepositView(
      rental({ depositRefundPending: true }),
      { result: 'NORMAL' },
      TODAY,
      NOW,
    );
    expect(view.title).toBe('보증금 반환을 처리하고 있어요');
  });

  it('HELD(이상 판정) 제공자, 제안 없음: 확정 연체료·보류 시각·제안 안내', () => {
    const view = getDepositView(
      rental({
        myRole: 'OWNER',
        depositStatus: 'HELD',
        depositHeldAt: '2026-11-18T09:30:00',
        overdueFee: 15000,
      }),
      { result: 'ABNORMAL' },
      TODAY,
      NOW,
    );
    expect(view.title).toBe('상태 확인에서 이상이 있어 보증금이 보류됐어요');
    expect(view.rows).toEqual([
      { label: '보증금', value: '50,000원' },
      { label: '연체료', value: '−15,000원', tone: 'danger' },
      { label: '보류 시작', value: '11월 18일 09:30' },
    ]);
    expect(view.notes).toEqual(['차감을 제안하면 대여자가 수락하거나 거절해요']);
    expect(view.proposal).toBeNull();
  });

  it('HELD(미반납 신고) 대여자, 응답 대기 제안 + 보류 7일 지남', () => {
    const view = getDepositView(
      rental({
        depositStatus: 'HELD',
        depositHeldAt: '2026-11-10T09:00:00',
        latestDeductionProposal: proposal({ grade: 'D', amount: 50000 }),
      }),
      { result: null },
      TODAY,
      NOW,
    );
    expect(view.title).toBe('미반납 신고로 보증금이 보류됐어요');
    // 응답을 기다리는 제안이 있으면 관리자 확정 안내를 붙이지 않는다(409 DEDUCTION_PROPOSAL_IN_PROGRESS)
    expect(view.notes).toEqual([]);
    expect(view.proposal).toMatchObject({
      state: 'OPEN',
      gradeLabel: 'D 전손·분실',
      amountText: '50,000원',
      deadlineText: '11월 22일 10:00까지 응답이 없으면 자동으로 수락돼요',
      evidenceImageUrls: ['/a.jpg'],
    });
  });

  it('보류 7일 지남: 기한 지난 제안(CLOSED)은 안내 없음, 거절된 제안은 관리자 확정 안내', () => {
    const held = { depositStatus: 'HELD', depositHeldAt: '2026-11-10T09:00:00' };
    const closed = getDepositView(
      rental({
        ...held,
        latestDeductionProposal: proposal({ respondDeadline: '2026-11-19T10:00:00' }),
      }),
      { result: 'ABNORMAL' },
      TODAY,
      NOW,
    );
    expect(closed.proposal.state).toBe('CLOSED');
    expect(closed.notes).toEqual([]);
    const rejected = getDepositView(
      rental({
        ...held,
        myRole: 'OWNER',
        latestDeductionProposal: proposal({ status: 'REJECTED' }),
      }),
      { result: 'ABNORMAL' },
      TODAY,
      NOW,
    );
    expect(rejected.notes).toEqual([
      '대여자가 제안을 거절했어요. 금액을 조정해 다시 제안할 수 있어요',
      '보류 후 7일이 지나 관리자가 차감 금액을 정해요',
    ]);
  });

  it('HELD 제공자, 거절된 제안: 다시 제안 안내', () => {
    const view = getDepositView(
      rental({
        myRole: 'OWNER',
        depositStatus: 'HELD',
        latestDeductionProposal: proposal({ status: 'REJECTED' }),
      }),
      { result: 'ABNORMAL' },
      TODAY,
      NOW,
    );
    expect(view.notes).toEqual(['대여자가 제안을 거절했어요. 금액을 조정해 다시 제안할 수 있어요']);
    expect(view.proposal.deadlineText).toBeNull();
  });

  it('REFUNDED: 연체료·차감을 뺀 반환액, 누적 환불액, 차감 사유', () => {
    const view = getDepositView(
      rental({
        status: 'COMPLETED',
        depositStatus: 'REFUNDED',
        overdueFee: 15000,
        deduction: { grade: 'C', amount: 20000, reason: '폴대 휨' },
        refundedAmount: 15000,
      }),
      { result: 'ABNORMAL' },
      TODAY,
      NOW,
    );
    expect(view.title).toBe('보증금 15,000원을 돌려드렸어요');
    expect(view.rows).toEqual([
      { label: '보증금', value: '50,000원' },
      { label: '연체료', value: '−15,000원', tone: 'danger' },
      { label: '보증금 차감(C 부분 손상)', value: '−20,000원', tone: 'danger' },
      { label: '반환액', value: '15,000원' },
      { label: '누적 환불액', value: '15,000원' },
    ]);
    expect(view.notes).toEqual(['차감 사유: 폴대 휨']);
  });

  it('FORFEITED·REFUND_FAILED 문구', () => {
    expect(
      getDepositView(
        rental({ status: 'COMPLETED', depositStatus: 'FORFEITED', overdueFee: 50000 }),
        null,
        TODAY,
        NOW,
      ).title,
    ).toBe('연체료로 보증금이 모두 쓰였어요');
    expect(
      getDepositView(
        rental({
          status: 'COMPLETED',
          depositStatus: 'FORFEITED',
          deduction: { grade: 'D', amount: 50000, reason: '분실' },
        }),
        { result: null },
        TODAY,
        NOW,
      ).title,
    ).toBe('보증금이 전액 차감됐어요');
    expect(getDepositView(rental({ depositStatus: 'REFUND_FAILED' }), null, TODAY, NOW).title).toBe(
      '보증금 반환이 늦어지고 있어요. 자동으로 다시 시도해요',
    );
  });

  it('REFUNDED 제공자 문구', () => {
    expect(
      getDepositView(
        rental({ myRole: 'OWNER', status: 'COMPLETED', depositStatus: 'REFUNDED' }),
        null,
        TODAY,
        NOW,
      ).title,
    ).toBe('대여자에게 보증금 50,000원을 돌려줬어요');
  });
});
