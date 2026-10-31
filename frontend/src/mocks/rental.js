// 대여 API 목. 요청·응답 형태는 docs/openapi.json 스키마 그대로이고, 메모리 저장소로 전이까지 흉내 낸다.
// 시드(어떤 id가 어떤 경우인지)는 rentalSeed.js 맨 위 표. 검사 순서는 백엔드와 같게(404 → 403 → 409 → 사진).
import { delay, http } from 'msw';

import {
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
import { hasFrozenDeposit } from './paymentLogic';
import { DEPOSIT_REFUND_FAILED, takeFailure } from './paymentScenario';
import { fail, ok } from './response';
import { minutesFromNow } from './rentalSeed';
import { db, nowServer, occupiesPeriod, saveDb } from './rentalStore';
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
  REVIEW_WRITE_DEADLINE_DAYS: 7,
  PAYMENT_EXPIRE_BATCH_MINUTES: 1, // policies.md 배치 주기 "결제 만료"
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
  PAYMENT_CANCELED_AFTER_EXPIRY: '결제 처리 중 예약이 만료되어 결제 금액을 전액 환불합니다.',
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
    const hasExpired = expirePendingPayments();
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

function roleOf(rental) {
  if (rental.renterId === currentMemberId()) {
    return 'RENTER';
  }
  return rental.ownerId === currentMemberId() ? 'OWNER' : null;
}

/** 404 → 403(당사자) 순서로 대여를 찾는다 */
function findParticipantRental(rentalId) {
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

/** 거래 완료 후 review.write-deadline-days 이내(정각 포함). 후기 테이블이 없어 "미작성" 조건은 없다(REQ-019) */
function canWriteReview(rental) {
  if (rental.status !== 'COMPLETED' || !rental.completedAt) {
    return false;
  }
  const deadline =
    new Date(`${rental.completedAt}+09:00`).getTime() +
    POLICY.REVIEW_WRITE_DEADLINE_DAYS * 24 * 60 * 60 * 1000;
  return Date.now() <= deadline;
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

/** multipart: request 파트는 application/json이어야 한다(아니면 415) */
async function readMultipart(request, { isRequestRequired }) {
  const form = await request.formData();
  const part = form.get('request');
  const images = form.getAll('images');
  if (!part) {
    if (isRequestRequired) {
      invalidInput('request', '필수 항목입니다.');
    }
    return { body: {}, images };
  }
  if (typeof part === 'string' || part.type !== 'application/json') {
    reject('COMMON_UNSUPPORTED_MEDIA_TYPE');
  }
  return { body: JSON.parse(await part.text()), images };
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
}

function calendarDays(item, month) {
  const today = todayInSeoul();
  const occupying = db.rentals.filter((r) => r.itemId === item.itemId && occupiesPeriod(r));
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
      const hasOverlap = db.rentals.some(
        (r) =>
          r.itemId === item.itemId &&
          occupiesPeriod(r) &&
          compareDate(r.startDate, endDate) <= 0 &&
          compareDate(r.endDate, startDate) >= 0,
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
          reject('RENTAL_CANCEL_NOT_ALLOWED');
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
      addInspection({
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
      Object.assign(rental, { status: 'RETURNED', returnedAt: now });
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
      if (!record || record.result) {
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
];
