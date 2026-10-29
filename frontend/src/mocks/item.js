// 물품 목. 대여 목(rental.js)도 이 카탈로그를 쓴다. 나 = 회원 1(MOCK_ME).
//
// | itemId | 물품 | 제공자 | 쓰임 |
// | --- | --- | --- | --- |
// | 101 | 4인용 거실형 텐트 15,000원/일 · 보증금 50,000원 | 캠핑조아(2) | 예약 화면 기본. 가능 기간 오늘~+40일, +45~+90일(사이 UNAVAILABLE), 예약됨이 섞임 |
// | 102 | 전동 드릴 세트 5,000원/일 · 보증금 없음 | 캠핑조아(2) | 예약하면 항상 409 RENTAL_PERIOD_CONFLICT |
// | 103 | 캠핑 의자 2개 | 나(1) | 본인 물품(예약 대신 안내), 제공자 시드 |
// | 104 | 빔 프로젝터 | 나(1) | 제공자 시드 |
// | 105 | 캠핑 랜턴 | 캠핑조아(2) | 대여자 시드 |
// | 106 | 접이식 자전거 | 이웃(4) | 삭제된 물품(isDeleted) 표시용 |
// | 107 | 보드게임 세트 | 나(1) | 내 물품 대여 가능 탭, 예약 현황 23건(시트 안 더 불러오기), 다가오는 예약 1건 |
// | 108 | 아이스박스 45L | 나(1) | 내 물품 대여 가능 탭, 이미지 없음·대여 없음·카운터 0 |
//
// 물품 상세(GET /api/items/{itemId})는 openapi.json ItemDetailResponse 필드를 전부 돌려준다. 물품 동네·거래 장소는
// 제공자 동네(MOCK_REGIONS)를 따르고, 카테고리 id·이름은 백엔드 V3__insert_categories.sql 순서와 같다.
// 조회·관심·채팅 수(counts)는 물품 상세와 내 물품 관리 목(rental.js)이 함께 쓴다.
import { http } from 'msw';

import { addDays, todayInSeoul } from '../shared/lib/date';
import { readPathId } from './pageList';
import { fail, ok } from './response';

/** 로그인한 나 */
export const MOCK_ME = 1;

/** V3__insert_categories.sql 순서(categoryId = 넣은 순서) 중 시드가 쓰는 것 */
const MOCK_CATEGORIES = {
  1: '캠핑·아웃도어',
  2: '공구·DIY',
  3: '촬영·영상 장비',
  6: '취미·시즌 용품',
};

/** 동네 → 물품 거래 장소(그 동네의 대략적인 위치) */
const MOCK_REGIONS = {
  송도동: { address: '인천 연수구 송도동 센트럴로 160', latitude: 37.3925, longitude: 126.6392 },
  연수동: { address: '인천 연수구 연수동 청능대로 105', latitude: 37.4179, longitude: 126.6787 },
  동춘동: { address: '인천 연수구 동춘동 앵고개로 233', latitude: 37.4048, longitude: 126.6704 },
};

export const MOCK_MEMBERS = {
  1: { nickname: '동네주민', regionName: '송도동' },
  2: { nickname: '캠핑조아', regionName: '연수동' },
  3: { nickname: '텐트초보', regionName: '송도동' },
  4: { nickname: '이웃', regionName: '동춘동' },
};

/** 회색 면에 글자를 얹은 자리 이미지(외부 이미지 없이) */
export function placeholderImage(label, color = '#EDEBE5') {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="${color}"/><text x="200" y="210" font-size="36" text-anchor="middle" fill="#5C5F5A" font-family="sans-serif">${label}</text></svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

// availablePeriods는 오늘 기준 [시작 오프셋, 끝 오프셋] 일수(양 끝 포함). bumpedDaysAgo는 등록(끌어올리기) 며칠 전
const ITEMS = [
  {
    itemId: 101,
    name: '4인용 거실형 텐트',
    ownerId: 2,
    pricePerDay: 15000,
    deposit: 50000,
    availablePeriods: [
      [0, 40],
      [45, 90],
    ],
    color: '#E8F1EC',
    categoryId: 1,
    condition: 'LIGHTLY_USED',
    description: '4인 가족이 쓰기 좋은 거실형 텐트예요. 펙·스트링·그라운드시트 포함.',
    bumpedDaysAgo: 3,
    counts: { viewCount: 312, wishCount: 28, chatCount: 11 },
  },
  {
    itemId: 102,
    name: '전동 드릴 세트',
    ownerId: 2,
    pricePerDay: 5000,
    deposit: 0,
    availablePeriods: [[0, 60]],
    color: '#E6EDF8',
    categoryId: 2,
    condition: 'USED',
    description: '충전식 전동 드릴과 비트 20종 세트예요. 배터리 2개.',
    bumpedDaysAgo: 6,
    counts: { viewCount: 87, wishCount: 6, chatCount: 2 },
  },
  {
    itemId: 103,
    name: '캠핑 의자 2개',
    ownerId: 1,
    pricePerDay: 3000,
    deposit: 10000,
    availablePeriods: [[0, 60]],
    color: '#FBF1E2',
    counts: { viewCount: 42, wishCount: 5, chatCount: 3 },
    categoryId: 1,
    condition: 'LIKE_NEW',
    description: '접이식 캠핑 의자 2개예요. 컵홀더 있어요.',
    bumpedDaysAgo: 12,
  },
  {
    itemId: 104,
    name: '빔 프로젝터',
    ownerId: 1,
    pricePerDay: 20000,
    deposit: 100000,
    availablePeriods: [[0, 60]],
    color: '#F6F5F1',
    counts: { viewCount: 128, wishCount: 17, chatCount: 9 },
    categoryId: 3,
    condition: 'LIGHTLY_USED',
    description: 'FHD 빔 프로젝터예요. 100인치 스크린과 HDMI 케이블 함께 드려요.',
    bumpedDaysAgo: 20,
  },
  {
    itemId: 105,
    name: '캠핑 랜턴',
    ownerId: 2,
    pricePerDay: 4000,
    deposit: 20000,
    availablePeriods: [[0, 60]],
    color: '#FBEAE5',
    categoryId: 1,
    condition: 'USED',
    description: '충전식 LED 랜턴이에요. 밝기 3단계.',
    bumpedDaysAgo: 9,
    counts: { viewCount: 54, wishCount: 4, chatCount: 1 },
  },
  {
    itemId: 106,
    name: '접이식 자전거',
    ownerId: 4,
    pricePerDay: 12000,
    deposit: 80000,
    availablePeriods: [[0, 60]],
    isDeleted: true,
    color: '#EDEBE5',
    categoryId: 6,
    condition: 'USED',
    description: '20인치 접이식 자전거예요.',
    bumpedDaysAgo: 30,
  },
  {
    itemId: 107,
    name: '보드게임 세트',
    ownerId: 1,
    pricePerDay: 2000,
    deposit: 0,
    availablePeriods: [[0, 60]],
    color: '#E6EDF8',
    counts: { viewCount: 230, wishCount: 21, chatCount: 14 },
    categoryId: 6,
    condition: 'LIGHTLY_USED',
    description: '인기 보드게임 5종 세트예요. 구성품 다 있어요.',
    bumpedDaysAgo: 40,
  },
  {
    itemId: 108,
    name: '아이스박스 45L',
    ownerId: 1,
    pricePerDay: 5000,
    deposit: 20000,
    availablePeriods: [[0, 60]],
    hasNoImage: true,
    categoryId: 1,
    condition: 'LIKE_NEW',
    description: '45L 아이스박스예요. 바퀴 달려 있어요.',
    bumpedDaysAgo: 1,
  },
];

/** 예약하면 항상 기간 충돌인 물품 */
export const CONFLICT_ITEM_ID = 102;

export function findMockItem(itemId) {
  const item = ITEMS.find((candidate) => candidate.itemId === Number(itemId));
  if (!item) {
    return null;
  }
  const today = todayInSeoul();
  const regionName = MOCK_MEMBERS[item.ownerId].regionName;
  return {
    ...item,
    isDeleted: item.isDeleted ?? false,
    thumbnailUrl: item.hasNoImage ? null : placeholderImage(item.name, item.color),
    regionName,
    ...MOCK_REGIONS[regionName],
    bumpedAt: `${addDays(today, -item.bumpedDaysAgo)}T10:00:00`,
    counts: item.counts ?? { viewCount: 0, wishCount: 0, chatCount: 0 },
    availablePeriods: item.availablePeriods.map(([from, to]) => ({
      startDate: addDays(today, from),
      endDate: addDays(today, to),
    })),
  };
}

/** 삭제되지 않은 내 물품 */
export function listMyMockItems() {
  return ITEMS.filter((item) => item.ownerId === MOCK_ME && !item.isDeleted).map((item) =>
    findMockItem(item.itemId),
  );
}

export const itemHandlers = [
  // openapi.json ItemDetailResponse 그대로. 검사 순서: itemId 형식 400 → 404. 숨김 물품은 시드에 없어
  // (숨기기 REQ-044 전) 없는·삭제된 물품만 404. 조회 수는 올리지 않는다(새로고침마다 바뀌면 화면 확인이 어려워서).
  http.get('/api/items/:itemId', ({ params }) => {
    const itemId = readPathId(params.itemId);
    if (itemId == null) {
      return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', [
        { field: 'itemId', reason: '형식이 올바르지 않습니다.' },
      ]);
    }
    const item = findMockItem(itemId);
    if (!item || item.isDeleted) {
      return fail(404, 'ITEM_NOT_FOUND', '물품을 찾을 수 없습니다.');
    }
    const owner = MOCK_MEMBERS[item.ownerId];
    return ok({
      itemId: item.itemId,
      name: item.name,
      // 자리 이미지 하나를 원본·썸네일에 함께 쓴다
      images: item.thumbnailUrl
        ? [{ imageId: item.itemId * 10, url: item.thumbnailUrl, thumbnailUrl: item.thumbnailUrl }]
        : [],
      category: { categoryId: item.categoryId, name: MOCK_CATEGORIES[item.categoryId] },
      condition: item.condition,
      description: item.description,
      pricePerDay: item.pricePerDay,
      deposit: item.deposit,
      address: item.address,
      latitude: item.latitude,
      longitude: item.longitude,
      regionName: item.regionName,
      status: 'AVAILABLE',
      bumpedAt: item.bumpedAt,
      viewCount: item.counts.viewCount,
      wishCount: item.counts.wishCount,
      chatCount: item.counts.chatCount,
      isWished: false,
      owner: {
        memberId: item.ownerId,
        nickname: owner.nickname,
        profileImageUrl: null,
        regionName: owner.regionName,
      },
      isOwner: item.ownerId === MOCK_ME,
    });
  }),
];
