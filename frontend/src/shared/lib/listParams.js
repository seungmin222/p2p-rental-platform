// 목록 화면의 페이지·탭 처리 순수 함수. page/hasNext 방식(PageResponse: { items, page, size, totalElements, hasNext })용.

/** useInfiniteQuery getNextPageParam: 다음 페이지가 있으면 page + 1, 없으면 undefined(더 부르지 않음) */
export function getNextPageParam(lastPage) {
  return lastPage?.hasNext ? lastPage.page + 1 : undefined;
}

/**
 * useInfiniteQuery 데이터({ pages })의 items를 순서대로 이어 붙인다. 아직 없으면 undefined.
 * mapItem이 있으면 항목마다 바꾼다(미확정 응답을 읽는 함수를 끼울 때).
 */
export function flattenPages(data, mapItem) {
  if (!data) {
    return undefined;
  }
  const items = data.pages.flatMap((page) => page.items ?? []);
  return mapItem ? items.map((item) => mapItem(item)) : items;
}

/** URL ?tab= 값이 tabs의 키면 그대로, 없거나 모르는 값이면 fallback */
export function normalizeTab(value, tabs, fallback) {
  return value != null && Object.hasOwn(tabs, value) ? value : fallback;
}
