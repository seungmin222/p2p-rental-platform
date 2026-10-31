// 대여 화면에서 응답을 가공하는 순수 함수. node 환경에서 테스트한다.
import { compareDate, countDaysInclusive } from '../../shared/lib/date';
import { parseServerDateTime } from '../../shared/lib/format';
import { CANCEL_FEE, RETURN_CHECKLIST_ITEM } from './constants';

const SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000;

/**
 * 여러 달의 캘린더 응답(RentalCalendarResponse, 아직 없으면 undefined)을 Calendar의 days 하나로 합친다.
 * 같은 날짜가 겹치면 뒤의 응답이 이긴다. 날짜순으로 정렬한다.
 */
export function mergeCalendarDays(responses) {
  const byDate = new Map();
  for (const response of responses) {
    for (const day of response?.days ?? []) {
      byDate.set(day.date, day);
    }
  }
  return [...byDate.values()].sort((a, b) => compareDate(a.date, b.date));
}

/**
 * inspections 배열에서 phase(HANDOVER·RETURN) 기록 하나. 아직 불러오지 않았으면 undefined, 기록이 없으면 null.
 * undefined와 null을 구분하는 것은 getRentalActions가 "못 불러옴"이면 버튼을 그리지 않아서다.
 */
export function findInspection(inspections, phase) {
  if (!inspections) {
    return undefined;
  }
  return inspections.find((inspection) => inspection.phase === phase) ?? null;
}

/** 반납 예정일이 지난 일수(today − endDate). 지나지 않았으면 0. */
export function overdueDays(endDate, today) {
  if (compareDate(today, endDate) <= 0) {
    return 0;
  }
  return countDaysInclusive(endDate, today) - 1;
}

/**
 * 지금 단독 취소하면 붙는 수수료(policies.md §2). 대여자가 RESERVED를 시작일 2일 전 ~ 전날 취소할 때만
 * 대여료 × 30%(원 미만 절사), 그 외 0. 확정 금액은 취소 응답의 cancelFee가 기준이다.
 */
export function estimateCancelFee(rental, today) {
  if (rental.status !== 'RESERVED' || rental.myRole !== 'RENTER') {
    return 0;
  }
  if (compareDate(today, rental.startDate) >= 0) {
    return 0;
  }
  const daysBefore = countDaysInclusive(today, rental.startDate) - 1;
  if (daysBefore >= CANCEL_FEE.FREE_UNTIL_DAYS_BEFORE) {
    return 0;
  }
  return Math.floor((rental.rentalFee * CANCEL_FEE.RATE_PERCENT) / 100);
}

/** 결제 금액 = 대여료 + 보증금. 예약 화면의 미리보기용이고 확정 금액은 서버가 계산한다. */
export function calcTotalAmount(pricePerDay, rentalDays, deposit) {
  return pricePerDay * rentalDays + deposit;
}

/** 결제 대기이고 만료 시각이 지났는가(now는 밀리초) */
export function isPaymentExpired(rental, now) {
  if (rental.status !== 'PENDING_PAYMENT') {
    return false;
  }
  const expiry = parseServerDateTime(rental.paymentExpiresAt)?.getTime();
  return expiry != null && expiry <= now;
}

/** 단독 취소로 돌려받을 금액(payment.md 취소 시 환불). 결제 대기 취소는 결제한 돈이 없어 0 */
export function refundOnCancel(statusBeforeCancel, canceled) {
  return statusBeforeCancel === 'RESERVED' ? canceled.totalAmount - canceled.cancelFee : 0;
}

/**
 * 보증금 처리 결과 안내. 없으면 null.
 * TODO: 대여 상세 응답에 보증금 상태·누적 환불액·연체료가 없어 있는 필드로만 추정한다. 연체료(REQ-023)·차감(REQ-049)이
 * 들어오면 "보증금 전액 반환" 가정이 틀어지므로 그 전에 백엔드와 필드를 합의해 바꾼다.
 */
export function getDepositNotice(rental, returnInspection) {
  if (rental.deposit <= 0 || !returnInspection) {
    return null;
  }
  if (rental.status === 'COMPLETED' && returnInspection.result === 'NORMAL') {
    return { kind: 'REFUNDED', amount: rental.deposit };
  }
  if (rental.status === 'RETURNED' && returnInspection.result === 'NORMAL') {
    return { kind: 'REFUNDING' };
  }
  if (rental.status === 'RETURNED' && returnInspection.result === 'ABNORMAL') {
    return { kind: 'WAITING' };
  }
  return null;
}

/** 반납 폼의 답({ KEY: true | false | null })을 API 체크리스트 맵으로. 5개 키를 모두 boolean으로 채운다. */
export function toChecklist(answers) {
  return Object.fromEntries(
    Object.keys(RETURN_CHECKLIST_ITEM).map((key) => [key, answers?.[key] === true]),
  );
}

/** "2026-10-09" → "10/9" */
function shortDate(date) {
  const [, month, day] = date.split('-');
  return `${Number(month)}/${Number(day)}`;
}

/** 요약 줄 기간: "10/9 ~ 10/11", 하루면 "10/9" */
export function formatShortRange(startDate, endDate) {
  return startDate === endDate
    ? shortDate(startDate)
    : `${shortDate(startDate)} ~ ${shortDate(endDate)}`;
}

function seoulParts(dateTime) {
  const date = parseServerDateTime(dateTime);
  if (!date) {
    return null;
  }
  const d = new Date(date.getTime() + SEOUL_OFFSET_MS);
  return {
    month: d.getUTCMonth() + 1,
    day: d.getUTCDate(),
    time: `${String(d.getUTCHours()).padStart(2, '0')}:${String(d.getUTCMinutes()).padStart(2, '0')}`,
  };
}

/** 서버 일시 → "10월 30일 14:05"(서울). 해석할 수 없으면 "". */
export function formatDateTime(dateTime) {
  const parts = seoulParts(dateTime);
  return parts ? `${parts.month}월 ${parts.day}일 ${parts.time}` : '';
}

/** 서버 일시 → "10/9"(서울, 사진 캡션). 해석할 수 없으면 "". */
export function formatShortDate(dateTime) {
  const parts = seoulParts(dateTime);
  return parts ? `${parts.month}/${parts.day}` : '';
}
