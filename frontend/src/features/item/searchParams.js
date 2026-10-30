// 검색 화면(SCR-04)의 조건. 전부 URL 쿼리에 두어 뒤로 가기·새로고침에 유지하고 queryKey에 그대로 넣는다.
// 쿼리 값은 사용자가 손으로 바꿀 수 있어 읽을 때마다 걸러 낸다(틀린 값은 없는 것으로 본다).
// 조건은 docs/openapi.json GET /api/items의 파라미터와 같다(regionId는 동네에서 오므로 여기에 없다).
import { compareDate } from '../../shared/lib/date';
import { DEFAULT_ITEM_SORT, ITEM_PRICE_PER_DAY, ITEM_SORT } from './constants';

export const SEARCH_PARAM = {
  KEYWORD: 'q',
  CATEGORY: 'category',
  SORT: 'sort',
  MIN_PRICE: 'minPrice',
  MAX_PRICE: 'maxPrice',
  START_DATE: 'startDate',
  END_DATE: 'endDate',
  AVAILABLE_ONLY: 'availableOnly',
};

const DIGITS = /^\d+$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

function toInteger(value, max = Number.MAX_SAFE_INTEGER) {
  if (typeof value !== 'string' || !DIGITS.test(value)) {
    return null;
  }
  const number = Number(value);
  return number <= max ? number : null;
}

/** 존재하는 날짜인지('2026-02-30' 같은 값을 거른다) */
function toDate(value) {
  if (typeof value !== 'string' || !DATE.test(value)) {
    return null;
  }
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.getTime()) && date.toISOString().startsWith(value) ? value : null;
}

/** URL ?category= 값. 양의 정수가 아니면 null(홈·검색이 같이 쓴다) */
export function readCategoryParam(searchParams) {
  const categoryId = toInteger(searchParams.get(SEARCH_PARAM.CATEGORY));
  return categoryId != null && categoryId > 0 ? categoryId : null;
}

/**
 * URL의 카테고리를 실제 카테고리와 맞춘다. 없는 값(?category=9999)이면 "전체"(null)로 본다.
 * 목록을 받는 중이면 isResolving(그 값으로 요청하지 않게 호출부가 기다린다), 목록을 못 받았으면 판단할 수 없어 URL 값을 그대로 쓴다.
 */
export function resolveCategory(categoryId, categories, isCategoriesPending) {
  if (categoryId == null) {
    return { categoryId: null, isResolving: false };
  }
  if (isCategoriesPending) {
    return { categoryId, isResolving: true };
  }
  const isKnown = !categories || categories.some((category) => category.categoryId === categoryId);
  return { categoryId: isKnown ? categoryId : null, isResolving: false };
}

/**
 * URLSearchParams → 검색 조건. 키워드는 앞뒤 공백을 지운다.
 * 가격은 0 ~ 일 대여료 상한(policies item.price-per-day)의 정수이고 최소 > 최대면 둘 다 버린다(서버는 400).
 * 기간은 시작·종료가 둘 다 있고 시작 ≤ 종료일 때만 쓴다(서버는 둘 다 보내거나 둘 다 생략).
 */
export function parseSearchParams(searchParams) {
  const keyword = (searchParams.get(SEARCH_PARAM.KEYWORD) ?? '').trim();
  const sortValue = searchParams.get(SEARCH_PARAM.SORT);

  let minPrice = toInteger(searchParams.get(SEARCH_PARAM.MIN_PRICE), ITEM_PRICE_PER_DAY.MAX);
  let maxPrice = toInteger(searchParams.get(SEARCH_PARAM.MAX_PRICE), ITEM_PRICE_PER_DAY.MAX);
  if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
    minPrice = null;
    maxPrice = null;
  }

  let startDate = toDate(searchParams.get(SEARCH_PARAM.START_DATE));
  let endDate = toDate(searchParams.get(SEARCH_PARAM.END_DATE));
  if (startDate == null || endDate == null || compareDate(startDate, endDate) > 0) {
    startDate = null;
    endDate = null;
  }

  return {
    keyword,
    categoryId: readCategoryParam(searchParams),
    sort: Object.hasOwn(ITEM_SORT, sortValue) ? sortValue : DEFAULT_ITEM_SORT,
    minPrice,
    maxPrice,
    startDate,
    endDate,
    availableOnly: searchParams.get(SEARCH_PARAM.AVAILABLE_ONLY) === 'true',
  };
}

/** 조건 → URLSearchParams. 기본값(정렬 ACCURACY, 비어 있는 조건)은 넣지 않아 주소를 짧게 둔다 */
export function toSearchParams(filters) {
  const params = new URLSearchParams();
  if (filters.keyword) {
    params.set(SEARCH_PARAM.KEYWORD, filters.keyword);
  }
  if (filters.categoryId != null) {
    params.set(SEARCH_PARAM.CATEGORY, String(filters.categoryId));
  }
  if (filters.sort !== DEFAULT_ITEM_SORT) {
    params.set(SEARCH_PARAM.SORT, filters.sort);
  }
  if (filters.minPrice != null) {
    params.set(SEARCH_PARAM.MIN_PRICE, String(filters.minPrice));
  }
  if (filters.maxPrice != null) {
    params.set(SEARCH_PARAM.MAX_PRICE, String(filters.maxPrice));
  }
  if (filters.startDate != null && filters.endDate != null) {
    params.set(SEARCH_PARAM.START_DATE, filters.startDate);
    params.set(SEARCH_PARAM.END_DATE, filters.endDate);
  }
  if (filters.availableOnly) {
    params.set(SEARCH_PARAM.AVAILABLE_ONLY, 'true');
  }
  return params;
}

/**
 * 조회할 조건이 있는지. 키워드가 있거나 카테고리·가격·기간·대여 가능만 중 하나라도 골랐을 때만 조회한다
 * (정렬만으로는 조회하지 않는다 — 공백뿐인 키워드는 trim되어 없는 것).
 */
export function hasSearchCondition(filters) {
  return (
    filters.keyword !== '' ||
    filters.categoryId != null ||
    filters.minPrice != null ||
    filters.maxPrice != null ||
    filters.startDate != null ||
    filters.availableOnly
  );
}

/** "필터" 버튼 옆에 보여 줄 적용 개수: 정렬(기본이 아닐 때)·가격·기간·대여 가능만. 카테고리는 칩이라 세지 않는다 */
export function countSheetFilters(filters) {
  return (
    Number(filters.sort !== DEFAULT_ITEM_SORT) +
    Number(filters.minPrice != null || filters.maxPrice != null) +
    Number(filters.startDate != null) +
    Number(filters.availableOnly)
  );
}

/** 조건 → GET /api/items 파라미터(없는 조건은 보내지 않는다). 기본 정렬도 보내지 않는다(서버 기본 ACCURACY) */
export function toApiParams(filters, regionId) {
  const params = { regionId };
  if (filters.keyword) {
    params.keyword = filters.keyword;
  }
  if (filters.categoryId != null) {
    params.categoryId = filters.categoryId;
  }
  if (filters.sort !== DEFAULT_ITEM_SORT) {
    params.sort = filters.sort;
  }
  if (filters.minPrice != null) {
    params.minPrice = filters.minPrice;
  }
  if (filters.maxPrice != null) {
    params.maxPrice = filters.maxPrice;
  }
  if (filters.startDate != null) {
    params.startDate = filters.startDate;
    params.endDate = filters.endDate;
  }
  if (filters.availableOnly) {
    params.availableOnly = true;
  }
  return params;
}

/**
 * 필터 시트의 가격 입력(문자열) 하나를 읽는다. 비우면 조건 없음(null), 숫자(0 ~ 일 대여료 상한)가 아니면 error.
 * 쉼표·공백은 지운다(입력 편의).
 */
export function parsePriceInput(text) {
  const cleaned = text.replace(/[,\s]/g, '');
  if (cleaned === '') {
    return { value: null, error: null };
  }
  const value = toInteger(cleaned, ITEM_PRICE_PER_DAY.MAX);
  if (value == null) {
    return {
      value: null,
      error: `0 ~ ${ITEM_PRICE_PER_DAY.MAX.toLocaleString('ko-KR')}원 사이의 숫자로 입력해 주세요`,
    };
  }
  return { value, error: null };
}

/** 최소·최대 가격 입력 검사. 최소 > 최대면 최대 칸에 오류(서버는 details minPrice 400) */
export function validatePriceRange(minText, maxText) {
  const min = parsePriceInput(minText);
  const max = parsePriceInput(maxText);
  let maxError = max.error;
  if (!min.error && !max.error && min.value != null && max.value != null && min.value > max.value) {
    maxError = '최대 가격은 최소 가격 이상이어야 해요';
  }
  return {
    minPrice: min.value,
    maxPrice: max.value,
    minError: min.error,
    maxError,
    isValid: !min.error && !maxError,
  };
}
