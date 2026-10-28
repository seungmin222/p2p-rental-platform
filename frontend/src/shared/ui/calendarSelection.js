// 대여 캘린더(REQ-012) 기간 선택 규칙. 화면과 분리해 node 환경에서 테스트한다.
import { addDays, compareDate, countDaysInclusive } from '../lib/date';

/** 날짜 상태 코드(rental.md REQ-012 응답 days[].status) */
export const DAY_STATUS = {
  AVAILABLE: 'AVAILABLE',
  BOOKED: 'BOOKED',
  UNAVAILABLE: 'UNAVAILABLE',
};

/** 선택을 거부한 이유 */
export const SELECT_REJECT_REASON = {
  DAY_NOT_AVAILABLE: 'DAY_NOT_AVAILABLE', // 누른 날이 AVAILABLE 아님
  RANGE_INCLUDES_UNAVAILABLE: 'RANGE_INCLUDES_UNAVAILABLE', // 사이에 BOOKED·UNAVAILABLE
  TOO_SHORT: 'TOO_SHORT',
  TOO_LONG: 'TOO_LONG',
};

/** days 배열(여러 달을 합쳐도 됨) → 날짜별 상태 Map */
export function toStatusMap(days) {
  return new Map(days.map(({ date, status }) => [date, status]));
}

/** days에 없는 날짜는 UNAVAILABLE로 본다(아직 불러오지 않은 달 포함). */
export function statusOf(statusByDate, date) {
  return statusByDate.get(date) ?? DAY_STATUS.UNAVAILABLE;
}

/**
 * 날짜를 눌렀을 때의 다음 선택 값.
 *
 * - 선택 없음 또는 범위 완성 상태 → 누른 날이 새 시작일
 * - 시작일만 있을 때: 같은 날 → 1일 범위, 앞선 날 → 시작일 교체, 뒤의 날 → 범위 검사 후 완성
 * - 검사 순서: 누른 날 상태 → TOO_SHORT → TOO_LONG → 사이(시작·끝 포함) 날짜 상태.
 *   시작일도 다시 검사하는 것은 고른 뒤 days가 새로 불러와져 BOOKED가 됐을 수 있어서다.
 * - 거부하면 기존 value를 그대로 돌려준다(시작일 유지).
 *
 * @param {{startDate: string, endDate: string | null} | null} value
 * @param {string} date 누른 날짜 "YYYY-MM-DD"
 * @param {{statusByDate: Map<string, string>, minDays: number, maxDays: number}} options
 * @returns {{value: object | null, reason?: string}}
 */
export function selectDate(value, date, { statusByDate, minDays, maxDays }) {
  if (statusOf(statusByDate, date) !== DAY_STATUS.AVAILABLE) {
    return { value, reason: SELECT_REJECT_REASON.DAY_NOT_AVAILABLE };
  }
  if (!value || value.endDate) {
    return { value: { startDate: date, endDate: null } };
  }
  const { startDate } = value;
  if (compareDate(date, startDate) < 0) {
    return { value: { startDate: date, endDate: null } };
  }

  const days = countDaysInclusive(startDate, date);
  if (days < minDays) {
    return { value, reason: SELECT_REJECT_REASON.TOO_SHORT };
  }
  if (days > maxDays) {
    return { value, reason: SELECT_REJECT_REASON.TOO_LONG };
  }
  for (let d = startDate; compareDate(d, date) <= 0; d = addDays(d, 1)) {
    if (statusOf(statusByDate, d) !== DAY_STATUS.AVAILABLE) {
      return { value, reason: SELECT_REJECT_REASON.RANGE_INCLUDES_UNAVAILABLE };
    }
  }
  return { value: { startDate, endDate: date } };
}

/**
 * 칸 모양 결정용. 'single'(시작일만 있거나 1일 범위) · 'start' · 'middle' · 'end' · null
 */
export function rangePosition(value, date) {
  if (!value) {
    return null;
  }
  const { startDate, endDate } = value;
  if (!endDate || startDate === endDate) {
    return date === startDate ? 'single' : null;
  }
  if (date === startDate) {
    return 'start';
  }
  if (date === endDate) {
    return 'end';
  }
  return compareDate(date, startDate) > 0 && compareDate(date, endDate) < 0 ? 'middle' : null;
}
