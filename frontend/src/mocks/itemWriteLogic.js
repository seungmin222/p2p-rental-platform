// 물품 등록·수정·삭제·권장 보증금 목의 순수 함수(검증·imageOrder·대여 검사). 저장소는 받지 않아 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값을 여기 따로 둔다(policies.md).
import { addDays } from '../shared/lib/date';
import { invalidInput } from './itemLogic';

export const MOCK_ITEM_POLICY = {
  NAME_MAX: 50,
  DESCRIPTION_MAX: 2000,
  ADDRESS_MAX: 200, // openapi ItemCreateRequest.address
  MIN_PAID_AMOUNT: 1000,
  PRICE_PER_DAY_MAX: 1_000_000,
  DEPOSIT_MAX: 3_000_000,
  ITEM_PRICE_MAX: 10_000_000,
  IMAGE_MIN: 1,
  IMAGE_MAX: 10,
  IMAGE_SIZE_MAX: 10 * 1024 * 1024,
  IMAGE_TYPES: ['image/jpeg', 'image/png'],
};

const CONDITIONS = ['LIKE_NEW', 'LIGHTLY_USED', 'USED'];
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function isBlank(value) {
  return typeof value !== 'string' || value.trim() === '';
}

function checkText(field, value, max) {
  if (isBlank(value)) {
    return invalidInput(field, '필수 값입니다.');
  }
  return [...value].length > max ? invalidInput(field, `${max}자 이하로 입력해 주세요.`) : null;
}

/** 0원 또는 하한~상한 */
function checkAmount(field, value, max) {
  const valid =
    Number.isInteger(value) &&
    (value === 0 || (value >= MOCK_ITEM_POLICY.MIN_PAID_AMOUNT && value <= max));
  return valid ? null : invalidInput(field, '금액 범위를 확인해 주세요.');
}

function checkPeriods(periods, today) {
  if (!Array.isArray(periods) || periods.length === 0) {
    return invalidInput('availablePeriods', '대여 가능 기간을 1개 이상 입력해 주세요.');
  }
  const validDates = periods.every(
    ({ startDate, endDate }) =>
      DATE_PATTERN.test(startDate ?? '') &&
      DATE_PATTERN.test(endDate ?? '') &&
      startDate <= endDate,
  );
  if (!validDates) {
    return invalidInput('availablePeriods', '시작일과 종료일을 확인해 주세요.');
  }
  // 수정은 지난 시작일을 허용한다(openapi ItemUpdateRequest). 등록만 today를 넘긴다
  if (today && periods.some(({ startDate }) => startDate < today)) {
    return invalidInput('availablePeriods', '시작일은 오늘 이후여야 합니다.');
  }
  const sorted = [...periods].sort((a, b) => (a.startDate < b.startDate ? -1 : 1));
  const overlaps = sorted.some((period, i) => i > 0 && period.startDate <= sorted[i - 1].endDate);
  return overlaps ? invalidInput('availablePeriods', '기간끼리 겹칠 수 없습니다.') : null;
}

function checkCoordinate(field, value, limit) {
  return typeof value === 'number' && Math.abs(value) <= limit
    ? null
    : invalidInput(field, '위치를 확인해 주세요.');
}

const FIELD_CHECKS = {
  name: (value) => checkText('name', value, MOCK_ITEM_POLICY.NAME_MAX),
  description: (value) => checkText('description', value, MOCK_ITEM_POLICY.DESCRIPTION_MAX),
  condition: (value) =>
    CONDITIONS.includes(value) ? null : invalidInput('condition', '상태를 선택해 주세요.'),
  pricePerDay: (value) => checkAmount('pricePerDay', value, MOCK_ITEM_POLICY.PRICE_PER_DAY_MAX),
  deposit: (value) => checkAmount('deposit', value, MOCK_ITEM_POLICY.DEPOSIT_MAX),
  itemPrice: (value) =>
    Number.isInteger(value) && value >= 0 && value <= MOCK_ITEM_POLICY.ITEM_PRICE_MAX
      ? null
      : invalidInput('itemPrice', '금액 범위를 확인해 주세요.'),
  address: (value) => checkText('address', value, MOCK_ITEM_POLICY.ADDRESS_MAX),
  latitude: (value) => checkCoordinate('latitude', value, 90),
  longitude: (value) => checkCoordinate('longitude', value, 180),
};

function checkCategory(categoryId, categoryIds) {
  return categoryIds.includes(categoryId)
    ? null
    : invalidInput('categoryId', '사용할 수 없는 카테고리입니다.');
}

/** ItemCreateRequest 검증. 없는 필드도 오류다 */
export function validateItemCreate(body, { categoryIds, today }) {
  const errors = Object.entries(FIELD_CHECKS).map(([field, check]) => check(body?.[field]));
  errors.push(checkCategory(body?.categoryId, categoryIds));
  errors.push(checkPeriods(body?.availablePeriods, today));
  return errors.filter(Boolean);
}

/** ItemUpdateRequest 검증. 보낸 필드만 본다. 주소를 바꾸면 좌표도 함께 와야 한다(openapi address·latitude 설명) */
export function validateItemUpdate(body, { categoryIds }) {
  const errors = Object.entries(FIELD_CHECKS)
    .filter(([field]) => body?.[field] !== undefined)
    .map(([field, check]) => check(body[field]));
  if (body?.categoryId !== undefined) {
    errors.push(checkCategory(body.categoryId, categoryIds));
  }
  if (body?.availablePeriods !== undefined) {
    errors.push(checkPeriods(body.availablePeriods));
  }
  if (
    body?.address !== undefined &&
    (body.latitude === undefined || body.longitude === undefined)
  ) {
    errors.push(invalidInput('latitude', '주소를 바꾸면 위도·경도도 함께 보내야 합니다.'));
  }
  return errors.filter(Boolean);
}

/** 사진 파일 검사. 위반한 오류 코드, 없으면 null */
export function checkImageFiles(files) {
  if (files.some((file) => !MOCK_ITEM_POLICY.IMAGE_TYPES.includes(file.type))) {
    return 'IMAGE_INVALID_TYPE';
  }
  return files.some((file) => file.size > MOCK_ITEM_POLICY.IMAGE_SIZE_MAX)
    ? 'IMAGE_SIZE_EXCEEDED'
    : null;
}

export function isValidImageCount(count) {
  return count >= MOCK_ITEM_POLICY.IMAGE_MIN && count <= MOCK_ITEM_POLICY.IMAGE_MAX;
}

/**
 * imageOrder를 새 이미지 목록으로 푼다(openapi PATCH /api/items/{itemId}).
 * 각 칸은 imageId 또는 newImageIndex 중 하나, 없는 id·없는 순번·가리키지 않는 파일은 오류.
 * @param {object[]} current 지금 이미지
 * @param {{imageId?: number, newImageIndex?: number}[]} order
 * @param {object[]} added images 파트 순서대로의 새 이미지
 * @returns {{ images: object[] } | { error: {field, reason} }}
 */
export function applyImageOrder(current, order, added) {
  const invalid = { error: invalidInput('imageOrder', '이미지 순서가 올바르지 않습니다.') };
  const usedNew = new Set();
  const usedIds = new Set();
  const images = [];
  for (const cell of order) {
    const hasId = cell?.imageId != null;
    const hasNew = cell?.newImageIndex != null;
    if (hasId === hasNew) {
      return invalid;
    }
    const image = hasId
      ? current.find((candidate) => candidate.imageId === cell.imageId)
      : added[cell.newImageIndex];
    const used = hasId ? usedIds : usedNew;
    const key = hasId ? cell.imageId : cell.newImageIndex;
    if (!image || used.has(key)) {
      return invalid;
    }
    used.add(key);
    images.push(image);
  }
  return usedNew.size === added.length ? { images } : invalid;
}

const ACTIVE_FOR_DELETE = {
  PENDING_PAYMENT: '결제 대기',
  RESERVED: '대여 예정',
  RENTING: '대여 중',
  OVERDUE: '연체',
  RETURNED: '상태 확인 대기',
};

/** 삭제를 막는 대여(item.md REQ-008). error.details 모양 */
export function findRentalsBlockingDelete(rentals, itemId) {
  return rentals
    .filter((rental) => rental.itemId === itemId && ACTIVE_FOR_DELETE[rental.status])
    .map((rental) => ({
      field: 'rentals',
      reason: `${rental.startDate} ~ ${rental.endDate} ${ACTIVE_FOR_DELETE[rental.status]} (rentalId ${rental.rentalId})`,
    }));
}

function isCovered(date, periods) {
  return periods.some(({ startDate, endDate }) => startDate <= date && date <= endDate);
}

/**
 * 새 가능 기간이 예약 날짜를 빼면 그 예약 기간을 돌려준다(REQ-007). occupying은 기간을 점유한 대여.
 * 구간 하나에 다 들어가지 않아도 맞닿은 구간들로 덮이면 괜찮아 날짜마다 본다. error.details 모양
 */
export function findPeriodConflicts(occupying, periods) {
  return occupying
    .filter((rental) => {
      for (let date = rental.startDate; date <= rental.endDate; date = addDays(date, 1)) {
        if (!isCovered(date, periods)) {
          return true;
        }
      }
      return false;
    })
    .map((rental) => ({
      field: 'availablePeriods',
      reason: `${rental.startDate} ~ ${rental.endDate}`,
    }));
}

// policies.md §4 권장 보증금. 키는 V3__insert_categories.sql 순서의 categoryId
const DEPOSIT_RATES = {
  1: [0.3, 0.5], // CAMPING
  2: [0.2, 0.3], // TOOL
  3: [0.5, 0.7], // CAMERA
  4: [0.3, 0.5], // BABY
  5: [0.2, 0.3], // PARTY
  6: [0.3, 0.5], // HOBBY
  7: [0.3, 0.3], // APPLIANCE
  8: [0.3, 0.3], // ETC
};

/**
 * 권장 보증금(ItemDepositGuideResponse). 없는 카테고리는 null.
 * TODO: 백엔드 확정 전 임시 — 명세에 원 미만 처리가 없어 내림으로 둔다(실제 값은 feat/item-crud)
 */
export function calculateDepositGuide(categoryId, itemPrice) {
  const rates = DEPOSIT_RATES[categoryId];
  if (!rates) {
    return null;
  }
  return {
    minDeposit: Math.floor((itemPrice * Math.round(rates[0] * 100)) / 100),
    maxDeposit: Math.floor((itemPrice * Math.round(rates[1] * 100)) / 100),
  };
}
