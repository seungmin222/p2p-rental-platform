// 목록 목(빌린 내역·내 물품·물품별 예약 현황)의 순수 함수. 저장소(rental.js의 db)를 받아 계산하고 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 탭 표를 여기 따로 둔다(state-machines.md "UI 탭 매핑").
import { compareDate } from '../shared/lib/date';

// policies.md rental-history.page·my-items.page·item-rentals.page (모두 20, 요청의 size는 받지 않는다)
export const PAGE_SIZE = 20;

/** 빌린 내역 탭 → 상태 */
export const HISTORY_TAB_STATUSES = {
  UPCOMING: ['PENDING_PAYMENT', 'RESERVED'],
  ONGOING: ['RENTING', 'OVERDUE', 'RETURNED'],
  DONE: ['COMPLETED', 'CANCELED'],
};

/** 내 물품 대여 중 탭 기준 */
const RENTING_STATUSES = ['RENTING', 'OVERDUE'];
/** 다가오는 예약(REQ-020): 이 상태 중 startDate ≥ 오늘 */
const UPCOMING_STATUSES = ['RESERVED', 'PENDING_PAYMENT'];

export const MOCK_MY_ITEM_TABS = ['AVAILABLE', 'RENTING', 'HIDDEN'];

/** PageResponse 모양으로 자른다 */
export function toPageResponse(all, page, size = PAGE_SIZE) {
  const items = all.slice(page * size, (page + 1) * size);
  return {
    items,
    page,
    size,
    totalElements: all.length,
    hasNext: (page + 1) * size < all.length,
  };
}

// 백엔드 page는 int(int32). 넘는 값은 타입 변환 실패로 400
const INT32_MAX = 2_147_483_647;

/** 쿼리의 page. 없으면 0, 0 이상 int32 범위의 정수가 아니면 null(400) */
export function readPage(value) {
  if (value == null || value === '') {
    return 0;
  }
  if (!/^\d+$/.test(value)) {
    return null;
  }
  const page = Number(value);
  return page <= INT32_MAX ? page : null;
}

/** 빌린 내역 정렬(rental.md REQ-019): OVERDUE 먼저 → startDate DESC → id DESC */
export function compareRentalHistory(a, b) {
  const overdue = Number(b.status === 'OVERDUE') - Number(a.status === 'OVERDUE');
  return overdue || compareDate(b.startDate, a.startDate) || b.rentalId - a.rentalId;
}

/** 내가 대여자인 건 중 tab에 속한 것을 정렬해서 */
export function listRentalHistory(rentals, renterId, tab) {
  const statuses = HISTORY_TAB_STATUSES[tab];
  return rentals
    .filter((rental) => rental.renterId === renterId && statuses.includes(rental.status))
    .sort(compareRentalHistory);
}

/** 물품에 RENTING·OVERDUE 대여가 있으면 대여 중 탭 */
export function isRentingItem(rentals, itemId) {
  return rentals.some(
    (rental) => rental.itemId === itemId && RENTING_STATUSES.includes(rental.status),
  );
}

/**
 * 다가오는 예약 건수: RESERVED·PENDING_PAYMENT 중 startDate ≥ today. 결제 시간이 지난 PENDING_PAYMENT는
 * 세지 않는다(캘린더 점유와 같은 기준, rental.md REQ-012). now는 서버 시각 문자열(오프셋 없는 서울 시각).
 */
export function countUpcomingRentals(rentals, itemId, today, now) {
  return rentals.filter(
    (rental) =>
      rental.itemId === itemId &&
      UPCOMING_STATUSES.includes(rental.status) &&
      (rental.status !== 'PENDING_PAYMENT' || rental.paymentExpiresAt > now) &&
      compareDate(rental.startDate, today) >= 0,
  ).length;
}

/** 내 물품 중 tab에 속한 것(items는 삭제되지 않은 내 물품). 숨김(HIDDEN)은 1순위에 없어 빈 목록. 최근 등록(id 큰) 순 */
export function listMyItems(items, rentals, tab) {
  if (tab === 'HIDDEN') {
    return [];
  }
  const isRentingTab = tab === 'RENTING';
  return items
    .filter((item) => isRentingItem(rentals, item.itemId) === isRentingTab)
    .sort((a, b) => b.itemId - a.itemId);
}

/** 예약 현황에서 뒤로 보내는 지난 건 */
const PAST_STATUSES = ['COMPLETED', 'CANCELED'];

/**
 * 물품별 예약 현황 정렬(open-issues #20): 다가오는·진행 중 건(PENDING_PAYMENT~RETURNED) startDate ASC 먼저,
 * 지난 건(COMPLETED·CANCELED) startDate DESC 뒤에. 같은 시작일은 id ASC
 */
function compareItemRental(a, b) {
  const aPast = PAST_STATUSES.includes(a.status);
  const bPast = PAST_STATUSES.includes(b.status);
  if (aPast !== bPast) {
    return aPast ? 1 : -1;
  }
  const byDate = aPast
    ? compareDate(b.startDate, a.startDate)
    : compareDate(a.startDate, b.startDate);
  return byDate || a.rentalId - b.rentalId;
}

/** 물품별 예약 현황(REQ-020). CANCELED 포함 */
export function listItemRentals(rentals, itemId) {
  return rentals.filter((rental) => rental.itemId === itemId).sort(compareItemRental);
}
