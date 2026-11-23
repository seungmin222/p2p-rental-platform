// 관심(REQ-022)·최근 본 상품(REQ-035) 목의 순수 함수. 저장소(wishStore.js)의 배열을 받아 새 배열을 돌려주고 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값을 여기 둔다.

// policies.md wish.page(20, 요청의 size는 받지 않는다)·recent-items.max(회원당 20건)
export const WISH_PAGE = 20;
export const RECENT_ITEMS_MAX = 20;

/**
 * 물품 상태(WishItemResponse.status). item은 item.js findMockItem 결과(없으면 null).
 * 숨김(REQ-044)은 물품 목에 없어 관심 행의 표시용 플래그(hiddenMock)로 흉내 낸다.
 */
export function wishItemStatus(item, wish) {
  if (!item || item.isDeleted) {
    return 'DELETED';
  }
  return wish?.hiddenMock ? 'HIDDEN' : 'AVAILABLE';
}

/** 관심 추가 검사(상세와 같은 공개 규칙): 없음·삭제 404 → 본인 물품 400. 통과하면 null */
export function checkAddWish(item, memberId) {
  if (!item || item.isDeleted) {
    return 'ITEM_NOT_FOUND';
  }
  return item.ownerId === memberId ? 'WISH_OWN_ITEM' : null;
}

/**
 * 관심 추가(wished true)·해제. 멱등: 이미 그 상태면 그대로.
 * 결과 { wishes, changed } — changed면 물품 관심 수를 ±1 한다(호출부).
 */
export function setWish(wishes, { memberId, itemId, wished, wishedAt }) {
  const exists = wishes.some((wish) => wish.memberId === memberId && wish.itemId === itemId);
  if (exists === wished) {
    return { wishes, changed: false };
  }
  if (wished) {
    return { wishes: [...wishes, { memberId, itemId, wishedAt }], changed: true };
  }
  return {
    wishes: wishes.filter((wish) => !(wish.memberId === memberId && wish.itemId === itemId)),
    changed: true,
  };
}

/** 관심 수 ±1. 0 아래로 내려가지 않는다 */
export function nextWishCount(count, wished) {
  return wished ? count + 1 : Math.max(0, count - 1);
}

/** 내 관심(최근 추가순, 같은 시각이면 itemId 큰 순) */
export function listMyWishes(wishes, memberId) {
  return wishes
    .filter((wish) => wish.memberId === memberId)
    .sort((a, b) => b.wishedAt.localeCompare(a.wishedAt) || b.itemId - a.itemId);
}

/**
 * WishItemResponse. summary는 피드 카드(feed.js toSummary)이고 삭제·숨김이면 쓰지 않는다
 * (itemId·status·wishedAt 외에는 null·0·false).
 */
export function toWishItemResponse(wish, status, summary) {
  if (status !== 'AVAILABLE') {
    return {
      itemId: wish.itemId,
      name: null,
      thumbnailUrl: null,
      regionName: null,
      pricePerDay: 0,
      deposit: 0,
      isRenting: false,
      wishCount: 0,
      chatCount: 0,
      status,
      wishedAt: wish.wishedAt,
    };
  }
  return {
    itemId: wish.itemId,
    name: summary.name,
    thumbnailUrl: summary.thumbnailUrl,
    regionName: summary.regionName,
    pricePerDay: summary.pricePerDay,
    deposit: summary.deposit,
    isRenting: summary.isRenting,
    wishCount: summary.wishCount,
    chatCount: summary.chatCount,
    status,
    wishedAt: wish.wishedAt,
  };
}

/** 상세 조회 기록(upsert, viewedAt 갱신). 회원당 최신 max건만 남긴다 */
export function recordView(views, { memberId, itemId, viewedAt }, max = RECENT_ITEMS_MAX) {
  const others = views.filter((view) => !(view.memberId === memberId && view.itemId === itemId));
  const mine = [
    ...others.filter((view) => view.memberId === memberId),
    { memberId, itemId, viewedAt },
  ]
    .sort((a, b) => b.viewedAt.localeCompare(a.viewedAt))
    .slice(0, max);
  return [...others.filter((view) => view.memberId !== memberId), ...mine];
}

/** 최근 본 물품 id(최근 본 순). 삭제·숨김을 빼는 것은 호출부 */
export function listRecentItemIds(views, memberId) {
  return views
    .filter((view) => view.memberId === memberId)
    .sort((a, b) => b.viewedAt.localeCompare(a.viewedAt))
    .map((view) => view.itemId);
}
