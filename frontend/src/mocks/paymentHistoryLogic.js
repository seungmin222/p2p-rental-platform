// 결제·보증금 내역(REQ-033)·정산 내역(REQ-039) 목의 순수 함수. 대여 목 db(rentals·payments)를 받아 계산하고 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값·type 해석을 여기 따로 둔다.
//
// 금액은 다른 대여·결제 목과 같은 출처다: 대여료 = 주문이 있으면 주문 금액 − 보증금, 없으면(시드) 일 대여료 × 일수.
// 보증금은 대여 시점 값. 시드 대여는 주문이 없으므로 결제 대기가 아니면 결제한 것으로 본다(paymentLogic.js hasFrozenDeposit과 같은 가정).
//
// 응답에만 있는 값(연체료·보증금 차감·반환 실패·분쟁 보류)은 대여 시드에 없어 아래 표로 보충한다.
// | 대여 | 쪽 | 보충 | 화면에서 보이는 것 |
// | --- | --- | --- | --- |
// | 10 | 대여자 | 연체료 15,000 + 차감 20,000(C) | 보증금 50,000 중 15,000만 반환(부분 환불·반환) |
// | 41 | 대여자 | 연체료 50,000(보증금 전액) | 보증금 전액 차감(FORFEITED) |
// | 45 | 대여자 | 반환 실패 | 보증금 반환 실패 + 환불 처리 중(depositRefundPending) |
// | 30 | 제공자 | 차감 30,000(C) | 정산에 차감액이 수수료 없이 더해짐 |
// | 33 | 제공자 | 분쟁 보류 | 정산 HELD(heldTotal) |
// | 34 | 제공자 | (rentalSeed.js) 수수료 있는 대여자 취소 | 정산 CANCEL_FEE, 지급 예정 |
import { addDays, compareDate, countDaysInclusive, weekdayOf } from '../shared/lib/date';

// policies.md settlement.fee-rate (원 미만 절사)
export const SETTLEMENT_FEE_RATE = 0.1;
// policies.md settlement.payout-delay (영업일, 주말만 제외)
export const SETTLEMENT_PAYOUT_BUSINESS_DAYS = 2;
// policies.md payment-history.default-range
export const PAYMENT_HISTORY_DEFAULT_MONTHS = 3;

export const PAYMENT_HISTORY_TYPES = ['ALL', 'PAYMENT', 'REFUND', 'DEPOSIT'];

const PAID_STATUSES = ['APPROVED', 'PARTIAL_CANCELED', 'CANCELED'];
// 결제가 승인된 적이 없는 취소
const UNPAID_CANCEL_TYPES = ['PAYMENT_EXPIRED', 'AMOUNT_MISMATCH'];
// open-issues #44 임시 해석: DEPOSIT = 보증금이 동결 이후 단계로 넘어간 건
const DEPOSIT_STAGES = ['HELD', 'REFUNDED', 'FORFEITED', 'REFUND_FAILED'];
const WEEKEND = ['토', '일'];
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export const PAYMENT_SEED_EXTRA = {
  10: {
    overdueFee: 15000,
    deduction: { amount: 20000, grade: 'C', reason: '텐트 폴대가 휘어 수리비를 차감해요' },
  },
  41: { overdueFee: 50000 },
  45: { refundFailed: true },
  30: { deduction: { amount: 30000, grade: 'C', reason: '렌즈 덮개 파손 수리비' } },
};

/**
 * 보충 표 + 화면에서 일어난 보증금 기록(rental.depositExtra: 반납·신고 때 확정한 연체료, 보류, 차감 확정 —
 * mocks/depositLogic.js). 둘 다 있으면 기록이 이긴다
 */
export function paymentExtraOf(rental) {
  return { ...PAYMENT_SEED_EXTRA[rental.rentalId], ...rental.depositExtra };
}

export const SETTLEMENT_SEED_EXTRA = {
  33: { status: 'HELD' },
};

/** 영업일(주말 제외) 더하기 */
export function addBusinessDays(date, days) {
  let current = date;
  let left = days;
  while (left > 0) {
    current = addDays(current, 1);
    if (!WEEKEND.includes(weekdayOf(current))) {
      left -= 1;
    }
  }
  return current;
}

/** LocalDate.minusMonths와 같게(없는 날이면 그 달 마지막 날) */
export function minusMonths(date, months) {
  const [year, month, day] = date.split('-').map(Number);
  const total = year * 12 + (month - 1) - months;
  const y = Math.floor(total / 12);
  const m = (total % 12) + 1;
  const lastDay = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return `${y}-${String(m).padStart(2, '0')}-${String(Math.min(day, lastDay)).padStart(2, '0')}`;
}

function isDate(value) {
  if (!DATE_PATTERN.test(value)) {
    return false;
  }
  try {
    addDays(value, 0);
    return true;
  } catch {
    return false;
  }
}

function toRentalItem(item) {
  return {
    itemId: item.itemId,
    name: item.name,
    thumbnailUrl: item.thumbnailUrl,
    isDeleted: Boolean(item.isDeleted),
  };
}

function rentalOrdersOf(orders, rentalId) {
  return orders.filter((order) => order.rentalId === rentalId);
}

/** 대여료·보증금 결제가 승인된 적이 있는가. 주문이 없는 시드는 결제 대기·미승인 취소가 아니면 결제한 것으로 본다 */
export function isRentalPaid(rental, orders) {
  const rentalOrders = rentalOrdersOf(orders, rental.rentalId).filter(
    (order) => order.type !== 'EXTENSION',
  );
  if (rentalOrders.length > 0) {
    return rentalOrders.some((order) => PAID_STATUSES.includes(order.status));
  }
  return rental.status !== 'PENDING_PAYMENT' && !UNPAID_CANCEL_TYPES.includes(rental.cancelType);
}

/** 대여료(실제 받은 금액). 주문이 있으면 주문 금액 − 보증금(연장으로 종료일이 바뀌어도 그대로) */
export function rentalFeeOf(rental, orders) {
  const order = rentalOrdersOf(orders, rental.rentalId).find(
    (candidate) => candidate.type !== 'EXTENSION' && PAID_STATUSES.includes(candidate.status),
  );
  if (order) {
    return order.amount - rental.deposit;
  }
  return rental.pricePerDay * countDaysInclusive(rental.startDate, rental.endDate);
}

function paidExtensionOrders(orders, rentalId) {
  return rentalOrdersOf(orders, rentalId).filter(
    (order) => order.type === 'EXTENSION' && PAID_STATUSES.includes(order.status),
  );
}

// 목 연장 주문은 전액 취소(CANCELED)만 환불로 남는다. 환불 처리 중 금액은 없다(0).
function extensionRefundedOf(order) {
  return order.status === 'CANCELED' ? order.amount : 0;
}

/** payment.md REQ-039: 연장료 = APPROVED·PARTIAL_CANCELED 결제마다 amount − refunded − pending */
function receivedExtensionFees(orders, rentalId) {
  return paidExtensionOrders(orders, rentalId)
    .filter((order) => ['APPROVED', 'PARTIAL_CANCELED'].includes(order.status))
    .reduce((sum, order) => sum + order.amount - extensionRefundedOf(order), 0);
}

/**
 * 대여료·보증금 결제 한 건의 상태·환불액(대여 상태에서 정한다. rental.js cancel과 같은 규칙).
 * 대여 상세 목(rental.js)도 보증금 상태를 여기서 받는다.
 */
export function rentalPaymentState(rental, rentalFee, extra) {
  const { deposit, cancelFee } = rental;
  const amount = rentalFee + deposit;
  const overdueFee = Math.min(extra.overdueFee ?? 0, deposit);
  const deductionAmount = extra.deduction?.amount ?? 0;
  const state = {
    paymentStatus: 'APPROVED',
    depositStatus: deposit > 0 ? 'FROZEN' : 'NONE',
    refundedAmount: 0,
    depositRefundPending: false,
    overdueFee: 0,
    deduction: null,
  };
  if (rental.status === 'CANCELED') {
    return {
      ...state,
      paymentStatus: cancelFee > 0 ? 'PARTIAL_CANCELED' : 'CANCELED',
      depositStatus: deposit > 0 ? 'REFUNDED' : 'NONE',
      refundedAmount: amount - cancelFee,
    };
  }
  // 반납 완료: 연체료는 반납·신고 때 확정, 이상 판정·미반납 신고면 보류(REQ-049)
  // 차감 확정 뒤 반환이 실패했으면 REFUND_FAILED(재시도 배치가 완료한다)
  if (rental.status === 'RETURNED' && deposit > 0) {
    if (extra.refundFailed) {
      return {
        ...state,
        overdueFee,
        deduction: extra.deduction ?? null,
        depositStatus: 'REFUND_FAILED',
        depositRefundPending: true,
      };
    }
    return { ...state, overdueFee, depositStatus: extra.held ? 'HELD' : 'FROZEN' };
  }
  if (rental.status !== 'COMPLETED' || deposit === 0) {
    return state;
  }
  const back = deposit - overdueFee - deductionAmount;
  const settled = { ...state, overdueFee, deduction: extra.deduction ?? null };
  if (extra.refundFailed) {
    return { ...settled, depositStatus: 'REFUND_FAILED', depositRefundPending: true };
  }
  if (back > 0) {
    return {
      ...settled,
      paymentStatus: 'PARTIAL_CANCELED',
      depositStatus: 'REFUNDED',
      refundedAmount: back,
    };
  }
  return { ...settled, depositStatus: 'FORFEITED' };
}

/**
 * 대여자 본인의 결제 내역(최신 순). 항목은 { payment: PaymentHistoryResponse, rentalCanceled }.
 * rentalCanceled는 REFUND 필터용(응답에는 없음)
 */
export function buildPaymentHistory(rentals, orders, renterId, itemOf) {
  const entries = [];
  for (const rental of rentals.filter((candidate) => candidate.renterId === renterId)) {
    const base = {
      item: toRentalItem(itemOf(rental.itemId)),
      rentalId: rental.rentalId,
      startDate: rental.startDate,
      endDate: rental.endDate,
      cancelFee: rental.cancelFee,
    };
    const rentalCanceled = rental.status === 'CANCELED';
    if (isRentalPaid(rental, orders)) {
      const rentalFee = rentalFeeOf(rental, orders);
      const amount = rentalFee + rental.deposit;
      const order = rentalOrdersOf(orders, rental.rentalId).find(
        (candidate) => candidate.type !== 'EXTENSION' && PAID_STATUSES.includes(candidate.status),
      );
      // 0원 대여는 결제 레코드가 없다(state-machines §4)
      if (amount > 0) {
        entries.push({
          rentalCanceled,
          payment: {
            ...base,
            paymentId: rental.rentalId,
            paymentType: 'RENTAL',
            amount,
            rentalFee,
            deposit: rental.deposit,
            extensionFee: 0,
            createdAt: order?.createdAt ?? rental.createdAt,
            ...rentalPaymentState(rental, rentalFee, paymentExtraOf(rental)),
          },
        });
      }
    }
    for (const order of paidExtensionOrders(orders, rental.rentalId)) {
      entries.push({
        rentalCanceled,
        payment: {
          ...base,
          paymentId: 100000 + order.extensionId,
          paymentType: 'EXTENSION',
          amount: order.amount,
          rentalFee: 0,
          deposit: 0,
          extensionFee: order.amount,
          createdAt: order.createdAt ?? rental.createdAt,
          paymentStatus: order.status,
          depositStatus: null,
          refundedAmount: extensionRefundedOf(order),
          depositRefundPending: false,
          overdueFee: 0,
          deduction: null,
          cancelFee: 0,
        },
      });
    }
  }
  return entries.sort(
    (a, b) =>
      b.payment.createdAt.localeCompare(a.payment.createdAt) ||
      b.payment.paymentId - a.payment.paymentId,
  );
}

const TYPE_MATCHERS = {
  ALL: () => true,
  PAYMENT: ({ payment }) => PAID_STATUSES.includes(payment.paymentStatus),
  REFUND: ({ payment, rentalCanceled }) =>
    rentalCanceled && (payment.refundedAmount > 0 || payment.depositRefundPending),
  DEPOSIT: ({ payment }) => DEPOSIT_STAGES.includes(payment.depositStatus),
};

/**
 * 쿼리(type·from·to) 검사 + 기본값. 잘못되면 { error: { field, reason } }.
 * 백엔드처럼 기본값(to = 오늘, from = to − 3개월)을 채운 뒤 from > to면 field to.
 */
export function readHistoryQuery(searchParams, today) {
  const type = searchParams.get('type') ?? 'ALL';
  if (!PAYMENT_HISTORY_TYPES.includes(type)) {
    return { error: { field: 'type', reason: '형식이 올바르지 않습니다.' } };
  }
  for (const field of ['from', 'to']) {
    const value = searchParams.get(field);
    if (value != null && value !== '' && !isDate(value)) {
      return { error: { field, reason: '형식이 올바르지 않습니다.' } };
    }
  }
  const to = searchParams.get('to') || today;
  const from = searchParams.get('from') || minusMonths(to, PAYMENT_HISTORY_DEFAULT_MONTHS);
  if (compareDate(from, to) > 0) {
    return { error: { field: 'to', reason: '조회 종료일은 시작일보다 빠를 수 없습니다.' } };
  }
  return { type, from, to };
}

/** type·기간(createdAt의 날짜, 양끝 포함)으로 거른 PaymentHistoryResponse 목록 */
export function filterPaymentHistory(entries, { type, from, to }) {
  return entries
    .filter((entry) => {
      const date = entry.payment.createdAt.slice(0, 10);
      return compareDate(from, date) <= 0 && compareDate(date, to) <= 0;
    })
    .filter(TYPE_MATCHERS[type])
    .map((entry) => entry.payment);
}

/** 제공자 본인의 정산(최신 생성 순). payment.md REQ-039 금액 공식 그대로 */
export function buildSettlements(rentals, orders, ownerId, itemOf, today) {
  const settlements = [];
  for (const rental of rentals.filter((candidate) => candidate.ownerId === ownerId)) {
    const isCompleted = rental.status === 'COMPLETED';
    const isFeeCancel = rental.status === 'CANCELED' && rental.cancelFee > 0;
    // 받은 돈이 없으면 정산을 만들지 않는다
    if ((!isCompleted && !isFeeCancel) || !isRentalPaid(rental, orders)) {
      continue;
    }
    const extra = paymentExtraOf(rental);
    let grossAmount;
    let deductionAmount = 0;
    if (isCompleted) {
      const extensionFees = receivedExtensionFees(orders, rental.rentalId);
      const overdueFee = Math.min(extra.overdueFee ?? 0, rental.deposit);
      grossAmount = rentalFeeOf(rental, orders) + extensionFees + overdueFee;
      deductionAmount = extra.deduction?.amount ?? 0;
    } else {
      grossAmount = rental.cancelFee;
    }
    const feeAmount = Math.floor(grossAmount * SETTLEMENT_FEE_RATE);
    const payoutAmount = grossAmount - feeAmount + deductionAmount;
    if (payoutAmount <= 0) {
      continue;
    }
    const createdAt = isCompleted ? rental.completedAt : rental.canceledAt;
    const scheduledDate = addBusinessDays(createdAt.slice(0, 10), SETTLEMENT_PAYOUT_BUSINESS_DAYS);
    const status =
      SETTLEMENT_SEED_EXTRA[rental.rentalId]?.status ??
      (compareDate(scheduledDate, today) <= 0 ? 'PAID' : 'SCHEDULED');
    settlements.push({
      createdAt,
      settlement: {
        settlementId: rental.rentalId,
        rentalId: rental.rentalId,
        item: toRentalItem(itemOf(rental.itemId)),
        startDate: rental.startDate,
        endDate: rental.endDate,
        source: isCompleted ? 'COMPLETED' : 'CANCEL_FEE',
        status,
        grossAmount,
        feeAmount,
        deductionAmount,
        payoutAmount,
        scheduledDate,
        // 지급 배치(매일 00:30) 흉내
        paidAt: status === 'PAID' ? `${scheduledDate}T00:30:00` : null,
      },
    });
  }
  return settlements
    .sort(
      (a, b) =>
        b.createdAt.localeCompare(a.createdAt) ||
        b.settlement.settlementId - a.settlement.settlementId,
    )
    .map((entry) => entry.settlement);
}

/** 합계: 상태별 지급액 합 */
export function sumSettlements(settlements) {
  const totals = { scheduledTotal: 0, heldTotal: 0, paidTotal: 0 };
  const keys = { SCHEDULED: 'scheduledTotal', HELD: 'heldTotal', PAID: 'paidTotal' };
  for (const settlement of settlements) {
    totals[keys[settlement.status]] += settlement.payoutAmount;
  }
  return totals;
}
