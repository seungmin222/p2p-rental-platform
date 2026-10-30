// 물품 피드·검색 목의 순수 함수(필터·정렬·커서·페이지·검증). 저장소는 받지 않고 항목 배열만 받아 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값을 여기 따로 둔다.
//
// 목이 기대하는 항목 모양(item.js findMockItem): { itemId, name, description, categoryId, regionId, pricePerDay, deposit,
//   bumpedAt('YYYY-MM-DDTHH:mm:ss'), isDeleted, availablePeriods: [{startDate, endDate}] }
import { compareDate } from '../shared/lib/date';
import { readPage, toPageResponse } from './pageList';

// policies.md feed.page(20) / search.page(20)
export const FEED_PAGE_SIZE = 20;
const SORTS = ['ACCURACY', 'LATEST', 'PRICE_ASC'];

export function invalidInput(field, reason) {
  return { field, reason };
}

/** 최신순: bumpedAt DESC, id DESC (item.md REQ-009) */
export function compareLatest(a, b) {
  if (a.bumpedAt !== b.bumpedAt) {
    return a.bumpedAt < b.bumpedAt ? 1 : -1;
  }
  return b.itemId - a.itemId;
}

// ── 커서 ─────────────────────────────────────────────────────────────────────

/** 커서 = (bumpedAt, id) 인코딩(item.md REQ-009). 문자열은 ASCII뿐이라 btoa로 충분 */
export function encodeCursor(item) {
  return btoa(`${item.bumpedAt}|${item.itemId}`);
}

/** 되돌리지 못하면 null(400) */
export function decodeCursor(cursor) {
  try {
    const [bumpedAt, id] = atob(cursor).split('|');
    const itemId = Number(id);
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(bumpedAt) || !Number.isInteger(itemId)) {
      return null;
    }
    return { bumpedAt, itemId };
  } catch {
    return null;
  }
}

// ── 공통 ─────────────────────────────────────────────────────────────────────

function readLong(value) {
  return typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : null;
}

/** 공개된(삭제되지 않은) 물품. 숨김(HIDDEN)은 1순위 시드에 없다 */
function isVisible(item) {
  return !item.isDeleted;
}

// ── 피드 ─────────────────────────────────────────────────────────────────────

/**
 * 피드 쿼리 검증. regionId가 없거나 숫자가 아니면 400(비회원·대표 동네 없음과 같다 — 공개 API라 토큰으로 대표 동네를 알 수 없다).
 * @returns {{ errors: {field, reason}[], query?: { regionId, categoryId, cursor } }}
 */
export function parseFeedQuery(searchParams) {
  const errors = [];
  const regionId = readLong(searchParams.get('regionId'));
  if (regionId == null) {
    errors.push(invalidInput('regionId', '동네를 지정해야 합니다.'));
  }
  const categoryText = searchParams.get('categoryId');
  const categoryId = categoryText == null ? null : readLong(categoryText);
  if (categoryText != null && categoryId == null) {
    errors.push(invalidInput('categoryId', '형식이 올바르지 않습니다.'));
  }
  const cursorText = searchParams.get('cursor');
  const cursor = cursorText == null ? null : decodeCursor(cursorText);
  if (cursorText != null && cursor == null) {
    errors.push(invalidInput('cursor', '형식이 올바르지 않습니다.'));
  }
  return errors.length > 0 ? { errors } : { errors, query: { regionId, categoryId, cursor } };
}

/**
 * CursorResponse. 지정 동네의 공개 물품을 최신순으로, 커서 뒤부터 size건. 같은 bumpedAt이 여럿이어도
 * (bumpedAt, id) 순서로 끊으므로 경계에서 빠지거나 겹치지 않는다.
 */
export function listFeed(items, { regionId, categoryId, cursor }, size = FEED_PAGE_SIZE) {
  const sorted = items
    .filter(
      (item) =>
        isVisible(item) &&
        item.regionId === regionId &&
        (categoryId == null || item.categoryId === categoryId),
    )
    .sort(compareLatest);
  const rest = cursor
    ? sorted.filter(
        (item) =>
          item.bumpedAt < cursor.bumpedAt ||
          (item.bumpedAt === cursor.bumpedAt && item.itemId < cursor.itemId),
      )
    : sorted;
  const page = rest.slice(0, size);
  const hasNext = rest.length > size;
  return {
    items: page,
    hasNext,
    nextCursor: hasNext ? encodeCursor(page[page.length - 1]) : null,
  };
}

// ── 검색 ─────────────────────────────────────────────────────────────────────

function readDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value ?? '')) {
    return null;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value) ? value : null;
}

function readPrice(searchParams, name, errors) {
  const text = searchParams.get(name);
  if (text == null) {
    return null;
  }
  const price = readLong(text);
  if (price == null) {
    errors.push(invalidInput(name, '0 이상의 정수여야 합니다.'));
  }
  return price;
}

/**
 * 검색 쿼리 검증(openapi GET /api/items 설명): regionId 필수(목), 시작일·종료일은 함께, 시작일 > 종료일 400(details endDate),
 * minPrice > maxPrice 400(details minPrice). keyword 길이는 제한하지 않는다.
 * @returns {{ errors: {field, reason}[], query?: object }}
 */
export function parseSearchQuery(searchParams) {
  const errors = [];
  const regionId = readLong(searchParams.get('regionId'));
  if (regionId == null) {
    errors.push(invalidInput('regionId', '동네를 지정해야 합니다.'));
  }
  const categoryText = searchParams.get('categoryId');
  const categoryId = categoryText == null ? null : readLong(categoryText);
  if (categoryText != null && categoryId == null) {
    errors.push(invalidInput('categoryId', '형식이 올바르지 않습니다.'));
  }
  const minPrice = readPrice(searchParams, 'minPrice', errors);
  const maxPrice = readPrice(searchParams, 'maxPrice', errors);
  if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
    errors.push(invalidInput('minPrice', '최소 가격이 최대 가격보다 클 수 없습니다.'));
  }

  const startText = searchParams.get('startDate');
  const endText = searchParams.get('endDate');
  const startDate = startText == null ? null : readDate(startText);
  const endDate = endText == null ? null : readDate(endText);
  if ((startText == null) !== (endText == null)) {
    errors.push(invalidInput('endDate', '시작일과 종료일은 함께 보내야 합니다.'));
  } else if (startText != null && (startDate == null || endDate == null)) {
    errors.push(invalidInput('startDate', '날짜 형식이 올바르지 않습니다.'));
  } else if (startDate != null && compareDate(startDate, endDate) > 0) {
    errors.push(invalidInput('endDate', '종료일은 시작일 이후여야 합니다.'));
  }

  const sort = searchParams.get('sort') ?? 'ACCURACY';
  if (!SORTS.includes(sort)) {
    errors.push(invalidInput('sort', '지원하지 않는 정렬입니다.'));
  }
  const availableText = searchParams.get('availableOnly');
  if (availableText != null && availableText !== 'true' && availableText !== 'false') {
    errors.push(invalidInput('availableOnly', '형식이 올바르지 않습니다.'));
  }
  const page = readPage(searchParams.get('page'));
  if (page == null) {
    errors.push(invalidInput('page', '0 이상의 정수여야 합니다.'));
  }

  if (errors.length > 0) {
    return { errors };
  }
  return {
    errors,
    query: {
      regionId,
      keyword: (searchParams.get('keyword') ?? '').trim().toLowerCase(),
      categoryId,
      minPrice,
      maxPrice,
      startDate,
      endDate,
      availableOnly: availableText === 'true',
      sort,
      page,
    },
  };
}

/** [start, end](양 끝 포함) 구간이 하나의 가능 기간에 모두 들어가는지 */
function isInsideAvailablePeriod(item, start, end) {
  return item.availablePeriods.some(
    (period) => compareDate(period.startDate, start) <= 0 && compareDate(end, period.endDate) <= 0,
  );
}

/** 점유 중인 대여(기간 겹침은 시작 ≤ 끝, 끝 ≥ 시작 — 경계 날짜도 겹침) */
function overlapsOccupying(occupying, itemId, start, end) {
  return occupying.some(
    (rental) =>
      rental.itemId === itemId &&
      compareDate(rental.startDate, end) <= 0 &&
      compareDate(rental.endDate, start) >= 0,
  );
}

/**
 * 대여 가능 판정(openapi availableOnly). 기간을 보냈으면 그 기간 전체가 가능 기간 하나에 들어가고 점유 대여와 겹치지 않아야 하고,
 * 없으면 오늘 이후 가능 기간이 남아 있고 오늘이 예약되지 않아야 한다.
 */
function isRentable(item, occupying, { startDate, endDate }, today) {
  if (startDate != null) {
    return (
      isInsideAvailablePeriod(item, startDate, endDate) &&
      !overlapsOccupying(occupying, item.itemId, startDate, endDate)
    );
  }
  return (
    item.availablePeriods.some((period) => compareDate(period.endDate, today) >= 0) &&
    isInsideAvailablePeriod(item, today, today) &&
    !overlapsOccupying(occupying, item.itemId, today, today)
  );
}

/** 정확도: 이름 일치(0) > 설명 일치(1) > 그 외(2), 같으면 최신순(item.md REQ-010). 키워드가 없으면 모두 같아 최신순 */
function accuracyRank(item, keyword) {
  if (keyword === '') {
    return 0;
  }
  if (item.name.toLowerCase().includes(keyword)) {
    return 0;
  }
  return item.description.toLowerCase().includes(keyword) ? 1 : 2;
}

/**
 * 검색 조건에 맞는 물품(정렬까지). 키워드는 이름·설명에 부분 일치(대소문자 무시).
 * 기간을 보냈으면(availableOnly와 무관하게) 그 기간에 빌릴 수 있는 물품만, availableOnly만이면 오늘 기준.
 */
export function searchMatches(items, occupying, query, today) {
  const hasPeriod = query.startDate != null;
  const matched = items.filter(
    (item) =>
      isVisible(item) &&
      item.regionId === query.regionId &&
      (query.categoryId == null || item.categoryId === query.categoryId) &&
      (query.minPrice == null || item.pricePerDay >= query.minPrice) &&
      (query.maxPrice == null || item.pricePerDay <= query.maxPrice) &&
      (query.keyword === '' ||
        item.name.toLowerCase().includes(query.keyword) ||
        item.description.toLowerCase().includes(query.keyword)) &&
      (!(hasPeriod || query.availableOnly) || isRentable(item, occupying, query, today)),
  );
  switch (query.sort) {
    case 'LATEST':
      return matched.sort(compareLatest);
    case 'PRICE_ASC':
      return matched.sort((a, b) => a.pricePerDay - b.pricePerDay || b.itemId - a.itemId);
    default:
      return matched.sort(
        (a, b) =>
          accuracyRank(a, query.keyword) - accuracyRank(b, query.keyword) || compareLatest(a, b),
      );
  }
}

/** PageResponse. 페이지 크기는 고정(search.page) */
export function searchPage(items, occupying, query, today) {
  return toPageResponse(searchMatches(items, occupying, query, today), query.page);
}
