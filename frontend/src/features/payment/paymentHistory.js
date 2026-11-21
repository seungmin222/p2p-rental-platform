// 결제·보증금 내역(REQ-033)과 정산 내역(REQ-039)의 표시·필터 순수 함수. node에서 테스트한다.
//
// open-issues #44 (임시 해석, 확정 전): 한 항목 = 대여자 본인의 결제 한 건. type 필터 PAYMENT = 승인된 결제 전체,
// REFUND = 취소 환불이 있는 건, DEPOSIT = 보증금이 동결 이후 단계로 넘어간 건. 한 결제가 여러 구분에 함께 들어가므로
// 항목마다 "구분" 라벨은 붙이지 않고 필터로만 쓴다. 해석이 바뀌면 PAYMENT_HISTORY_TYPES와 getPaymentHistoryView만 고친다.
import { compareDate, formatMonthDay } from '../../shared/lib/date';
import { formatPrice } from '../../shared/lib/format';
import {
  DEPOSIT_STATUS,
  PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS,
  PAYMENT_STATUS,
  PAYMENT_TYPE,
  SETTLEMENT_SOURCE,
  SETTLEMENT_STATUS,
} from './constants';

/** type 필터(키 = API type 값). description은 #44 임시 해석 */
export const PAYMENT_HISTORY_TYPES = {
  ALL: { label: '전체', description: null },
  PAYMENT: { label: '결제', description: '승인된 결제' },
  REFUND: { label: '환불', description: '예약 취소로 환불한 결제' },
  DEPOSIT: { label: '보증금', description: '보증금 반환·차감·보류가 진행된 결제' },
};

export const DEFAULT_HISTORY_TYPE = 'ALL';

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** 실제 있는 "YYYY-MM-DD"인가 */
export function isDateString(value) {
  if (typeof value !== 'string' || !DATE_PATTERN.test(value)) {
    return false;
  }
  try {
    formatMonthDay(value);
    return true;
  } catch {
    return false;
  }
}

/**
 * URL 쿼리 → 필터 { type, from, to }. 모르는 type은 ALL, 날짜가 아니면 null(서버 기본),
 * from > to면 둘 다 버린다(서버 400을 부르지 않게).
 */
export function readHistoryFilter(searchParams) {
  const rawType = searchParams.get('type');
  const type =
    rawType != null && Object.hasOwn(PAYMENT_HISTORY_TYPES, rawType)
      ? rawType
      : DEFAULT_HISTORY_TYPE;
  let from = isDateString(searchParams.get('from')) ? searchParams.get('from') : null;
  let to = isDateString(searchParams.get('to')) ? searchParams.get('to') : null;
  if (from && to && compareDate(from, to) > 0) {
    from = null;
    to = null;
  }
  return { type, from, to };
}

/** 필터 → URL 쿼리에 쓸 값. 기본값(ALL·빈 날짜)은 넣지 않는다 */
export function writeHistoryFilter(params, { type, from, to }) {
  const next = new URLSearchParams(params);
  for (const [key, value] of [
    ['type', type === DEFAULT_HISTORY_TYPE ? null : type],
    ['from', from],
    ['to', to],
  ]) {
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
  }
  return next;
}

/** API 요청 쿼리. 비어 있는 값은 보내지 않는다(서버 기본: type ALL, to 오늘, from = to − 3개월) */
export function toHistoryParams({ type, from, to }, page) {
  const params = { page };
  if (type !== DEFAULT_HISTORY_TYPE) {
    params.type = type;
  }
  if (from) {
    params.from = from;
  }
  if (to) {
    params.to = to;
  }
  return params;
}

/**
 * 기간 입력 검사. to를 비우면 서버가 오늘로 채우므로 오늘과 비교한다(서버도 기본값을 채운 뒤 from > to면 400).
 * 문제가 없으면 null.
 */
export function validateHistoryRange({ from, to }, today) {
  if (from && !isDateString(from)) {
    return '시작일을 확인해 주세요';
  }
  if (to && !isDateString(to)) {
    return '종료일을 확인해 주세요';
  }
  if (from && compareDate(from, to || today) > 0) {
    return to ? '시작일이 종료일보다 늦어요' : '시작일이 오늘보다 늦어요';
  }
  return null;
}

function formatDot(date) {
  return date.replaceAll('-', '.');
}

/** 목록 위 기간 요약 문구 */
export function getRangeLabel({ from, to }) {
  if (!from && !to) {
    return `최근 ${PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS}개월`;
  }
  if (!from) {
    return `${formatDot(to)}까지 ${PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS}개월`;
  }
  return `${formatDot(from)} ~ ${to ? formatDot(to) : '오늘'}`;
}

const DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

/** 서버 일시(오프셋 없는 서울 시각) → "2026.11.03 14:20". 해석할 수 없으면 "" */
export function formatDateTime(dateTime) {
  const match = typeof dateTime === 'string' ? DATE_TIME_PATTERN.exec(dateTime) : null;
  return match ? `${match[1]}.${match[2]}.${match[3]} ${match[4]}:${match[5]}` : '';
}

function itemName(item) {
  return item?.isDeleted ? '삭제된 물품' : (item?.name ?? '');
}

/**
 * PaymentHistoryResponse → 카드 표시 값.
 * rows: 금액 줄 [{ label, value, tone? }], notes: 한 줄 안내.
 */
export function getPaymentHistoryView(payment) {
  const isExtension = payment.paymentType === 'EXTENSION';
  const rows = isExtension
    ? [{ label: '연장료', value: formatPrice(payment.extensionFee) }]
    : [
        { label: '대여료', value: formatPrice(payment.rentalFee) },
        { label: '보증금', value: formatPrice(payment.deposit) },
      ];
  if (payment.overdueFee > 0) {
    rows.push({ label: '연체료', value: `−${formatPrice(payment.overdueFee)}`, tone: 'danger' });
  }
  if (payment.deduction) {
    rows.push({
      label: '보증금 차감',
      value: `−${formatPrice(payment.deduction.amount)}`,
      tone: 'danger',
    });
  }
  if (payment.cancelFee > 0) {
    rows.push({ label: '취소 수수료', value: formatPrice(payment.cancelFee) });
  }
  if (payment.refundedAmount > 0) {
    rows.push({ label: '환불 금액', value: formatPrice(payment.refundedAmount) });
  }

  const notes = [];
  if (payment.deduction?.reason) {
    notes.push(`차감 사유: ${payment.deduction.reason}`);
  }
  if (payment.depositRefundPending) {
    notes.push('환불을 처리하고 있어요');
  }

  const status = PAYMENT_STATUS[payment.paymentStatus];
  // 연장 결제는 보증금 상태가 없다(null)
  const deposit = isExtension ? null : DEPOSIT_STATUS[payment.depositStatus];
  return {
    itemName: itemName(payment.item),
    isItemDeleted: Boolean(payment.item?.isDeleted),
    kindLabel: PAYMENT_TYPE[payment.paymentType]?.label ?? '',
    paidAt: formatDateTime(payment.createdAt),
    status: { label: status?.label ?? payment.paymentStatus, tone: status?.tone ?? 'gray' },
    deposit: deposit ? { label: `보증금 ${deposit.label}`, tone: deposit.tone } : null,
    amount: formatPrice(payment.amount),
    rows,
    notes,
  };
}

/** SettlementResponse → 카드 표시 값 */
export function getSettlementView(settlement) {
  const status = SETTLEMENT_STATUS[settlement.status];
  const rows = [
    {
      label: settlement.source === 'CANCEL_FEE' ? '취소 수수료' : '대여료·연장료·연체료',
      value: formatPrice(settlement.grossAmount),
    },
    { label: '플랫폼 수수료', value: `−${formatPrice(settlement.feeAmount)}` },
  ];
  if (settlement.deductionAmount > 0) {
    rows.push({ label: '보증금 차감액', value: `+${formatPrice(settlement.deductionAmount)}` });
  }
  let dateText;
  if (settlement.status === 'PAID') {
    const paidAt = formatDateTime(settlement.paidAt);
    dateText = paidAt ? `${paidAt} 지급` : '지급 완료';
  } else if (settlement.status === 'HELD') {
    dateText = '분쟁이 끝나면 지급돼요';
  } else {
    dateText = isDateString(settlement.scheduledDate)
      ? `${formatMonthDay(settlement.scheduledDate)} 지급 예정`
      : '';
  }
  return {
    itemName: itemName(settlement.item),
    isItemDeleted: Boolean(settlement.item?.isDeleted),
    sourceLabel: SETTLEMENT_SOURCE[settlement.source]?.label ?? '',
    status: { label: status?.label ?? settlement.status, tone: status?.tone ?? 'gray' },
    payout: formatPrice(settlement.payoutAmount),
    rows,
    dateText,
  };
}
