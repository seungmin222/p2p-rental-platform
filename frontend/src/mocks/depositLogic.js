// 보증금 보류·차감(REQ-049)·미반납 신고(REQ-018)·연체료(REQ-023) 목의 순수 함수. node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값을 여기 따로 둔다(policies.md).
//
// 대여 목 rental에 쌓는 보증금 기록(rental.depositExtra, paymentHistoryLogic.js PAYMENT_SEED_EXTRA와 같은 모양):
// { overdueFee(반납·신고 때 확정), held(보류 중), heldAt, lostReported, deduction({grade, amount, reason}, 확정) }
// 차감 제안은 db.deductionProposals(대여 목 저장소)에 둔다.
import { compareDate, countDaysInclusive } from '../shared/lib/date';

// policies.md overdue-fee.rate (연체 1일당 일 대여료 × 150%, 원 미만 절사, 보증금 한도)
export const OVERDUE_FEE_RATE = 1.5;
// policies.md lost-report.after-days
export const LOST_REPORT_AFTER_DAYS = 7;
// policies.md deduction.reason.max · deduction.renter-response-hours
export const DEDUCTION_REASON_MAX = 500;
export const DEDUCTION_RESPONSE_HOURS = 72;
// policies.md inspection.images (차감 증빙·영수증도 같은 값, open-issues #40)
export const DEDUCTION_IMAGES_MAX = 5;

// policies.md §4 카테고리별 정책(categoryId = V3 순서). fixedB null = 공구 소모품 실비
const DAMAGE_POLICIES = {
  1: { fixedB: 10000, capRateC: 0.5 }, // CAMPING
  2: { fixedB: null, capRateC: 0.5 }, // TOOL
  3: { fixedB: 10000, capRateC: 0.7 }, // CAMERA
  4: { fixedB: 20000, capRateC: 0.5 }, // BABY
  5: { fixedB: 10000, capRateC: 0.5 }, // PARTY
  6: { fixedB: 10000, capRateC: 0.5 }, // HOBBY
  7: { fixedB: 10000, capRateC: 0.5 }, // APPLIANCE
  8: { fixedB: 10000, capRateC: 0.5 }, // ETC
};

const GRADES = ['A', 'B', 'C', 'D'];

/** 연체 일수 = 기준일 − 반납 예정일(지나지 않았으면 0) */
export function overdueDaysOf(endDate, untilDate) {
  if (compareDate(untilDate, endDate) <= 0) {
    return 0;
  }
  return countDaysInclusive(endDate, untilDate) - 1;
}

/** 연체료 = min(연체 일수 × floor(일 대여료 × 1.5), 보증금). 보증금 0원이면 0(policies.md "무료 대여") */
export function calcOverdueFee(rental, untilDate) {
  if (rental.deposit <= 0) {
    return 0;
  }
  const perDay = Math.floor(rental.pricePerDay * OVERDUE_FEE_RATE);
  return Math.min(overdueDaysOf(rental.endDate, untilDate) * perDay, rental.deposit);
}

/**
 * 상세 응답 overdueFee: OVERDUE이거나 RENTING인데 반납 예정일이 지났으면 오늘까지(미확정),
 * 그 밖에는 반납·신고 때 확정된 값(없으면 0)
 */
export function overdueFeeOf(rental, extra, today) {
  const isLive =
    rental.status === 'OVERDUE' ||
    (rental.status === 'RENTING' && compareDate(today, rental.endDate) > 0);
  if (isLive) {
    return calcOverdueFee(rental, today);
  }
  return Math.min(extra.overdueFee ?? 0, rental.deposit);
}

/** 미반납 신고 가능: 오늘 > 반납 예정일 + lost-report.after-days */
export function isLostReportOpen(rental, today) {
  return overdueDaysOf(rental.endDate, today) > LOST_REPORT_AFTER_DAYS;
}

/**
 * GET deposit/policy 계산(policies.md §4 계산 규칙). 등급이 틀리면 { error: code }.
 * @returns {{grade, deductionType, deposit, overdueFee, remainingDeposit, maxAmount, receiptRequired} | {error}}
 */
export function deductionPolicyOf({ deposit, overdueFee, categoryId, grade, isLostReport }) {
  if (!GRADES.includes(grade)) {
    return { error: 'COMMON_INVALID_INPUT' };
  }
  if (grade === 'A' || (isLostReport && grade !== 'D')) {
    return { error: 'DEDUCTION_GRADE_NOT_ALLOWED' };
  }
  const policy = DAMAGE_POLICIES[categoryId] ?? DAMAGE_POLICIES[8];
  const remainingDeposit = Math.max(0, deposit - overdueFee);
  let deductionType;
  let cap;
  if (grade === 'B') {
    deductionType = policy.fixedB == null ? 'ACTUAL' : 'FIXED';
    cap = policy.fixedB ?? remainingDeposit;
  } else if (grade === 'C') {
    deductionType = 'ACTUAL';
    cap = Math.floor(deposit * policy.capRateC);
  } else {
    deductionType = 'FULL';
    cap = remainingDeposit;
  }
  return {
    grade,
    deductionType,
    deposit,
    overdueFee,
    remainingDeposit,
    maxAmount: Math.min(cap, remainingDeposit),
    receiptRequired: deductionType === 'ACTUAL',
  };
}

/**
 * PROPOSE 검사(payment.md REQ-049 순서, 진행 중 제안·보류 여부는 호출부가 먼저 본다). 통과하면 null,
 * 아니면 { code, details? }.
 */
export function validateProposal(policy, { amount, evidenceCount, receiptCount, isLostReport }) {
  if (policy.error) {
    return { code: policy.error };
  }
  if (amount > policy.remainingDeposit) {
    return { code: 'DEDUCTION_EXCEEDS_DEPOSIT' };
  }
  if (amount > policy.maxAmount) {
    return {
      code: 'DEDUCTION_EXCEEDS_CAP',
      details: [{ field: 'maxAmount', reason: String(policy.maxAmount) }],
    };
  }
  if ((!isLostReport && evidenceCount < 1) || (policy.receiptRequired && receiptCount < 1)) {
    return { code: 'DEDUCTION_EVIDENCE_REQUIRED' };
  }
  if (evidenceCount > DEDUCTION_IMAGES_MAX || receiptCount > DEDUCTION_IMAGES_MAX) {
    return { code: 'IMAGE_COUNT_INVALID' };
  }
  return null;
}

/** 응답 기한이 지난 PROPOSED인가(now는 서버 형식 문자열, 같은 형식끼리 사전순 비교) */
export function isProposalClosed(proposal, now) {
  return proposal.status === 'PROPOSED' && proposal.respondDeadline <= now;
}

/** 대여의 최신 제안(proposalId가 가장 큰 것). 없으면 null */
export function latestProposalOf(proposals, rentalId) {
  return (
    proposals
      .filter((proposal) => proposal.rentalId === rentalId)
      .sort((a, b) => b.proposalId - a.proposalId)[0] ?? null
  );
}

/** Proposal 응답(rentalId는 응답에 없다) */
export function toProposalResponse(proposal) {
  if (!proposal) {
    return null;
  }
  return {
    proposalId: proposal.proposalId,
    status: proposal.status,
    grade: proposal.grade,
    amount: proposal.amount,
    reason: proposal.reason,
    evidenceImageUrls: proposal.evidenceImageUrls,
    receiptImageUrls: proposal.receiptImageUrls,
    proposedAt: proposal.proposedAt,
    respondDeadline: proposal.respondDeadline,
    respondedAt: proposal.respondedAt,
  };
}

/** 확정: 반환액 = 보증금 − 연체료 − 차감액. 0이면 FORFEITED(PG 호출 없음), 아니면 REFUNDED */
export function settleDeduction(deposit, overdueFee, amount) {
  const refund = Math.max(0, deposit - overdueFee - amount);
  return { refund, depositStatus: refund > 0 ? 'REFUNDED' : 'FORFEITED' };
}
