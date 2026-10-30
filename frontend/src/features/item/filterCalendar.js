// 검색 필터의 대여 희망 기간 선택용 Calendar days. 물품이 정해져 있지 않아 오늘부터는 모두 선택 가능, 지난 날짜는 선택 불가.
// (가능 기간·예약과 맞는지는 서버가 거른다 — openapi GET /api/items startDate·endDate)
import { addMonths, buildMonthGrid, compareDate } from '../../shared/lib/date';

export function buildFilterDays(month, today) {
  return buildMonthGrid(month)
    .filter(Boolean)
    .map((date) => ({ date, status: compareDate(date, today) < 0 ? 'UNAVAILABLE' : 'AVAILABLE' }));
}

/** fromMonth ~ toMonth를 합친 days. Calendar는 범위 검사를 받은 days 전체로 하므로 달을 넘긴 기간을 고르려면 모두 넘겨야 한다 */
export function buildFilterDaysInRange(fromMonth, toMonth, today) {
  const days = [];
  for (let month = fromMonth; month <= toMonth; month = addMonths(month, 1)) {
    days.push(...buildFilterDays(month, today));
  }
  return days;
}
