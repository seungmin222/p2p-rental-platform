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

/** REQ-020 내 물품 관리 탭(키 = API tab 값). state-machines.md "UI 탭 매핑". HIDDEN 탭은 숨기기(REQ-044, 3순위)와 함께 */
export const MY_ITEM_TABS = {
  AVAILABLE: { label: '대여 가능' },
  RENTING: { label: '대여 중' },
};
