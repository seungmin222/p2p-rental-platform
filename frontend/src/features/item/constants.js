// Enum 코드 → 라벨 매핑(docs/state-machines.md §5)

export const ITEM_CONDITION = {
  LIKE_NEW: { label: '새 상품급' },
  LIGHTLY_USED: { label: '사용감 적음' },
  USED: { label: '사용감 있음' },
};

// policies.md region.range (기본 NORMAL)
export const REGION_RANGE = {
  NEAR: { label: '1.5km' },
  NORMAL: { label: '3km' },
  FAR: { label: '6km' },
};

// policies.md item.images (1~10장, jpg/png, 장당 10MB)
export const ITEM_IMAGES = {
  MIN_COUNT: 1,
  MAX_COUNT: 10,
  MAX_FILE_SIZE: 10 * 1024 * 1024,
  ACCEPT: ['image/jpeg', 'image/png'],
};
// 안내 문구용 장당 용량(MB)
export const ITEM_IMAGE_MAX_MB = ITEM_IMAGES.MAX_FILE_SIZE / (1024 * 1024);

/** REQ-020 내 물품 관리 탭(키 = API tab 값). state-machines.md "UI 탭 매핑". HIDDEN 탭은 숨기기(REQ-044, 3순위)와 함께 */
export const MY_ITEM_TABS = {
  AVAILABLE: { label: '대여 가능' },
  RENTING: { label: '대여 중' },
};

// policies.md item.price-per-day 상한(일 대여료 0원 또는 1,000원 ~ 1,000,000원). 검색 가격 필터 입력의 한도
export const ITEM_PRICE_PER_DAY = { MAX: 1_000_000 };
// policies.md item.deposit(0원 또는 1,000원 ~ 3,000,000원)
export const ITEM_DEPOSIT = { MAX: 3_000_000 };
// policies.md item.price-per-day·item.deposit의 0원이 아닐 때 하한
export const ITEM_MIN_PAID_AMOUNT = 1000;
// policies.md item.item-price.max(0 ~ 10,000,000원, 비공개)
export const ITEM_PRICE = { MAX: 10_000_000 };
// policies.md item.name.max·description.max. ADDRESS는 openapi ItemCreateRequest.address maxLength
export const ITEM_TEXT_MAX = { NAME: 50, DESCRIPTION: 2000, ADDRESS: 200 };
// 권장 보증금 조회: 물품 가격을 치는 동안 요청하지 않게 기다리는 시간(UI 조절값)
export const DEPOSIT_GUIDE_DEBOUNCE_MS = 400;

/** REQ-010 정렬(openapi GET /api/items sort). 기본 ACCURACY */
export const ITEM_SORT = {
  ACCURACY: { label: '정확도순' },
  LATEST: { label: '최신순' },
  PRICE_ASC: { label: '낮은 가격순' },
};
export const DEFAULT_ITEM_SORT = 'ACCURACY';

// 카테고리는 거의 바뀌지 않는다(관리자 변경은 3순위)
export const CATEGORY_STALE_TIME = 10 * 60 * 1000;
// 상세(isOwner·가격)는 목록보다 짧게(frontend/CLAUDE.md 기본 옵션)
export const ITEM_DETAIL_STALE_TIME = 10 * 1000;
