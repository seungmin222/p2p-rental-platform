// 관심(REQ-022)·최근 본 상품(REQ-035) API 목. 요청·응답은 docs/openapi.json 그대로, 계산은 wishLogic.js, 저장은 wishStore.js
// (시드 표도 그 파일 맨 위). 관심 수는 물품 목(item.js itemDb)의 counts.wishCount를 고쳐 피드·상세·내 물품이 같은 값을 본다.
// 상세 조회의 isWished·최근 본 기록은 물품 상세 목(item.js)이 한다.
import { delay, http } from 'msw';

import { toSummary } from './feed';
import { findMockItem, itemDb } from './item';
import { readPage, readPathId, toPageResponse } from './pageList';
import { fail, ok } from './response';
import { authenticateMockRequest } from './session';
import {
  checkAddWish,
  listMyWishes,
  listRecentItemIds,
  nextWishCount,
  setWish,
  toWishItemResponse,
  WISH_PAGE,
  wishItemStatus,
} from './wishLogic';
import { nowServerDateTime, wishDb } from './wishStore';

const MESSAGES = {
  COMMON_INVALID_INPUT: '입력값이 올바르지 않습니다.',
  AUTH_UNAUTHORIZED: '로그인이 필요합니다.',
  ITEM_NOT_FOUND: '물품을 찾을 수 없습니다.',
  WISH_OWN_ITEM: '본인 물품은 관심 목록에 담을 수 없습니다.',
};
const STATUSES = {
  COMMON_INVALID_INPUT: 400,
  AUTH_UNAUTHORIZED: 401,
  ITEM_NOT_FOUND: 404,
  WISH_OWN_ITEM: 400,
};

function failWith(code, details) {
  return fail(STATUSES[code], code, MESSAGES[code], details);
}

/** 저장된 물품(counts를 고칠 원본). 시드에 counts가 없으면 0으로 만들어 둔다 */
function rawCounts(itemId) {
  const raw = itemDb.items.find((item) => item.itemId === itemId);
  raw.counts ??= { viewCount: 0, wishCount: 0, chatCount: 0 };
  return raw.counts;
}

// 시드 관심 수가 담은 회원 수보다 작지 않게 맞춘다(담겨 있는데 관심 0인 물품이 없게)
for (const wish of wishDb.wishes) {
  if (findMockItem(wish.itemId)) {
    const counts = rawCounts(wish.itemId);
    const wishers = wishDb.wishes.filter((other) => other.itemId === wish.itemId).length;
    counts.wishCount = Math.max(counts.wishCount, wishers);
  }
}

/** 검사 순서: 인증 401 → itemId 형식 400 → 물품 404(→ 추가는 본인 물품 400) */
function toggle(wished) {
  return async ({ params, request }) => {
    await delay(300);
    const memberId = authenticateMockRequest(request)?.memberId;
    if (memberId == null) {
      return failWith('AUTH_UNAUTHORIZED');
    }
    const itemId = readPathId(params.itemId);
    if (itemId == null) {
      return failWith('COMMON_INVALID_INPUT', [
        { field: 'itemId', reason: '형식이 올바르지 않습니다.' },
      ]);
    }
    const item = findMockItem(itemId);
    // 해제는 삭제·숨김 물품도 된다(없는 물품만 404)
    const code = wished ? checkAddWish(item, memberId) : item ? null : 'ITEM_NOT_FOUND';
    if (code) {
      return failWith(code);
    }
    const result = setWish(wishDb.wishes, {
      memberId,
      itemId,
      wished,
      wishedAt: nowServerDateTime(),
    });
    wishDb.wishes = result.wishes;
    const counts = rawCounts(itemId);
    if (result.changed) {
      counts.wishCount = nextWishCount(counts.wishCount, wished);
    }
    return ok({ itemId, isWished: wished, wishCount: counts.wishCount });
  };
}

export const wishHandlers = [
  http.post('/api/items/:itemId/wishes', toggle(true)),
  http.delete('/api/items/:itemId/wishes', toggle(false)),

  http.get('/api/members/me/wishes', async ({ request }) => {
    await delay(300);
    const memberId = authenticateMockRequest(request)?.memberId;
    if (memberId == null) {
      return failWith('AUTH_UNAUTHORIZED');
    }
    const page = readPage(new URL(request.url).searchParams.get('page'));
    if (page == null) {
      return failWith('COMMON_INVALID_INPUT', [
        { field: 'page', reason: '형식이 올바르지 않습니다.' },
      ]);
    }
    const all = listMyWishes(wishDb.wishes, memberId).map((wish) => {
      const item = findMockItem(wish.itemId);
      const status = wishItemStatus(item, wish);
      return toWishItemResponse(wish, status, status === 'AVAILABLE' ? toSummary(item) : null);
    });
    return ok(toPageResponse(all, page, WISH_PAGE));
  }),

  http.get('/api/members/me/recent-items', async ({ request }) => {
    await delay(300);
    const memberId = authenticateMockRequest(request)?.memberId;
    if (memberId == null) {
      return failWith('AUTH_UNAUTHORIZED');
    }
    // 삭제(·숨김) 물품은 빼고 준다
    const items = listRecentItemIds(wishDb.views, memberId)
      .map((itemId) => findMockItem(itemId))
      .filter((item) => item && !item.isDeleted)
      .map(toSummary);
    return ok(items);
  }),
];
