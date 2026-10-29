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
import { http } from 'msw';

import { addDays, todayInSeoul } from '../shared/lib/date';
import { fail, ok } from './response';

/** 로그인한 나 */
export const MOCK_ME = 1;

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

// availablePeriods는 오늘 기준 [시작 오프셋, 끝 오프셋] 일수(양 끝 포함)
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
  },
  {
    itemId: 102,
    name: '전동 드릴 세트',
    ownerId: 2,
    pricePerDay: 5000,
    deposit: 0,
    availablePeriods: [[0, 60]],
    color: '#E6EDF8',
  },
  {
    itemId: 103,
    name: '캠핑 의자 2개',
    ownerId: 1,
    pricePerDay: 3000,
    deposit: 10000,
    availablePeriods: [[0, 60]],
    color: '#FBF1E2',
  },
  {
    itemId: 104,
    name: '빔 프로젝터',
    ownerId: 1,
    pricePerDay: 20000,
    deposit: 100000,
    availablePeriods: [[0, 60]],
    color: '#F6F5F1',
  },
  {
    itemId: 105,
    name: '캠핑 랜턴',
    ownerId: 2,
    pricePerDay: 4000,
    deposit: 20000,
    availablePeriods: [[0, 60]],
    color: '#FBEAE5',
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
  return {
    ...item,
    isDeleted: item.isDeleted ?? false,
    thumbnailUrl: placeholderImage(item.name, item.color),
    availablePeriods: item.availablePeriods.map(([from, to]) => ({
      startDate: addDays(today, from),
      endDate: addDays(today, to),
    })),
  };
}

export const itemHandlers = [
  // TODO: 백엔드 확정 전 임시 — REQ-011 물품 상세는 openapi.json에 아직 없다. 예약 화면 상단·본인 물품 여부에
  // 필요한 필드만 item.md REQ-011 목록에서 골라 이름을 가정했다(features/item/api.js toItemSummary).
  http.get('/api/items/:itemId', ({ params }) => {
    const item = findMockItem(params.itemId);
    if (!item || item.isDeleted) {
      return fail(404, 'ITEM_NOT_FOUND', '물품을 찾을 수 없습니다.');
    }
    const owner = MOCK_MEMBERS[item.ownerId];
    return ok({
      itemId: item.itemId,
      name: item.name,
      images: [{ imageId: item.itemId * 10, url: item.thumbnailUrl }],
      pricePerDay: item.pricePerDay,
      deposit: item.deposit,
      owner: { memberId: item.ownerId, nickname: owner.nickname, regionName: owner.regionName },
      isOwner: item.ownerId === MOCK_ME,
    });
  }),
];
