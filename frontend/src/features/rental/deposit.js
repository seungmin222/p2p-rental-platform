// 대여 상세(SCR-08)의 보증금 보류·차감(REQ-049)·미반납 신고(REQ-018)·연체료(REQ-023) 판단과 표시.
// 상세 응답 필드(depositStatus·depositHeldAt·deduction·latestDeductionProposal·overdueFee·refundedAmount·
// depositRefundPending)만 보고 정한다. node 환경에서 테스트한다.
import { compareDate } from '../../shared/lib/date';
import { formatPrice, parseServerDateTime } from '../../shared/lib/format';
import { DAMAGE_GRADE } from '../payment/constants';
import { DEDUCTION_POLICY, LOST_REPORT_AFTER_DAYS } from './constants';
import { formatDateTime, overdueDays } from './rentalData';

const HOUR_MS = 60 * 60 * 1000;

/**
 * 미반납 신고로 생긴 보류인가. 상세에 표시 필드가 없어(open-issues #57) "HELD이고 RETURN 기록이 판정 전"으로 본다:
 * 상태 확인 '이상'은 항상 result ABNORMAL을 남기고, 신고는 판정 없이 보류한다(rental.md REQ-018).
 * RETURN 기록을 아직 못 불러왔으면(undefined) 판단하지 않는다(false).
 */
export function isLostReportHold(rental, returnInspection) {
  return (
    rental.depositStatus === 'HELD' && returnInspection != null && returnInspection.result == null
  );
}

/** 미반납 신고 버튼: 제공자이고 OVERDUE이며 오늘 > 반납 예정일 + lost-report.after-days. 그 전에는 숨긴다 */
export function canReportLost(rental, today) {
  return (
    rental.status === 'OVERDUE' &&
    rental.myRole === 'OWNER' &&
    overdueDays(rental.endDate, today) > LOST_REPORT_AFTER_DAYS
  );
}

/**
 * 최신 차감 제안의 진행 상태.
 * - NONE: 제안 없음
 * - OPEN: PROPOSED이고 응답 기한 전(대여자가 수락·거절할 수 있다)
 * - CLOSED: PROPOSED인데 기한이 지남(배치가 자동 수락하기 전, 응답하면 409 — open-issues #43)
 * - REJECTED: 거절됨(제공자가 다시 제안할 수 있다)
 * - DONE: 수락·자동 수락·관리자 확정
 * @param {number} now 밀리초
 */
export function getProposalState(proposal, now) {
  if (!proposal) {
    return 'NONE';
  }
  if (proposal.status === 'PROPOSED') {
    const deadline = parseServerDateTime(proposal.respondDeadline)?.getTime();
    return deadline != null && deadline <= now ? 'CLOSED' : 'OPEN';
  }
  return proposal.status === 'REJECTED' ? 'REJECTED' : 'DONE';
}

/** 보류 후 deposit.hold-agreement-days가 지나 관리자 확정 대상인가(now는 밀리초) */
export function isAdminDecisionDue(rental, now) {
  if (rental.depositStatus !== 'HELD') {
    return false;
  }
  const heldAt = parseServerDateTime(rental.depositHeldAt)?.getTime();
  return heldAt != null && heldAt + DEDUCTION_POLICY.HOLD_AGREEMENT_DAYS * 24 * HOUR_MS <= now;
}

/** 차감 제안 시트의 등급 선택지. A는 차감 불가, 미반납 신고 건은 D만(payment.md REQ-049) */
export function getDeductionGrades(isLostReport) {
  return isLostReport ? ['D'] : ['B', 'C', 'D'];
}

/**
 * 차감 금액 입력(문자열)을 읽는다. 쉼표·공백은 지운다. 0 ~ maxAmount 정수가 아니면 error.
 * maxAmount를 모르면(정책을 못 불러옴) 상한 검사는 서버에 맡긴다.
 */
export function parseDeductionAmount(text, maxAmount) {
  const cleaned = String(text ?? '').replace(/[,\s]/g, '');
  if (cleaned === '') {
    return { value: null, error: '차감 금액을 입력해 주세요' };
  }
  if (!/^\d+$/.test(cleaned)) {
    return { value: null, error: '숫자로 입력해 주세요' };
  }
  const value = Number(cleaned);
  if (!Number.isSafeInteger(value)) {
    return { value: null, error: '숫자로 입력해 주세요' };
  }
  if (maxAmount != null && value > maxAmount) {
    return { value: null, error: `${formatPrice(maxAmount)}까지 제안할 수 있어요` };
  }
  return { value, error: null };
}

/**
 * 차감 제안 사진 규칙(open-issues #38·#40). 장수 상한은 ImageUploader가 막는다.
 * 손상 사진은 1장 이상(미반납 신고 건은 0장도 됨), 영수증은 정책이 ACTUAL(receiptRequired)이면 1장 이상.
 * 틀린 곳이 없으면 null.
 */
export function validateDeductionImages({
  evidenceCount,
  receiptCount,
  isLostReport,
  receiptRequired,
}) {
  const errors = {};
  if (!isLostReport && evidenceCount < 1) {
    errors.evidenceImages = '손상 사진을 1장 이상 올려 주세요';
  }
  if (receiptRequired && receiptCount < 1) {
    errors.receiptImages = '견적서·영수증 사진을 1장 이상 올려 주세요';
  }
  return Object.keys(errors).length > 0 ? errors : null;
}

/** 연체료가 아직 확정 전("현재까지")인가: OVERDUE이거나 RENTING인데 반납 예정일이 지남(openapi overdueFee) */
function isOverdueFeeLive(rental, today) {
  return (
    rental.status === 'OVERDUE' ||
    (rental.status === 'RENTING' && compareDate(today, rental.endDate) > 0)
  );
}

/** 돌려준(돌려줄) 보증금 = 보증금 − 연체료 − 차감액, 0 미만은 0 */
export function depositReturnAmount(rental) {
  return Math.max(0, rental.deposit - (rental.overdueFee ?? 0) - (rental.deduction?.amount ?? 0));
}

function heldTitle(isLostReport) {
  return isLostReport
    ? '미반납 신고로 보증금이 보류됐어요'
    : '상태 확인에서 이상이 있어 보증금이 보류됐어요';
}

function heldDescription(isOwner, proposalState, adminDue) {
  const lines = [];
  if (isOwner && (proposalState === 'NONE' || proposalState === 'REJECTED')) {
    lines.push(
      proposalState === 'REJECTED'
        ? '대여자가 제안을 거절했어요. 금액을 조정해 다시 제안할 수 있어요'
        : '차감을 제안하면 대여자가 수락하거나 거절해요',
    );
  }
  if (!isOwner && proposalState === 'NONE') {
    lines.push('제공자가 차감 금액을 정리하고 있어요');
  }
  // 응답을 기다리는 제안(기한 지나 자동 수락 대기 포함)이 있으면 관리자 확정은 409라 안내하지 않는다(REQ-049 5번)
  const awaitingResponse = proposalState === 'OPEN' || proposalState === 'CLOSED';
  if (adminDue && !awaitingResponse) {
    lines.push(
      `보류 후 ${DEDUCTION_POLICY.HOLD_AGREEMENT_DAYS}일이 지나 관리자가 차감 금액을 정해요`,
    );
  }
  return lines;
}

function statusText(rental, isOwner, isLostReport) {
  const back = depositReturnAmount(rental);
  switch (rental.depositStatus) {
    case 'FROZEN':
      if (rental.status === 'RETURNED' && rental.depositRefundPending) {
        return '보증금 반환을 처리하고 있어요';
      }
      return isOwner
        ? '대여자의 보증금을 플랫폼이 보관하고 있어요'
        : '보증금을 보관하고 있어요. 반납을 확인하면 돌려드려요';
    case 'HELD':
      return heldTitle(isLostReport);
    case 'REFUNDED':
      return isOwner
        ? `대여자에게 보증금 ${formatPrice(back)}을 돌려줬어요`
        : `보증금 ${formatPrice(back)}을 돌려드렸어요`;
    case 'FORFEITED':
      // 차감 확정 없이 FORFEITED면 연체료가 보증금을 다 썼다(REQ-025)
      return rental.deduction ? '보증금이 전액 차감됐어요' : '연체료로 보증금이 모두 쓰였어요';
    case 'REFUND_FAILED':
      return '보증금 반환이 늦어지고 있어요. 자동으로 다시 시도해요';
    default:
      return '';
  }
}

/** 최신 차감 제안 표시. 없으면 null */
export function getProposalView(proposal, now) {
  if (!proposal) {
    return null;
  }
  const state = getProposalState(proposal, now);
  let deadlineText = null;
  if (state === 'OPEN') {
    deadlineText = `${formatDateTime(proposal.respondDeadline)}까지 응답이 없으면 자동으로 수락돼요`;
  } else if (state === 'CLOSED') {
    deadlineText = '응답 기한이 지나 자동 수락을 처리하고 있어요';
  }
  return {
    state,
    statusCode: proposal.status,
    gradeLabel: DAMAGE_GRADE[proposal.grade]?.label ?? proposal.grade,
    amountText: formatPrice(proposal.amount),
    reason: proposal.reason,
    proposedAtText: formatDateTime(proposal.proposedAt),
    deadlineText,
    evidenceImageUrls: proposal.evidenceImageUrls ?? [],
    receiptImageUrls: proposal.receiptImageUrls ?? [],
  };
}

/**
 * 대여 상세 보증금 카드. depositStatus가 null(승인된 결제 없음)·NONE(보증금 0원)이면 null.
 * @param {object} rental RentalDetailResponse
 * @param {object | null | undefined} returnInspection RETURN 기록(미반납 신고 판단용)
 * @param {string} today "YYYY-MM-DD"
 * @param {number} now 밀리초
 * @returns {{status: string, title: string, rows: {label: string, value: string, tone?: string}[],
 *   notes: string[], proposal: object | null} | null}
 */
export function getDepositView(rental, returnInspection, today, now) {
  if (!rental.depositStatus || rental.depositStatus === 'NONE') {
    return null;
  }
  const isOwner = rental.myRole === 'OWNER';
  const isLostReport = isLostReportHold(rental, returnInspection);
  const rows = [{ label: '보증금', value: formatPrice(rental.deposit) }];
  if (rental.overdueFee > 0) {
    rows.push({
      label: isOverdueFeeLive(rental, today) ? '현재까지 연체료' : '연체료',
      value: `−${formatPrice(rental.overdueFee)}`,
      tone: 'danger',
    });
  }
  if (rental.deduction) {
    rows.push({
      label: `보증금 차감(${DAMAGE_GRADE[rental.deduction.grade]?.label ?? rental.deduction.grade})`,
      value: `−${formatPrice(rental.deduction.amount)}`,
      tone: 'danger',
    });
  }
  if (rental.depositStatus === 'REFUNDED' || rental.depositStatus === 'FORFEITED') {
    rows.push({ label: '반환액', value: formatPrice(depositReturnAmount(rental)) });
  }
  if (rental.refundedAmount > 0) {
    rows.push({ label: '누적 환불액', value: formatPrice(rental.refundedAmount) });
  }
  if (rental.depositHeldAt) {
    rows.push({ label: '보류 시작', value: formatDateTime(rental.depositHeldAt) });
  }

  const notes = [];
  if (rental.deduction?.reason) {
    notes.push(`차감 사유: ${rental.deduction.reason}`);
  }
  if (rental.depositStatus === 'FROZEN' && isOverdueFeeLive(rental, today)) {
    notes.push('연체료는 보증금에서 먼저 빠져요');
  }
  const isHeld = rental.depositStatus === 'HELD';
  if (isHeld) {
    const proposalState = getProposalState(rental.latestDeductionProposal, now);
    notes.push(...heldDescription(isOwner, proposalState, isAdminDecisionDue(rental, now)));
  }

  return {
    status: rental.depositStatus,
    title: statusText(rental, isOwner, isLostReport),
    rows,
    notes,
    // 확정 뒤에는 deduction 줄로 충분하다. 협의 중(HELD)일 때만 최신 제안을 보인다
    proposal: isHeld ? getProposalView(rental.latestDeductionProposal, now) : null,
  };
}
