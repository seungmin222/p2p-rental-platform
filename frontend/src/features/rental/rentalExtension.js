// 대여 연장(REQ-038) 화면의 순수 함수. 서버가 최종 검증하므로 여기 계산은 고를 수 있는 날을 미리 보여 주는 보조다.
import { addDays, compareDate, monthOf } from '../../shared/lib/date';
import { parseServerDateTime } from '../../shared/lib/format';

/** 연장 날짜 칩에 필요한 달(종료일 다음 날 ~ 종료일 + maxDays가 걸친 달, 1~2개) */
export function getExtensionMonths(endDate, maxDays) {
  const first = monthOf(addDays(endDate, 1));
  const last = monthOf(addDays(endDate, maxDays));
  return first === last ? [first] : [first, last];
}

/**
 * 새 종료일 후보(종료일 + 1 ~ 종료일 + maxDays). 연장 구간은 종료일 다음 날부터 이어져야 하므로
 * 캘린더에서 처음으로 AVAILABLE이 아닌 날이 나오면 그날부터는 고를 수 없다. 서버가 알려 준 최대 날짜(maxEndDate)가 있으면 그 뒤도 막는다.
 *
 * @param {string} endDate 지금 종료일 "YYYY-MM-DD"
 * @param {{date: string, status: string}[] | null} days 캘린더 응답을 합친 것. null이면(못 불러옴) 캘린더로는 막지 않는다
 * @param {number} maxDays policies.md extension.max-days-per
 * @param {string | null | undefined} maxEndDate 서버가 알려 준 최대 날짜. undefined = 모름, null = 하루도 안 됨
 * @returns {{date: string, days: number, isSelectable: boolean}[]}
 */
export function getExtensionOptions(endDate, days, maxDays, maxEndDate) {
  const statusByDate = days ? new Map(days.map((day) => [day.date, day.status])) : null;
  let isBlocked = maxEndDate === null;
  const options = [];
  for (let count = 1; count <= maxDays; count += 1) {
    const date = addDays(endDate, count);
    if (statusByDate && statusByDate.get(date) !== 'AVAILABLE') {
      isBlocked = true;
    }
    if (maxEndDate && compareDate(date, maxEndDate) > 0) {
      isBlocked = true;
    }
    options.push({ date, days: count, isSelectable: !isBlocked });
  }
  return options;
}

/**
 * 연장 409·400의 error.details에서 "연장 가능한 최대 날짜". details에 maxEndDate가 있으면 그 날짜,
 * 연장 기간 오류인데 비어 있으면 null(하루도 안 됨), 그 밖의 오류면 undefined(모름).
 */
export function readMaxEndDate(error) {
  if (!MAX_END_DATE_CODES.includes(error?.code)) {
    return undefined;
  }
  const detail = error.details?.find((item) => item.field === 'maxEndDate');
  return detail && /^\d{4}-\d{2}-\d{2}$/.test(detail.reason) ? detail.reason : null;
}

// 서버가 maxEndDate를 싣는 코드(rental.md REQ-038)
const MAX_END_DATE_CODES = ['RENTAL_EXTENSION_PERIOD_CONFLICT', 'RENTAL_OUT_OF_AVAILABLE_PERIOD'];

/** 연장료 = 일 대여료(예약 시점) × 연장 일수. 확정 금액은 서버 응답 additionalFee다 */
export function estimateExtensionFee(pricePerDay, days) {
  return pricePerDay * days;
}

/** 결제 대기 연장의 결제 시간이 지났는가(만료 배치가 정리할 때까지 상세를 다시 불러오는 기준) */
export function isExtensionPaymentExpired(rental, now) {
  const expiry = parseServerDateTime(rental.pendingExtension?.expiresAt)?.getTime();
  return expiry != null && expiry <= now;
}
