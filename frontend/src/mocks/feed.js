// 동네 피드·검색·카테고리 목(openapi.json GET /api/items/feed, GET /api/items, GET /api/categories). 모두 공개 API라 AT를 보지 않는다.
// 계산(필터·정렬·커서·페이지·검증)은 itemLogic.js 순수 함수, 시드는 item.js·itemSeed.js.
// 핸들러 순서: /api/items/feed는 /api/items/:itemId보다 먼저 맞아야 해서 handlers.js가 이 배열을 물품 상세보다 앞에 둔다.
import { delay, http } from 'msw';

import { todayInSeoul } from '../shared/lib/date';
import { listMockItems, MOCK_CATEGORIES } from './item';
import { listFeed, parseFeedQuery, parseSearchQuery, searchPage } from './itemLogic';
import { fail, ok } from './response';
import { db, occupiesPeriod } from './rentalStore';

const INVALID_INPUT = 'COMMON_INVALID_INPUT';
const INVALID_INPUT_MESSAGE = '입력값이 올바르지 않습니다.';

/** ItemSummaryResponse. 대여 중 배지는 대여 DB(RENTING·OVERDUE)와 시드의 표시용 플래그를 합친다 */
function toSummary(item) {
  const isRentingByRental = db.rentals.some(
    (rental) =>
      rental.itemId === item.itemId && (rental.status === 'RENTING' || rental.status === 'OVERDUE'),
  );
  return {
    itemId: item.itemId,
    name: item.name,
    thumbnailUrl: item.thumbnailUrl,
    regionName: item.regionName,
    bumpedAt: item.bumpedAt,
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    isRenting: isRentingByRental || item.isRentingMock === true,
    wishCount: item.counts.wishCount,
    chatCount: item.counts.chatCount,
  };
}

export const feedHandlers = [
  http.get('/api/categories', async () => {
    await delay(150);
    return ok(MOCK_CATEGORIES);
  }),

  // 커서 방식. 검사 순서: 쿼리 형식 400 → 조회. 비회원도 보므로 regionId를 항상 받는다(없으면 400)
  http.get('/api/items/feed', async ({ request }) => {
    const { errors, query } = parseFeedQuery(new URL(request.url).searchParams);
    if (errors.length > 0) {
      return fail(400, INVALID_INPUT, INVALID_INPUT_MESSAGE, errors);
    }
    await delay(300);
    const page = listFeed(listMockItems(), query);
    return ok({ ...page, items: page.items.map(toSummary) });
  }),

  // page 방식, 20건 고정
  http.get('/api/items', async ({ request }) => {
    const { errors, query } = parseSearchQuery(new URL(request.url).searchParams);
    if (errors.length > 0) {
      return fail(400, INVALID_INPUT, INVALID_INPUT_MESSAGE, errors);
    }
    await delay(300);
    const occupying = db.rentals.filter(occupiesPeriod);
    const page = searchPage(listMockItems(), occupying, query, todayInSeoul());
    return ok({ ...page, items: page.items.map(toSummary) });
  }),
];
