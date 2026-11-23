// 관심·최근 본 상품 목의 메모리 저장소(새로고침하면 시드로 돌아간다). 물품 상세 목(item.js)과 관심 API 목(wish.js)이
// 같이 쓴다. item.js를 가져오지 않아 순환 import가 없다.
//
// | 회원 | 관심 | 확인할 것 |
// | user1 | 22건(2페이지) | 101·102·105, 106(삭제된 물품), 210(숨김 흉내 → "볼 수 없는 물품"), 201~218 |
// | user1 | 최근 본 6건 | 101·105·201·202·203 + 106(삭제라 목록에서 빠짐) → 5건 |
// 상세를 보면(로그인, 남의 물품) 최근 본에 기록되고, 하트를 누르면 관심이 바뀐다.
const HOUR_MS = 60 * 60 * 1000;
const SEOUL_OFFSET_MS = 9 * HOUR_MS;

/** 지금 서버 시각 문자열(오프셋 없는 서울 시각, rentalSeed.js toServerDateTime과 같은 모양) */
export function nowServerDateTime(now = Date.now()) {
  return new Date(now + SEOUL_OFFSET_MS).toISOString().slice(0, 19);
}

/** n시간 전(새로 담거나 본 것이 시드보다 앞에 오게 지금 기준) */
function hoursAgo(hours) {
  return nowServerDateTime(Date.now() - (hours + 1) * HOUR_MS);
}

const WISH_ITEM_IDS = [
  101, 201, 102, 202, 106, 203, 204, 105, 205, 206, 210, 207, 208, 209, 211, 212, 213, 214, 215,
  216, 217, 218,
];
// 숨김(HIDDEN)은 물품 목에 없어 이 관심 행만 숨김으로 보여 준다
const HIDDEN_MOCK_ITEM_ID = 210;

const RECENT_ITEM_IDS = [101, 201, 106, 105, 202, 203];

export const wishDb = {
  wishes: WISH_ITEM_IDS.map((itemId, index) => ({
    memberId: 1,
    itemId,
    wishedAt: hoursAgo(index),
    ...(itemId === HIDDEN_MOCK_ITEM_ID ? { hiddenMock: true } : {}),
  })),
  views: RECENT_ITEM_IDS.map((itemId, index) => ({
    memberId: 1,
    itemId,
    viewedAt: hoursAgo(index),
  })),
};
