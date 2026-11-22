// 대여 API 목. 요청·응답 형태는 docs/openapi.json 스키마 그대로이고, 메모리 저장소로 전이까지 흉내 낸다.
// 시드(어떤 id가 어떤 경우인지)는 rentalSeed.js 맨 위 표. 검사 순서는 백엔드와 같게(404 → 403 → 409 → 사진).
import { delay, http } from 'msw';

import {
  addDays,
  buildMonthGrid,
  compareDate,
  countDaysInclusive,
  monthOf,
  todayInSeoul,
} from '../shared/lib/date';
import { CONFLICT_ITEM_ID, findMockItem, listMyMockItems, MOCK_ME, MOCK_MEMBERS } from './item';
import {
  countUpcomingRentals,
  HISTORY_TAB_STATUSES,
  isRentingItem,
  listItemRentals,
  listMyItems,
  listRentalHistory,
  MOCK_MY_ITEM_TABS,
  readPage,
  readPathId,
  toPageResponse,
} from './pageList';
import {
  calcOverdueFee,
  DEDUCTION_REASON_MAX,
  DEDUCTION_RESPONSE_HOURS,
  deductionPolicyOf,
  isLostReportOpen,
  isProposalClosed,
  latestProposalOf,
  overdueFeeOf,
  settleDeduction,
  toProposalResponse,
  validateProposal,
} from './depositLogic';
import {
  isRentalPaid,
  paymentExtraOf,
  rentalFeeOf,
  rentalPaymentState,
} from './paymentHistoryLogic';
import { scheduleAiCompare, toAiResultResponse } from './aiCompare';
import { hasFrozenDeposit } from './paymentLogic';
import { DEPOSIT_REFUND_FAILED, takeFailure } from './paymentScenario';
import { fail, ok } from './response';
import { minutesFromNow } from './rentalSeed';
import { db, isPendingExtension, listOccupied, nowServer, saveDb } from './rentalStore';
import { canWriteReview as canWriteReviewOf, hasWritten } from './reviewLogic';
import { authenticateMockRequest } from './session';

const PAYMENT_ENABLED = import.meta.env.VITE_PAYMENT_ENABLED === 'true';

// 이 요청을 보낸 회원. AT가 없거나 무효면 예전처럼 회원 1로 본다(대여 목은 401을 내지 않는다)
let requestMemberId = MOCK_ME;

export function currentMemberId() {
  return requestMemberId;
}

// 정책 값(policies.md). 목은 서버 흉내라 화면 상수(features)를 가져오지 않고 따로 둔다.
const POLICY = {
  MIN_DAYS: 1,
  MAX_DAYS: 30,
  PAYMENT_EXPIRE_MINUTES: 15,
  CANCEL_AGREEMENT_EXPIRE_HOURS: 48,
  CANCEL_REASON_TEXT_MAX: 200,
  HANDOVER_MEMO_MAX: 300,
  RETURN_NOTE_MAX: 300,
  ABNORMAL_NOTE_MAX: 500,
  IMAGES_MIN: 1,
  IMAGES_MAX: 5,
  CANCEL_FEE_FREE_UNTIL_DAYS_BEFORE: 3,
  CANCEL_FEE_RATE_PERCENT: 30,
  PAYMENT_EXPIRE_BATCH_MINUTES: 1, // policies.md 배치 주기 "결제 만료"(연장 결제 만료도 1분)
  EXTENSION_MAX_COUNT: 2, // extension.max-count
  EXTENSION_MAX_DAYS: 7, // extension.max-days-per
};

// openapi.json 응답 예시의 메시지
const MESSAGES = {
  COMMON_INVALID_INPUT: '입력값이 올바르지 않습니다.',
  COMMON_UNSUPPORTED_MEDIA_TYPE: '지원하지 않는 요청 형식입니다.',
  ITEM_NOT_FOUND: '물품을 찾을 수 없습니다.',
  ITEM_NOT_OWNER: '물품 등록자만 처리할 수 있습니다.',
  IMAGE_COUNT_INVALID: '이미지 장수가 올바르지 않습니다.',
  RENTAL_INVALID_PERIOD: '대여 기간이 올바르지 않습니다.',
  RENTAL_OUT_OF_AVAILABLE_PERIOD: '대여 가능 기간을 벗어났습니다.',
  RENTAL_OWN_ITEM: '본인 물품은 예약할 수 없습니다.',
  RENTAL_PERIOD_CONFLICT: '이미 예약된 기간입니다.',
  RENTAL_NOT_PARTICIPANT: '대여 당사자만 이용할 수 있습니다.',
  RENTAL_NOT_FOUND: '대여 내역을 찾을 수 없습니다.',
  RENTAL_OWNER_ONLY: '물품 제공자만 처리할 수 있습니다.',
  RENTAL_LOST_REPORT_TOO_EARLY:
    '반납 예정일로부터 정해진 기간이 지난 연체 건만 미반납 신고할 수 있습니다.',
  DEPOSIT_NOT_HELD: '보증금이 보류 중이 아닙니다.',
  DEDUCTION_PROPOSAL_IN_PROGRESS: '응답을 기다리는 차감 제안이 있습니다.',
  DEDUCTION_GRADE_NOT_ALLOWED: '선택할 수 없는 손상 등급입니다.',
  DEDUCTION_EXCEEDS_DEPOSIT: '차감액이 남은 보증금을 넘습니다.',
  DEDUCTION_EXCEEDS_CAP: '차감액이 등급별 상한을 넘습니다.',
  DEDUCTION_EVIDENCE_REQUIRED: '증빙 자료가 필요합니다.',
  DEDUCTION_PROPOSAL_NOT_FOUND: '차감 제안을 찾을 수 없습니다.',
  DEDUCTION_PROPOSAL_CLOSED: '응답 기한이 지난 차감 제안입니다.',
  RENTAL_INVALID_TRANSITION: '현재 대여 상태에서는 처리할 수 없는 요청입니다.',
  RENTAL_HANDOVER_TOO_EARLY: '대여 시작일부터 인계를 확인할 수 있습니다.',
  RENTAL_CANCEL_NOT_ALLOWED:
    '대여 시작일이 지나 단독으로 취소할 수 없습니다. 상대방에게 취소 동의를 요청해 주세요.',
  RENTAL_CANCEL_AGREEMENT_TOO_EARLY: '대여 시작일 전에는 단독으로 취소할 수 있습니다.',
  RENTAL_CANCEL_AGREEMENT_IN_PROGRESS: '이미 진행 중인 취소 동의 요청이 있습니다.',
  RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE: '본인이 보낸 취소 동의 요청에는 응답할 수 없습니다.',
  RENTAL_CANCEL_AGREEMENT_NOT_FOUND: '취소 동의 요청을 찾을 수 없습니다.',
  RENTAL_CANCEL_AGREEMENT_CLOSED: '이미 응답했거나 만료된 취소 동의 요청입니다.',
  PAYMENT_NOT_FOUND: '결제 내역을 찾을 수 없습니다.',
  PAYMENT_INVALID_STATUS: '현재 결제 상태에서는 처리할 수 없는 요청입니다.',
  PAYMENT_RENTER_ONLY: '대여자만 결제할 수 있습니다.',
  PAYMENT_EXPIRED: '결제 가능 시간이 지났습니다. 다시 예약해 주세요.',
  PAYMENT_AMOUNT_MISMATCH: '결제 금액이 일치하지 않아 예약이 취소되었습니다.',
  PAYMENT_APPROVAL_FAILED: '결제 승인에 실패했습니다. 다시 결제해 주세요.',
  PAYMENT_RESULT_UNKNOWN: '결제 결과를 확인하고 있습니다. 잠시 후 다시 시도해 주세요.',
  PAYMENT_CANCELED_AFTER_EXPIRY:
    '결제 처리 중 예약 또는 연장이 만료·취소되어 결제 금액을 전액 환불합니다.',
  RENTAL_RENTER_ONLY: '대여자만 처리할 수 있습니다.',
  RENTAL_EXTENSION_LIMIT_EXCEEDED: '연장 횟수 또는 한 번에 연장할 수 있는 일수를 넘었습니다.',
  RENTAL_EXTENSION_IN_PROGRESS: '결제를 기다리는 연장 요청이 있습니다.',
  RENTAL_EXTENSION_OVERDUE: '연체 중에는 연장할 수 없습니다. 먼저 반납해 주세요.',
  RENTAL_EXTENSION_PERIOD_CONFLICT: '연장하려는 기간에 다른 예약이 있습니다.',
  RENTAL_EXTENSION_NOT_FOUND: '연장 요청을 찾을 수 없습니다.',
  REVIEW_ALREADY_WRITTEN: '이미 후기를 작성했습니다.',
  REVIEW_DEADLINE_PASSED: '후기 작성 기간이 지났습니다.',
  MEMBER_NOT_FOUND: '회원을 찾을 수 없습니다.',
};

const STATUS_BY_CODE = {
  COMMON_INVALID_INPUT: 400,
  IMAGE_COUNT_INVALID: 400,
  RENTAL_INVALID_PERIOD: 400,
  RENTAL_OUT_OF_AVAILABLE_PERIOD: 400,
  RENTAL_OWN_ITEM: 400,
  RENTAL_NOT_PARTICIPANT: 403,
  RENTAL_OWNER_ONLY: 403,
  ITEM_NOT_OWNER: 403,
  RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE: 403,
  ITEM_NOT_FOUND: 404,
  RENTAL_NOT_FOUND: 404,
  RENTAL_CANCEL_AGREEMENT_NOT_FOUND: 404,
  COMMON_UNSUPPORTED_MEDIA_TYPE: 415,
  PAYMENT_AMOUNT_MISMATCH: 400,
  PAYMENT_APPROVAL_FAILED: 400,
  PAYMENT_RENTER_ONLY: 403,
  PAYMENT_NOT_FOUND: 404,
  PAYMENT_RESULT_UNKNOWN: 503,
  REVIEW_DEADLINE_PASSED: 400,
  MEMBER_NOT_FOUND: 404,
  RENTAL_RENTER_ONLY: 403,
  RENTAL_EXTENSION_LIMIT_EXCEEDED: 400,
  RENTAL_EXTENSION_NOT_FOUND: 404,
  DEDUCTION_GRADE_NOT_ALLOWED: 400,
  DEDUCTION_EXCEEDS_DEPOSIT: 400,
  DEDUCTION_EXCEEDS_CAP: 400,
  DEDUCTION_EVIDENCE_REQUIRED: 400,
  DEDUCTION_PROPOSAL_NOT_FOUND: 404,
};

/** 검사에서 던지고 핸들러가 공통 실패 응답으로 바꾼다 */
class MockError extends Error {
  constructor(code, details = []) {
    super(code);
    this.code = code;
    this.details = details;
  }
}

export function reject(code, details) {
  throw new MockError(code, details);
}

export function invalidInput(field, reason) {
  reject('COMMON_INVALID_INPUT', [{ field, reason }]);
}

/** 예외를 실패 응답으로. 지연을 조금 넣어 로딩·제출 중 상태를 볼 수 있게 한다. */
export function handle(resolver) {
  return async (info) => {
    await delay(400);
    requestMemberId = authenticateMockRequest(info.request)?.memberId ?? MOCK_ME;
    const hasExpired = expirePendingPayments() | expirePendingExtensions();
    try {
      const response = await resolver(info);
      if (hasExpired || info.request.method !== 'GET') {
        saveDb();
      }
      return response;
    } catch (error) {
      // 실패 응답에도 전이가 남는 경우가 있다(금액 불일치 → 취소)
      saveDb();
      if (error instanceof MockError) {
        return fail(
          STATUS_BY_CODE[error.code] ?? 409,
          error.code,
          MESSAGES[error.code],
          error.details,
        );
      }
      throw error;
    }
  };
}

function isOpen(agreement) {
  return agreement.status === 'REQUESTED' && agreement.expiresAt > nowServer();
}

export function roleOf(rental) {
  if (rental.renterId === currentMemberId()) {
    return 'RENTER';
  }
  return rental.ownerId === currentMemberId() ? 'OWNER' : null;
}

/** 404 → 403(당사자) 순서로 대여를 찾는다 */
export function findParticipantRental(rentalId) {
  const rental = db.rentals.find((candidate) => candidate.rentalId === Number(rentalId));
  if (!rental) {
    reject('RENTAL_NOT_FOUND');
  }
  if (!roleOf(rental)) {
    reject('RENTAL_NOT_PARTICIPANT');
  }
  return rental;
}

function requireOwner(rental) {
  if (roleOf(rental) !== 'OWNER') {
    reject('RENTAL_OWNER_ONLY');
  }
}

function requireStatus(rental, ...statuses) {
  if (!statuses.includes(rental.status)) {
    reject('RENTAL_INVALID_TRANSITION');
  }
}

/**
 * 앞 대여 연체(open-issues #7): RESERVED·시작일 ≤ 오늘이고, 같은 물품의 앞 대여(RENTING·OVERDUE)가 종료일이 지났는데
 * 반납되지 않았는지. 백엔드 RentalRepository.existsPreviousOverdue와 같은 조건
 */
function hasPreviousOverdue(rental, today = todayInSeoul()) {
  if (rental.status !== 'RESERVED' || compareDate(today, rental.startDate) < 0) {
    return false;
  }
  return db.rentals.some(
    (other) =>
      other.itemId === rental.itemId &&
      other.rentalId !== rental.rentalId &&
      (other.status === 'RENTING' || other.status === 'OVERDUE') &&
      compareDate(other.startDate, rental.startDate) < 0 &&
      compareDate(other.endDate, today) < 0,
  );
}

/** RentalDetailResponse */
function toDetail(rental) {
  const myRole = roleOf(rental);
  const item = findMockItem(rental.itemId);
  const counterpartId = myRole === 'RENTER' ? rental.ownerId : rental.renterId;
  const rentalDays = countDaysInclusive(rental.startDate, rental.endDate);
  const rentalFee = rental.pricePerDay * rentalDays;
  const agreement =
    rental.status === 'RESERVED'
      ? db.agreements.find((a) => a.rentalId === rental.rentalId && isOpen(a))
      : null;
  return {
    rentalId: rental.rentalId,
    status: rental.status,
    myRole,
    item: {
      itemId: item.itemId,
      name: item.name,
      thumbnailUrl: item.thumbnailUrl,
      isDeleted: item.isDeleted,
    },
    counterpart: { memberId: counterpartId, nickname: MOCK_MEMBERS[counterpartId].nickname },
    startDate: rental.startDate,
    endDate: rental.endDate,
    rentalDays,
    pricePerDay: rental.pricePerDay,
    rentalFee,
    deposit: rental.deposit,
    totalAmount: rentalFee + rental.deposit,
    createdAt: rental.createdAt,
    paymentExpiresAt: rental.status === 'PENDING_PAYMENT' ? rental.paymentExpiresAt : null,
    handedOverAt: rental.handedOverAt,
    returnedAt: rental.returnedAt,
    completedAt: rental.completedAt,
    canceledAt: rental.canceledAt,
    cancelType: rental.cancelType,
    cancelReason: rental.cancelReason,
    cancelReasonText: rental.cancelReasonText,
    cancelFee: rental.cancelFee,
    previousRentalOverdue: hasPreviousOverdue(rental),
    extensionCount: rental.extensionCount ?? 0,
    pendingExtension: toPendingExtension(rental, myRole),
    canWriteReview: canWriteReview(rental),
    ...toDepositFields(rental),
    reviewWritten: hasWritten(db.reviews, rental.rentalId, currentMemberId()),
    activeCancelAgreement: agreement
      ? {
          agreementId: agreement.agreementId,
          reason: agreement.reason,
          reasonText: agreement.reasonText,
          expiresAt: agreement.expiresAt,
          requestedByMe: agreement.requesterId === currentMemberId(),
        }
      : null,
  };
}

/**
 * 상세의 보증금·연체료 필드(REQ-025·049·023). 상태·환불액은 결제 내역 목과 같은 계산(paymentHistoryLogic.js)이라
 * 두 화면이 어긋나지 않는다. 승인된 결제가 없으면(결제 꺼짐·결제 대기·무료 대여) depositStatus null
 */
function toDepositFields(rental) {
  const extra = paymentExtraOf(rental);
  const today = todayInSeoul();
  const proposal = latestProposalOf(db.deductionProposals, rental.rentalId);
  const fields = {
    depositStatus: null,
    refundedAmount: 0,
    depositRefundPending: false,
    depositHeldAt: extra.heldAt ?? null,
    deduction: null,
    latestDeductionProposal: toProposalResponse(proposal),
    overdueFee: overdueFeeOf(rental, extra, today),
  };
  const rentalFee = PAYMENT_ENABLED ? rentalFeeOf(rental, db.payments) : 0;
  if (!PAYMENT_ENABLED || !isRentalPaid(rental, db.payments) || rentalFee + rental.deposit === 0) {
    return fields;
  }
  const state = rentalPaymentState(rental, rentalFee, extra);
  return {
    ...fields,
    depositStatus: state.depositStatus,
    refundedAmount: state.refundedAmount,
    depositRefundPending: state.depositRefundPending,
    deduction: state.deduction,
  };
}

/** 결제 대기 연장(REQ-038). 대여자 본인이고 RENTING일 때만(백엔드 RentalQueryService와 같다) */
function toPendingExtension(rental, myRole) {
  if (myRole !== 'RENTER' || rental.status !== 'RENTING') {
    return null;
  }
  const extension = db.extensions.find(
    (candidate) => candidate.rentalId === rental.rentalId && isPendingExtension(candidate),
  );
  return extension
    ? {
        extensionId: extension.extensionId,
        newEndDate: extension.newEndDate,
        additionalFee: extension.additionalFee,
        expiresAt: extension.expiresAt,
      }
    : null;
}

/** 거래 완료 후 review.write-deadline-days 이내(정각 포함)이고 이 회원이 아직 쓰지 않았는지(REQ-034) */
function canWriteReview(rental) {
  return canWriteReviewOf(rental, db.reviews, currentMemberId(), Date.now());
}

/** RentalHistoryResponse. totalAmount는 상세와 같은 값(대여료 + 보증금) */
function toHistoryItem(rental) {
  const item = findMockItem(rental.itemId);
  const rentalDays = countDaysInclusive(rental.startDate, rental.endDate);
  return {
    rentalId: rental.rentalId,
    item: {
      itemId: item.itemId,
      name: item.name,
      thumbnailUrl: item.thumbnailUrl,
      isDeleted: item.isDeleted,
    },
    owner: { memberId: rental.ownerId, nickname: MOCK_MEMBERS[rental.ownerId].nickname },
    startDate: rental.startDate,
    endDate: rental.endDate,
    status: rental.status,
    totalAmount: rental.pricePerDay * rentalDays + rental.deposit,
    canWriteReview: canWriteReview(rental),
    reviewWritten: hasWritten(db.reviews, rental.rentalId, currentMemberId()),
  };
}

/** MyItemResponse. 숨김 물품은 시드에 없어(숨기기 REQ-044 전) status는 항상 AVAILABLE */
function toMyItemResponse(item, today, now) {
  return {
    itemId: item.itemId,
    name: item.name,
    thumbnailUrl: item.thumbnailUrl,
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    regionName: item.regionName,
    status: 'AVAILABLE',
    isRenting: isRentingItem(db.rentals, item.itemId),
    bumpedAt: item.bumpedAt,
    viewCount: item.counts.viewCount,
    wishCount: item.counts.wishCount,
    chatCount: item.counts.chatCount,
    upcomingRentalCount: countUpcomingRentals(db.rentals, item.itemId, today, now),
  };
}

/** ItemRentalResponse */
function toItemRentalResponse(rental) {
  return {
    rentalId: rental.rentalId,
    startDate: rental.startDate,
    endDate: rental.endDate,
    status: rental.status,
    renter: { memberId: rental.renterId, nickname: MOCK_MEMBERS[rental.renterId].nickname },
  };
}

/** path의 itemId. Long으로 바뀌지 않으면 400 */
function requireItemId(params) {
  const itemId = readPathId(params.itemId);
  if (itemId == null) {
    invalidInput('itemId', '형식이 올바르지 않습니다.');
  }
  return itemId;
}

/** 쿼리의 page(없으면 0). 0 이상 정수가 아니면 400. size는 보내도 무시한다 */
function requirePage(searchParams) {
  const page = readPage(searchParams.get('page'));
  if (page == null) {
    invalidInput('page', '형식이 올바르지 않습니다.');
  }
  return page;
}

/** RentalInspectionResponse(저장소의 rentalId는 응답에 없다) */
function toInspectionResponse(inspection) {
  return {
    inspectionId: inspection.inspectionId,
    phase: inspection.phase,
    imageUrls: inspection.imageUrls,
    note: inspection.note,
    checklist: inspection.checklist,
    writerRole: inspection.writerRole,
    createdAt: inspection.createdAt,
    result: inspection.result,
    resultNote: inspection.resultNote,
    resultImageUrls: inspection.resultImageUrls,
    aiResult: toAiResultResponse(inspection.aiResult),
  };
}

/** 공백을 지우고 비면 null(백엔드 검증과 같음) */
function trimToNull(text) {
  const trimmed = typeof text === 'string' ? text.trim() : '';
  return trimmed === '' ? null : trimmed;
}

const CANCEL_REASONS = ['SCHEDULE_CHANGE', 'ITEM_PROBLEM', 'COUNTERPART_REQUEST', 'OTHER'];

function validateCancelReason(body) {
  if (!CANCEL_REASONS.includes(body?.reason)) {
    invalidInput('reason', '취소 사유를 선택해 주세요.');
  }
  const reasonText = trimToNull(body.reasonText);
  if (body.reason === 'OTHER' && !reasonText) {
    invalidInput('reasonText', '기타 사유를 입력해 주세요.');
  }
  if (reasonText && [...reasonText].length > POLICY.CANCEL_REASON_TEXT_MAX) {
    invalidInput('reasonText', `${POLICY.CANCEL_REASON_TEXT_MAX}자 이하로 입력해 주세요.`);
  }
  return { reason: body.reason, reasonText };
}

export function cancel(rental, { cancelType, reason, reasonText, cancelFee }) {
  Object.assign(rental, {
    status: 'CANCELED',
    canceledAt: nowServer(),
    cancelType,
    cancelReason: reason,
    cancelReasonText: reasonText,
    cancelFee,
  });
  // 결제 대기 취소는 주문을 닫고, 승인된 결제는 환불한 것으로 둔다(payment.md 취소 시 환불)
  for (const order of db.payments.filter((o) => o.rentalId === rental.rentalId)) {
    if (order.status === 'READY') {
      order.status = 'FAILED';
    } else if (order.status === 'APPROVED') {
      const isFullRefund = cancelFee === 0;
      order.status = isFullRefund ? 'CANCELED' : 'PARTIAL_CANCELED';
      order.depositStatus = rental.deposit > 0 ? 'REFUNDED' : 'NONE';
    }
  }
}

/** 결제 만료 배치 흉내: 만료 시각이 배치 주기만큼 지난 결제 대기를 취소한다. 바뀐 게 있으면 true */
function expirePendingPayments() {
  const limit = minutesFromNow(-POLICY.PAYMENT_EXPIRE_BATCH_MINUTES);
  const expired = db.rentals.filter(
    (rental) => rental.status === 'PENDING_PAYMENT' && rental.paymentExpiresAt <= limit,
  );
  for (const rental of expired) {
    cancel(rental, { cancelType: 'PAYMENT_EXPIRED', reason: null, reasonText: null, cancelFee: 0 });
  }
  return expired.length > 0;
}

/**
 * 연장 결제 만료 배치 흉내(REQ-038, 1분): 만료 시각이 배치 주기만큼 지난 결제 대기 연장을 CANCELED로,
 * 묶인 READY 주문은 FAILED로. 바뀐 게 있으면 true
 */
function expirePendingExtensions() {
  const limit = minutesFromNow(-POLICY.PAYMENT_EXPIRE_BATCH_MINUTES);
  const expired = db.extensions.filter(
    (extension) => extension.status === 'PENDING_PAYMENT' && extension.expiresAt <= limit,
  );
  for (const extension of expired) {
    extension.status = 'CANCELED';
    for (const order of db.payments) {
      if (order.extensionId === extension.extensionId && order.status === 'READY') {
        order.status = 'FAILED';
      }
    }
  }
  return expired.length > 0;
}

/** 연장 확정: 종료일 갱신, 확정 횟수 + 1 (결제 없이 바로 확정·결제 승인 공통) */
export function confirmExtension(rental, extension) {
  extension.status = 'CONFIRMED';
  rental.endDate = extension.newEndDate;
  rental.extensionCount = (rental.extensionCount ?? 0) + 1;
}

/**
 * 연장 가능한 최대 날짜(백엔드 ExtensionPeriodCalculator): 기존 종료일 + max-days-per, 종료일 다음 날이 든 가능 기간의 끝,
 * 다음 점유 시작일 − 1 중 가장 이른 날. 하루도 안 되면 null
 */
function extensionMaxEndDate(rental) {
  const firstDate = addDays(rental.endDate, 1);
  const period = findMockItem(rental.itemId).availablePeriods.find(
    (p) => compareDate(p.startDate, firstDate) <= 0 && compareDate(firstDate, p.endDate) <= 0,
  );
  if (!period) {
    return null;
  }
  let max = addDays(rental.endDate, POLICY.EXTENSION_MAX_DAYS);
  if (compareDate(period.endDate, max) < 0) {
    max = period.endDate;
  }
  for (const occupied of listOccupied(rental.itemId)) {
    if (
      occupied.rentalId !== rental.rentalId &&
      compareDate(occupied.endDate, firstDate) >= 0 &&
      compareDate(occupied.startDate, max) <= 0
    ) {
      const before = addDays(occupied.startDate, -1);
      if (compareDate(before, max) < 0) {
        max = before;
      }
    }
  }
  return compareDate(max, firstDate) < 0 ? null : max;
}

/** multipart: request 파트는 application/json이어야 한다(아니면 415) */
async function readMultipart(request, { isRequestRequired }) {
  const form = await request.formData();
  const part = form.get('request');
  const images = form.getAll('images');
  if (!part) {
    if (isRequestRequired) {
      invalidInput('request', '필수 항목입니다.');
    }
    return { body: {}, images, form };
  }
  if (typeof part === 'string' || part.type !== 'application/json') {
    reject('COMMON_UNSUPPORTED_MEDIA_TYPE');
  }
  return { body: JSON.parse(await part.text()), images, form };
}

function requireImageCount(images) {
  if (images.length < POLICY.IMAGES_MIN || images.length > POLICY.IMAGES_MAX) {
    reject('IMAGE_COUNT_INVALID');
  }
}

/** 올린 파일은 브라우저 메모리 URL로 돌려준다(새로고침하면 사라짐) */
function toImageUrls(images) {
  return images.map((file) => URL.createObjectURL(file));
}

/** "YYYY-MM-DD"이고 실제로 있는 날짜(2026-02-30 거부) */
function isRealDate(date) {
  if (typeof date !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    return false;
  }
  const [year, month, day] = date.split('-').map(Number);
  const utc = new Date(Date.UTC(year, month - 1, day));
  return (
    utc.getUTCFullYear() === year && utc.getUTCMonth() === month - 1 && utc.getUTCDate() === day
  );
}

/** 서버는 LocalDate로 못 읽으면(형식·없는 날짜) 400 COMMON_INVALID_INPUT, details에 필드 */
function readDate(body, field) {
  if (body?.[field] == null) {
    invalidInput(field, '필수 값입니다.');
  }
  if (!isRealDate(body[field])) {
    invalidInput(field, '형식이 올바르지 않습니다.');
  }
  return body[field];
}

function validateLength(field, text, max) {
  if (text && [...text].length > max) {
    invalidInput(field, `${max}자 이하로 입력해 주세요.`);
  }
}

function addInspection(inspection) {
  const inspectionId = Math.max(0, ...db.inspections.map((i) => i.inspectionId)) + 1;
  db.inspections.push({ inspectionId, ...inspection });
  return inspectionId;
}

function calendarDays(item, month) {
  const today = todayInSeoul();
  const occupying = listOccupied(item.itemId);
  return buildMonthGrid(month)
    .filter(Boolean)
    .map((date) => {
      const isInPeriod = item.availablePeriods.some(
        (p) => compareDate(p.startDate, date) <= 0 && compareDate(date, p.endDate) <= 0,
      );
      if (compareDate(date, today) < 0 || !isInPeriod) {
        return { date, status: 'UNAVAILABLE' };
      }
      const isBooked = occupying.some(
        (r) => compareDate(r.startDate, date) <= 0 && compareDate(date, r.endDate) <= 0,
      );
      return { date, status: isBooked ? 'BOOKED' : 'AVAILABLE' };
    });
}

export const rentalHandlers = [
  // REQ-019 빌린 내역(openapi.json 그대로). 내가 대여자인 건만, 탭·정렬·20건 고정
  http.get(
    '/api/members/me/rentals',
    handle(({ request }) => {
      const { searchParams } = new URL(request.url);
      const role = searchParams.get('role');
      if (!role) {
        invalidInput('role', '필수 값입니다.');
      }
      if (role !== 'renter') {
        invalidInput('role', '형식이 올바르지 않습니다.');
      }
      const tab = searchParams.get('tab') ?? 'ONGOING';
      if (!Object.hasOwn(HISTORY_TAB_STATUSES, tab)) {
        invalidInput('tab', '형식이 올바르지 않습니다.');
      }
      const page = requirePage(searchParams);
      const rentals = listRentalHistory(db.rentals, currentMemberId(), tab).map(toHistoryItem);
      return ok(toPageResponse(rentals, page));
    }),
  ),

  // REQ-020 내 물품 관리(openapi.json 그대로). tab 생략은 AVAILABLE, HIDDEN은 빈 목록, 최근 등록 순, 20건 고정
  http.get(
    '/api/members/me/items',
    handle(({ request }) => {
      const { searchParams } = new URL(request.url);
      const tab = searchParams.get('tab') ?? 'AVAILABLE';
      if (!MOCK_MY_ITEM_TABS.includes(tab)) {
        invalidInput('tab', '형식이 올바르지 않습니다.');
      }
      const page = requirePage(searchParams);
      const today = todayInSeoul();
      const now = nowServer();
      const items = listMyItems(listMyMockItems(currentMemberId()), db.rentals, tab).map((item) =>
        toMyItemResponse(item, today, now),
      );
      return ok(toPageResponse(items, page));
    }),
  ),

  // REQ-020 물품별 예약 현황(openapi.json 그대로). 정렬은 open-issues #20(pageList.js), 20건 고정.
  // 검사 순서: 입력 400 → 404 → 403
  http.get(
    '/api/items/:itemId/rentals',
    handle(({ params, request }) => {
      const itemId = requireItemId(params);
      const page = requirePage(new URL(request.url).searchParams);
      const item = findMockItem(itemId);
      if (!item || item.isDeleted) {
        reject('ITEM_NOT_FOUND');
      }
      if (item.ownerId !== currentMemberId()) {
        reject('ITEM_NOT_OWNER');
      }
      const rentals = listItemRentals(db.rentals, item.itemId).map(toItemRentalResponse);
      return ok(toPageResponse(rentals, page));
    }),
  ),

  http.get(
    '/api/items/:itemId/calendar',
    handle(({ params, request }) => {
      const item = findMockItem(requireItemId(params));
      if (!item || item.isDeleted) {
        reject('ITEM_NOT_FOUND');
      }
      const month = new URL(request.url).searchParams.get('month') ?? monthOf(todayInSeoul());
      if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
        invalidInput('month', '형식이 올바르지 않습니다.');
      }
      return ok({ month, days: calendarDays(item, month) });
    }),
  ),

  http.post(
    '/api/rentals',
    handle(async ({ request }) => {
      const body = await request.json();
      if (body?.itemId == null) {
        invalidInput('itemId', '필수 값입니다.');
      }
      const { itemId } = body;
      const startDate = readDate(body, 'startDate');
      const endDate = readDate(body, 'endDate');
      const today = todayInSeoul();
      if (
        compareDate(startDate, today) < 0 ||
        compareDate(startDate, endDate) > 0 ||
        countDaysInclusive(startDate, endDate) > POLICY.MAX_DAYS
      ) {
        reject('RENTAL_INVALID_PERIOD');
      }
      const item = findMockItem(itemId);
      if (!item || item.isDeleted) {
        reject('ITEM_NOT_FOUND');
      }
      if (item.ownerId === currentMemberId()) {
        reject('RENTAL_OWN_ITEM');
      }
      const isInPeriod = item.availablePeriods.some(
        (p) => compareDate(p.startDate, startDate) <= 0 && compareDate(endDate, p.endDate) <= 0,
      );
      if (!isInPeriod) {
        reject('RENTAL_OUT_OF_AVAILABLE_PERIOD');
      }
      const hasOverlap = listOccupied(item.itemId).some(
        (r) => compareDate(r.startDate, endDate) <= 0 && compareDate(r.endDate, startDate) >= 0,
      );
      if (item.itemId === CONFLICT_ITEM_ID || hasOverlap) {
        reject('RENTAL_PERIOD_CONFLICT');
      }
      const total = item.pricePerDay * countDaysInclusive(startDate, endDate) + item.deposit;
      const isPending = PAYMENT_ENABLED && total > 0;
      const rental = {
        rentalId:
          Math.max(...db.rentals.filter((r) => r.rentalId < 900).map((r) => r.rentalId)) + 1,
        itemId: item.itemId,
        renterId: currentMemberId(),
        ownerId: item.ownerId,
        status: isPending ? 'PENDING_PAYMENT' : 'RESERVED',
        startDate,
        endDate,
        pricePerDay: item.pricePerDay,
        deposit: item.deposit,
        createdAt: nowServer(),
        paymentExpiresAt: isPending ? minutesFromNow(POLICY.PAYMENT_EXPIRE_MINUTES) : null,
        handedOverAt: null,
        returnedAt: null,
        completedAt: null,
        canceledAt: null,
        cancelType: null,
        cancelReason: null,
        cancelReasonText: null,
        cancelFee: 0,
      };
      db.rentals.push(rental);
      return ok(toDetail(rental), { status: 201 });
    }),
  ),

  http.get(
    '/api/rentals/:rentalId',
    handle(({ params }) => ok(toDetail(findParticipantRental(params.rentalId)))),
  ),

  http.get(
    '/api/rentals/:rentalId/inspections',
    handle(({ params }) => {
      const rental = findParticipantRental(params.rentalId);
      const order = { HANDOVER: 0, RETURN: 1 };
      return ok(
        db.inspections
          .filter((i) => i.rentalId === rental.rentalId)
          .sort((a, b) => order[a.phase] - order[b.phase])
          .map(toInspectionResponse),
      );
    }),
  ),

  http.patch(
    '/api/rentals/:rentalId/cancel',
    handle(async ({ params, request }) => {
      const { reason, reasonText } = validateCancelReason(await request.json());
      const rental = findParticipantRental(params.rentalId);
      requireStatus(rental, 'PENDING_PAYMENT', 'RESERVED');
      const today = todayInSeoul();
      const role = roleOf(rental);
      let cancelFee = 0;
      if (rental.status === 'RESERVED') {
        if (compareDate(today, rental.startDate) >= 0) {
          // 앞 대여 연체 중이면 대여자는 수수료 없이 단독 취소(open-issues #7)
          if (role !== 'RENTER' || !hasPreviousOverdue(rental, today)) {
            reject('RENTAL_CANCEL_NOT_ALLOWED');
          }
          cancel(rental, { cancelType: role, reason, reasonText, cancelFee: 0 });
          return ok(toDetail(rental));
        }
        const daysBefore = countDaysInclusive(today, rental.startDate) - 1;
        if (role === 'RENTER' && daysBefore < POLICY.CANCEL_FEE_FREE_UNTIL_DAYS_BEFORE) {
          const rentalFee =
            rental.pricePerDay * countDaysInclusive(rental.startDate, rental.endDate);
          cancelFee = Math.floor((rentalFee * POLICY.CANCEL_FEE_RATE_PERCENT) / 100);
        }
      }
      cancel(rental, { cancelType: role, reason, reasonText, cancelFee });
      return ok(toDetail(rental));
    }),
  ),

  http.post(
    '/api/rentals/:rentalId/cancel-agreements',
    handle(async ({ params, request }) => {
      const { reason, reasonText } = validateCancelReason(await request.json());
      const rental = findParticipantRental(params.rentalId);
      requireStatus(rental, 'RESERVED');
      if (compareDate(todayInSeoul(), rental.startDate) < 0) {
        reject('RENTAL_CANCEL_AGREEMENT_TOO_EARLY');
      }
      if (db.agreements.some((a) => a.rentalId === rental.rentalId && isOpen(a))) {
        reject('RENTAL_CANCEL_AGREEMENT_IN_PROGRESS');
      }
      const agreement = {
        agreementId: Math.max(0, ...db.agreements.map((a) => a.agreementId)) + 1,
        rentalId: rental.rentalId,
        requesterId: currentMemberId(),
        reason,
        reasonText,
        status: 'REQUESTED',
        expiresAt: minutesFromNow(POLICY.CANCEL_AGREEMENT_EXPIRE_HOURS * 60),
      };
      db.agreements.push(agreement);
      return ok(
        {
          agreementId: agreement.agreementId,
          rentalId: rental.rentalId,
          status: agreement.status,
          reason,
          reasonText,
          expiresAt: agreement.expiresAt,
          requestedByMe: true,
        },
        { status: 201 },
      );
    }),
  ),

  http.patch(
    '/api/rentals/:rentalId/cancel-agreements/:agreementId',
    handle(async ({ params, request }) => {
      const { action } = (await request.json()) ?? {};
      if (action !== 'ACCEPT' && action !== 'REJECT') {
        invalidInput('action', '형식이 올바르지 않습니다.');
      }
      const rental = findParticipantRental(params.rentalId);
      const agreement = db.agreements.find(
        (a) => a.agreementId === Number(params.agreementId) && a.rentalId === rental.rentalId,
      );
      if (!agreement) {
        reject('RENTAL_CANCEL_AGREEMENT_NOT_FOUND');
      }
      if (agreement.requesterId === currentMemberId()) {
        reject('RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE');
      }
      if (!isOpen(agreement)) {
        reject('RENTAL_CANCEL_AGREEMENT_CLOSED');
      }
      requireStatus(rental, 'RESERVED');
      if (action === 'ACCEPT') {
        agreement.status = 'ACCEPTED';
        cancel(rental, {
          cancelType: 'MUTUAL',
          reason: agreement.reason,
          reasonText: agreement.reasonText,
          cancelFee: 0,
        });
      } else {
        agreement.status = 'REJECTED';
      }
      return ok(toDetail(rental));
    }),
  ),

  http.patch(
    '/api/rentals/:rentalId/handover',
    handle(async ({ params, request }) => {
      const { body, images } = await readMultipart(request, { isRequestRequired: false });
      const memo = trimToNull(body.memo);
      validateLength('memo', memo, POLICY.HANDOVER_MEMO_MAX);
      const rental = findParticipantRental(params.rentalId);
      requireOwner(rental);
      requireStatus(rental, 'RESERVED');
      if (compareDate(todayInSeoul(), rental.startDate) < 0) {
        reject('RENTAL_HANDOVER_TOO_EARLY');
      }
      requireImageCount(images);
      const now = nowServer();
      addInspection({
        rentalId: rental.rentalId,
        phase: 'HANDOVER',
        imageUrls: toImageUrls(images),
        note: memo,
        checklist: null,
        writerRole: 'OWNER',
        createdAt: now,
        result: null,
        resultNote: null,
        resultImageUrls: [],
      });
      Object.assign(rental, { status: 'RENTING', handedOverAt: now });
      return ok(toDetail(rental));
    }),
  ),

  // REQ-038 대여 연장(openapi.json 그대로). 검사 순서: 입력 400 → 404 → 403 → 상태 409 → 기간·횟수 400 → 결제 대기 409 → 기간 400·409
  http.post(
    '/api/rentals/:rentalId/extensions',
    handle(async ({ params, request }) => {
      const newEndDate = readDate(await request.json(), 'newEndDate');
      const rental = findParticipantRental(params.rentalId);
      if (roleOf(rental) !== 'RENTER') {
        reject('RENTAL_RENTER_ONLY');
      }
      const today = todayInSeoul();
      if (
        rental.status === 'OVERDUE' ||
        (rental.status === 'RENTING' && compareDate(rental.endDate, today) < 0)
      ) {
        reject('RENTAL_EXTENSION_OVERDUE');
      }
      requireStatus(rental, 'RENTING');
      if (compareDate(newEndDate, rental.endDate) <= 0) {
        reject('RENTAL_INVALID_PERIOD');
      }
      const days = countDaysInclusive(addDays(rental.endDate, 1), newEndDate);
      if (
        (rental.extensionCount ?? 0) >= POLICY.EXTENSION_MAX_COUNT ||
        days > POLICY.EXTENSION_MAX_DAYS
      ) {
        reject('RENTAL_EXTENSION_LIMIT_EXCEEDED');
      }
      if (db.extensions.some((e) => e.rentalId === rental.rentalId && isPendingExtension(e))) {
        reject('RENTAL_EXTENSION_IN_PROGRESS');
      }
      const maxEndDate = extensionMaxEndDate(rental);
      const maxEndDateDetails = maxEndDate ? [{ field: 'maxEndDate', reason: maxEndDate }] : [];
      const firstDate = addDays(rental.endDate, 1);
      const item = findMockItem(rental.itemId);
      const isInPeriod = item.availablePeriods.some(
        (p) => compareDate(p.startDate, firstDate) <= 0 && compareDate(newEndDate, p.endDate) <= 0,
      );
      if (!isInPeriod) {
        reject('RENTAL_OUT_OF_AVAILABLE_PERIOD', maxEndDateDetails);
      }
      const hasOverlap = listOccupied(rental.itemId).some(
        (r) =>
          r.rentalId !== rental.rentalId &&
          compareDate(r.startDate, newEndDate) <= 0 &&
          compareDate(r.endDate, firstDate) >= 0,
      );
      if (hasOverlap) {
        reject('RENTAL_EXTENSION_PERIOD_CONFLICT', maxEndDateDetails);
      }
      const additionalFee = rental.pricePerDay * days;
      const isPending = PAYMENT_ENABLED && additionalFee > 0;
      const extension = {
        extensionId: Math.max(0, ...db.extensions.map((e) => e.extensionId)) + 1,
        rentalId: rental.rentalId,
        prevEndDate: rental.endDate,
        firstDate,
        newEndDate,
        additionalFee,
        status: isPending ? 'PENDING_PAYMENT' : 'CONFIRMED',
        expiresAt: isPending ? minutesFromNow(POLICY.PAYMENT_EXPIRE_MINUTES) : nowServer(),
      };
      db.extensions.push(extension);
      if (!isPending) {
        confirmExtension(rental, extension);
      }
      return ok(
        {
          extensionId: extension.extensionId,
          rentalId: rental.rentalId,
          status: extension.status,
          prevEndDate: extension.prevEndDate,
          newEndDate,
          additionalFee,
          expiresAt: isPending ? extension.expiresAt : null,
        },
        { status: 201 },
      );
    }),
  ),

  http.post(
    '/api/rentals/:rentalId/return-request',
    handle(async ({ params, request }) => {
      const { body, images } = await readMultipart(request, { isRequestRequired: true });
      const checklist = body.checklist;
      const keys = [
        'NO_EXTERIOR_DAMAGE',
        'ALL_PARTS_INCLUDED',
        'NO_CONTAMINATION',
        'WORKS_NORMALLY',
        'CONSUMABLES_RESTORED',
      ];
      if (
        !checklist ||
        typeof checklist !== 'object' ||
        Object.keys(checklist).some((key) => !keys.includes(key)) ||
        keys.some((key) => typeof checklist[key] !== 'boolean')
      ) {
        invalidInput('checklist', '형식이 올바르지 않습니다.');
      }
      const note = trimToNull(body.note);
      if (keys.some((key) => checklist[key] === false) && !note) {
        invalidInput('note', '아니오 항목이 있으면 메모를 입력해 주세요.');
      }
      validateLength('note', note, POLICY.RETURN_NOTE_MAX);
      const rental = findParticipantRental(params.rentalId);
      requireStatus(rental, 'RENTING', 'OVERDUE');
      requireImageCount(images);
      const now = nowServer();
      const inspectionId = addInspection({
        rentalId: rental.rentalId,
        phase: 'RETURN',
        imageUrls: toImageUrls(images),
        note,
        checklist,
        writerRole: roleOf(rental),
        createdAt: now,
        result: null,
        resultNote: null,
        resultImageUrls: [],
      });
      // 연체료는 반납 요청일 기준으로 확정한다(REQ-023)
      rental.depositExtra = {
        ...rental.depositExtra,
        overdueFee: calcOverdueFee(rental, todayInSeoul()),
      };
      Object.assign(rental, { status: 'RETURNED', returnedAt: now });
      // 커밋 뒤 ai.compare 적재(REQ-040)
      scheduleAiCompare(inspectionId, rental);
      return ok(toDetail(rental));
    }),
  ),

  http.patch(
    '/api/rentals/:rentalId/return',
    handle(async ({ params, request }) => {
      const { body, images } = await readMultipart(request, { isRequestRequired: true });
      const { result } = body;
      if (result !== 'NORMAL' && result !== 'ABNORMAL') {
        invalidInput('result', '형식이 올바르지 않습니다.');
      }
      const resultNote = trimToNull(body.resultNote);
      if (result === 'ABNORMAL' && !resultNote) {
        invalidInput('resultNote', '이상 내용을 입력해 주세요.');
      }
      validateLength('resultNote', resultNote, POLICY.ABNORMAL_NOTE_MAX);
      const rental = findParticipantRental(params.rentalId);
      requireOwner(rental);
      requireStatus(rental, 'RETURNED');
      const record = db.inspections.find(
        (i) => i.rentalId === rental.rentalId && i.phase === 'RETURN',
      );
      // 미반납 신고 건은 물건이 돌아와도 상태 확인을 하지 않는다(open-issues #41)
      if (!record || record.result || rental.depositExtra?.lostReported) {
        reject('RENTAL_INVALID_TRANSITION');
      }
      // NORMAL에 사진을 보내면 400(rental.md REQ-018)
      if (result === 'NORMAL' && images.length > 0) {
        reject('IMAGE_COUNT_INVALID');
      }
      if (result === 'ABNORMAL') {
        requireImageCount(images);
      }
      Object.assign(record, {
        result,
        resultNote,
        resultImageUrls: toImageUrls(images),
      });
      const isFrozen = hasFrozenDeposit(rental, db.payments, PAYMENT_ENABLED);
      // 동결 보증금이 있는 ABNORMAL은 RETURNED 유지(REQ-049), 없으면 처리할 금액이 없어 완료(백엔드와 같음)
      if (result === 'ABNORMAL' && isFrozen) {
        rental.depositExtra = { ...rental.depositExtra, held: true, heldAt: nowServer() };
        return ok(toDetail(rental));
      }
      // 보증금 반환 실패는 200 + RETURNED(재시도 배치가 완료한다)
      if (isFrozen && takeFailure([DEPOSIT_REFUND_FAILED])) {
        return ok(toDetail(rental));
      }
      Object.assign(rental, { status: 'COMPLETED', completedAt: nowServer() });
      return ok(toDetail(rental));
    }),
  ),

  // 미반납 신고(REQ-018). 검사 순서는 openapi 설명 그대로(400 → 404 → 403 → 409)
  http.post(
    '/api/rentals/:rentalId/lost-report',
    handle(async ({ params, request }) => {
      const body = await request.json().catch(() => ({}));
      const note = trimToNull(body?.note);
      validateLength('note', note, POLICY.RETURN_NOTE_MAX);
      const rental = findParticipantRental(params.rentalId);
      requireOwner(rental);
      requireStatus(rental, 'OVERDUE');
      const today = todayInSeoul();
      if (!isLostReportOpen(rental, today)) {
        reject('RENTAL_LOST_REPORT_TOO_EARLY');
      }
      const now = nowServer();
      addInspection({
        rentalId: rental.rentalId,
        phase: 'RETURN',
        imageUrls: [],
        note,
        checklist: null,
        writerRole: 'OWNER',
        createdAt: now,
        result: null,
        resultNote: null,
        resultImageUrls: [],
      });
      // 연체료는 신고일 기준으로 확정. 동결 보증금이 없으면 처리할 금액이 없어 바로 완료(open-issues #39)
      const isFrozen = hasFrozenDeposit(rental, db.payments, PAYMENT_ENABLED);
      rental.depositExtra = {
        ...rental.depositExtra,
        overdueFee: calcOverdueFee(rental, today),
        lostReported: true,
        ...(isFrozen ? { held: true, heldAt: now } : {}),
      };
      Object.assign(rental, {
        status: isFrozen ? 'RETURNED' : 'COMPLETED',
        returnedAt: now,
        completedAt: isFrozen ? null : now,
      });
      return ok(toDetail(rental));
    }),
  ),

  // 등급별 차감 상한(REQ-049, 당사자)
  http.get(
    '/api/rentals/:rentalId/deposit/policy',
    handle(({ params, request }) => {
      const grade = new URL(request.url).searchParams.get('grade');
      const rental = findParticipantRental(params.rentalId);
      const policy = deductionPolicyOf({
        deposit: rental.deposit,
        overdueFee: overdueFeeOf(rental, paymentExtraOf(rental), todayInSeoul()),
        categoryId: findMockItem(rental.itemId).categoryId,
        grade,
        isLostReport: rental.depositExtra?.lostReported === true,
      });
      if (policy.error === 'COMMON_INVALID_INPUT') {
        invalidInput('grade', '형식이 올바르지 않습니다.');
      }
      if (policy.error) {
        reject(policy.error);
      }
      return ok(policy);
    }),
  ),

  // 보증금 차감 협의(REQ-049). 응답은 대여 상세
  http.patch(
    '/api/rentals/:rentalId/deposit',
    handle(async ({ params, request }) => {
      const { body, form } = await readMultipart(request, { isRequestRequired: true });
      const evidenceImages = form.getAll('evidenceImages');
      const receiptImages = form.getAll('receiptImages');
      const { action } = body;
      if (!['PROPOSE', 'ACCEPT', 'REJECT'].includes(action)) {
        invalidInput('action', '형식이 올바르지 않습니다.');
      }
      const reason = trimToNull(body.reason);
      if (action === 'PROPOSE') {
        if (!Number.isSafeInteger(body.amount) || body.amount < 0) {
          invalidInput('amount', '0 이상이어야 합니다.');
        }
        if (!reason) {
          invalidInput('reason', '차감 사유를 입력해 주세요.');
        }
        validateLength('reason', reason, DEDUCTION_REASON_MAX);
      } else if (!Number.isSafeInteger(body.proposalId)) {
        invalidInput('proposalId', '필수 항목입니다.');
      }
      const rental = findParticipantRental(params.rentalId);
      if (action === 'PROPOSE') {
        requireOwner(rental);
      } else if (roleOf(rental) !== 'RENTER') {
        reject('RENTAL_RENTER_ONLY');
      }
      requireStatus(rental, 'RETURNED');
      const extra = paymentExtraOf(rental);
      if (!extra.held) {
        reject('DEPOSIT_NOT_HELD');
      }
      const now = nowServer();
      const overdueFee = overdueFeeOf(rental, extra, todayInSeoul());
      const latest = latestProposalOf(db.deductionProposals, rental.rentalId);

      if (action === 'PROPOSE') {
        if (latest?.status === 'PROPOSED') {
          reject('DEDUCTION_PROPOSAL_IN_PROGRESS');
        }
        const isLostReport = extra.lostReported === true;
        const policy = deductionPolicyOf({
          deposit: rental.deposit,
          overdueFee,
          categoryId: findMockItem(rental.itemId).categoryId,
          grade: body.grade,
          isLostReport,
        });
        if (policy.error === 'COMMON_INVALID_INPUT') {
          invalidInput('grade', '형식이 올바르지 않습니다.');
        }
        const invalid = validateProposal(policy, {
          amount: body.amount,
          evidenceCount: evidenceImages.length,
          receiptCount: receiptImages.length,
          isLostReport,
        });
        if (invalid) {
          reject(invalid.code, invalid.details);
        }
        db.deductionProposals.push({
          proposalId: Math.max(0, ...db.deductionProposals.map((p) => p.proposalId)) + 1,
          rentalId: rental.rentalId,
          status: 'PROPOSED',
          grade: body.grade,
          amount: body.amount,
          reason,
          evidenceImageUrls: toImageUrls(evidenceImages),
          receiptImageUrls: toImageUrls(receiptImages),
          proposedAt: now,
          respondDeadline: minutesFromNow(DEDUCTION_RESPONSE_HOURS * 60),
          respondedAt: null,
        });
        return ok(toDetail(rental));
      }

      // ACCEPT·REJECT: 사진을 보내면 400, 최신 제안에만 응답(404), 기한이 지나면 409(open-issues #43)
      if (evidenceImages.length > 0 || receiptImages.length > 0) {
        invalidInput('evidenceImages', '응답에는 사진을 보낼 수 없습니다.');
      }
      const proposal = db.deductionProposals.find(
        (p) => p.rentalId === rental.rentalId && p.proposalId === body.proposalId,
      );
      if (!proposal) {
        reject('DEDUCTION_PROPOSAL_NOT_FOUND');
      }
      if (proposal.status !== 'PROPOSED' || isProposalClosed(proposal, now)) {
        reject('DEDUCTION_PROPOSAL_CLOSED');
      }
      Object.assign(proposal, {
        status: action === 'ACCEPT' ? 'ACCEPTED' : 'REJECTED',
        respondedAt: now,
      });
      if (action === 'ACCEPT') {
        // 확정: 결과를 기록하고 거래 완료(반환 실패 시나리오는 200 + RETURNED, 재시도 배치가 완료)
        const deduction = {
          grade: proposal.grade,
          amount: proposal.amount,
          reason: proposal.reason,
        };
        const { refund } = settleDeduction(rental.deposit, overdueFee, proposal.amount);
        if (refund > 0 && takeFailure([DEPOSIT_REFUND_FAILED])) {
          rental.depositExtra = {
            ...rental.depositExtra,
            deduction,
            held: false,
            refundFailed: true,
          };
          return ok(toDetail(rental));
        }
        rental.depositExtra = { ...rental.depositExtra, deduction, held: false };
        Object.assign(rental, { status: 'COMPLETED', completedAt: now });
      }
      return ok(toDetail(rental));
    }),
  ),
];
