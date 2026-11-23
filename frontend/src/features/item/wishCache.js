// 관심(REQ-022) 토글 응답으로 캐시를 고치는 순수 함수.
// 카드 목록(피드·검색·최근 본·관심 목록·프로필 등록 물품)은 다시 받지 않고 그 물품의 관심 수만 응답 값으로 바꾼다 —
// 다시 받으면 상세에서 뒤로 왔을 때 피드가 처음 페이지부터 다시 그려져 스크롤이 튄다.

/** 카드 배열에서 itemId의 wishCount만 바꾼다. 바뀐 게 없으면 같은 참조 */
function patchItems(items, itemId, wishCount) {
  let changed = false;
  const next = items.map((item) => {
    if (item?.itemId !== itemId || item.wishCount === wishCount) {
      return item;
    }
    changed = true;
    return { ...item, wishCount };
  });
  return changed ? next : items;
}

/**
 * 카드 목록 캐시 데이터 하나를 고친다. 모양은 useInfiniteQuery({ pages: [{ items }] }) 또는 배열(최근 본 상품).
 * 모르는 모양·바뀐 게 없으면 그대로 돌려준다(setQueriesData가 알림을 보내지 않게 같은 참조).
 */
export function patchCardWishCount(data, itemId, wishCount) {
  if (Array.isArray(data)) {
    return patchItems(data, itemId, wishCount);
  }
  if (!Array.isArray(data?.pages)) {
    return data;
  }
  let changed = false;
  const pages = data.pages.map((page) => {
    if (!Array.isArray(page?.items)) {
      return page;
    }
    const items = patchItems(page.items, itemId, wishCount);
    if (items === page.items) {
      return page;
    }
    changed = true;
    return { ...page, items };
  });
  return changed ? { ...data, pages } : data;
}

/** 상세 응답(ItemDetailResponse)에 관심 상태를 넣는다. wishCount를 모르면(낙관적 업데이트) isWished만 */
export function patchDetailWish(data, { isWished, wishCount }) {
  if (!data) {
    return data;
  }
  return wishCount === undefined ? { ...data, isWished } : { ...data, isWished, wishCount };
}

/**
 * 관심 수를 고칠 카드 목록 캐시인지. 피드·검색 ['items','feed'|'search', …], 최근 본 ['recent-items','me'],
 * 관심 목록 ['wishes','me'], 프로필 등록 물품 ['members', memberId, 'items'].
 */
export function isCardListKey(queryKey) {
  const [root, second, third] = queryKey;
  if (root === 'items') {
    return second === 'feed' || second === 'search';
  }
  if (root === 'members') {
    return second !== 'me' && third === 'items';
  }
  return root === 'recent-items' || root === 'wishes';
}
