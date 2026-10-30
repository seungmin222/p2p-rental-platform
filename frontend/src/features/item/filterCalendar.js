// 검색 필터의 대여 희망 기간 선택용 Calendar days. 물품이 정해져 있지 않아 오늘부터는 모두 선택 가능, 지난 날짜는 선택 불가.
// (가능 기간·예약과 맞는지는 서버가 거른다 — openapi GET /api/items startDate·endDate)
import { buildMonthGrid, compareDate } from '../../shared/lib/date';

export function buildFilterDays(month, today) {
  return buildMonthGrid(month)
    .filter(Boolean)
    .map((date) => ({ date, status: compareDate(date, today) < 0 ? 'UNAVAILABLE' : 'AVAILABLE' }));
}
