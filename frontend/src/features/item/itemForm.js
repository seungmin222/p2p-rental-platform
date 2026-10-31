// 물품 등록·수정 폼의 순수 함수(기본값·검증·요청 변환). 금액 칸은 문자열로 들고 있다가 제출할 때 숫자로 바꾼다.
import { countCodePoints } from '../../shared/lib/validators';
import { getCurrentCoords, TRADE_PLACE_GEOLOCATION_OPTIONS } from '../region/geolocation';
import {
  ITEM_DEPOSIT,
  ITEM_IMAGE_MAX_MB,
  ITEM_IMAGES,
  ITEM_MIN_PAID_AMOUNT,
  ITEM_PRICE,
  ITEM_PRICE_PER_DAY,
  ITEM_TEXT_MAX,
} from './constants';

export const EMPTY_PERIOD = { startDate: '', endDate: '' };

/** 등록 폼 기본값 */
export function createItemFormValues() {
  return {
    name: '',
    description: '',
    images: [],
    categoryId: null,
    condition: null,
    pricePerDay: '',
    itemPrice: '',
    deposit: '',
    availablePeriods: [{ ...EMPTY_PERIOD }],
    address: '',
    latitude: null,
    longitude: null,
  };
}

/** 금액 칸 → 숫자. 숫자가 아니면 null(쉼표는 허용) */
export function parseAmount(text) {
  const digits = String(text ?? '')
    .replaceAll(',', '')
    .trim();
  return /^\d+$/.test(digits) ? Number(digits) : null;
}

/** 금액 칸 안내. zeroLabel은 0원의 뜻(무료·보증금 없음) */
export function getPaidAmountHint(zeroLabel) {
  return `0원(${zeroLabel}) 또는 ${ITEM_MIN_PAID_AMOUNT.toLocaleString()}원 이상`;
}

export const ITEM_IMAGE_HINT = `첫 장이 대표 사진이에요. jpg·png, 장당 ${ITEM_IMAGE_MAX_MB}MB`;

/** 거래 장소 좌표. geolocation은 테스트에서 바꿔 넣는다 */
export function getTradePlaceCoords(geolocation) {
  return getCurrentCoords(TRADE_PLACE_GEOLOCATION_OPTIONS, geolocation);
}

/**
 * 카테고리 칸 상태. 이미 받은 목록이 있으면 재조회가 실패해도 칩을 그대로 둔다.
 * @returns {'ready' | 'loading' | 'error'}
 */
export function getCategoryStatus({ data, isPending, isFetching }) {
  if (data) {
    return 'ready';
  }
  return isPending || isFetching ? 'loading' : 'error';
}

/** 값이 곧 바뀔 수 있는 동안(카테고리 로딩·위치 가져오는 중)은 제출하지 않는다 */
export function isSubmitBlocked({ categoryStatus, isLocating }) {
  return categoryStatus === 'loading' || isLocating;
}

// ── 검증(react-hook-form validate 모양: 통과 true, 실패 문구) ──────────────────────

function validateText(value, max, emptyMessage) {
  if (value.trim() === '') {
    return emptyMessage;
  }
  return countCodePoints(value) <= max || `${max}자 이하로 입력해 주세요`;
}

export function validateName(value) {
  return validateText(value, ITEM_TEXT_MAX.NAME, '물품 이름을 입력해 주세요');
}

export function validateDescription(value) {
  return validateText(value, ITEM_TEXT_MAX.DESCRIPTION, '설명을 입력해 주세요');
}

export function validateAddress(value) {
  return validateText(value, ITEM_TEXT_MAX.ADDRESS, '거래 장소 주소를 입력해 주세요');
}

/** 0원 또는 하한~상한(policies.md item.price-per-day·item.deposit) */
function validatePaidAmount(text, max, label) {
  const amount = parseAmount(text);
  if (amount == null) {
    return `${label}을 숫자로 입력해 주세요`;
  }
  return (
    amount === 0 ||
    (amount >= ITEM_MIN_PAID_AMOUNT && amount <= max) ||
    `0원 또는 ${ITEM_MIN_PAID_AMOUNT.toLocaleString()}원 ~ ${max.toLocaleString()}원으로 입력해 주세요`
  );
}

export function validatePricePerDay(text) {
  return validatePaidAmount(text, ITEM_PRICE_PER_DAY.MAX, '일 대여료');
}

export function validateDeposit(text) {
  return validatePaidAmount(text, ITEM_DEPOSIT.MAX, '보증금');
}

export function validateItemPrice(text) {
  const amount = parseAmount(text);
  if (amount == null) {
    return '물품 가격을 숫자로 입력해 주세요';
  }
  return amount <= ITEM_PRICE.MAX || `${ITEM_PRICE.MAX.toLocaleString()}원 이하로 입력해 주세요`;
}

/**
 * 가능 기간(REQ-006). today를 넘기면 시작일 ≥ 오늘도 본다(등록만. 수정 API는 지난 시작일을 허용한다).
 * @param {{startDate: string, endDate: string}[]} periods
 */
export function validatePeriods(periods, { today } = {}) {
  if (periods.length === 0) {
    return '대여 가능 기간을 1개 이상 입력해 주세요';
  }
  if (periods.some(({ startDate, endDate }) => !startDate || !endDate)) {
    return '시작일과 종료일을 모두 입력해 주세요';
  }
  if (periods.some(({ startDate, endDate }) => startDate > endDate)) {
    return '종료일은 시작일과 같거나 뒤여야 해요';
  }
  if (today && periods.some(({ startDate }) => startDate < today)) {
    return '시작일은 오늘 이후로 입력해 주세요';
  }
  const sorted = [...periods].sort((a, b) => (a.startDate < b.startDate ? -1 : 1));
  const overlaps = sorted.some((period, i) => i > 0 && period.startDate <= sorted[i - 1].endDate);
  return !overlaps || '기간끼리 겹치지 않게 입력해 주세요';
}

function sameImages(a, b) {
  return a.length === b.length && a.every((image, i) => image.id === b[i].id);
}

/**
 * 사진 장수. initialImages(수정의 처음 사진)와 같으면 보지 않는다: 바꾸지 않으면 사진을 보내지 않으므로
 * 사진 없는 물품도 가격만 고칠 수 있다.
 */
export function validateImages(images, { initialImages } = {}) {
  if (initialImages && sameImages(images, initialImages)) {
    return true;
  }
  return (
    images.length >= ITEM_IMAGES.MIN_COUNT || `사진을 ${ITEM_IMAGES.MIN_COUNT}장 이상 올려 주세요`
  );
}

// ── 요청 변환 ────────────────────────────────────────────────────────────────

function toFiles(images) {
  return images.filter((image) => image.file).map((image) => image.file);
}

function toPeriods(periods) {
  return periods.map(({ startDate, endDate }) => ({ startDate, endDate }));
}

/** 폼 값 → ItemCreateRequest + 사진 파일(순서 = sortOrder) */
export function toCreatePayload(values) {
  return {
    request: {
      name: values.name.trim(),
      description: values.description.trim(),
      categoryId: values.categoryId,
      condition: values.condition,
      pricePerDay: parseAmount(values.pricePerDay),
      itemPrice: parseAmount(values.itemPrice),
      deposit: parseAmount(values.deposit),
      availablePeriods: toPeriods(values.availablePeriods),
      address: values.address.trim(),
      latitude: values.latitude,
      longitude: values.longitude,
    },
    images: toFiles(values.images),
  };
}

/** 이미지 칸 → ImageOrder: 기존 { imageId }, 새 { newImageIndex }(images 파트의 0부터 순번) */
export function toImageOrder(images) {
  let nextIndex = 0;
  return images.map((image) => {
    if (image.file) {
      nextIndex += 1;
      return { newImageIndex: nextIndex - 1 };
    }
    return { imageId: image.id };
  });
}

function samePeriods(a, b) {
  return (
    a.length === b.length &&
    a.every((period, i) => period.startDate === b[i].startDate && period.endDate === b[i].endDate)
  );
}

/**
 * 처음 값과 비교해 바뀐 필드만 ItemUpdateRequest로. 바뀐 것이 없으면 null.
 * - 사진이 그대로면 imageOrder·images를 보내지 않는다(images만 보내면 400).
 * - 주소를 바꾸면 위도·경도도 함께 보낸다(openapi ItemUpdateRequest).
 * @param {ReturnType<typeof createItemFormValues>} initial
 * @param {ReturnType<typeof createItemFormValues>} values
 */
export function toUpdatePayload(initial, values) {
  const request = {};
  const name = values.name.trim();
  const description = values.description.trim();
  const address = values.address.trim();
  if (name !== initial.name) {
    request.name = name;
  }
  if (description !== initial.description) {
    request.description = description;
  }
  if (values.categoryId !== initial.categoryId) {
    request.categoryId = values.categoryId;
  }
  if (values.condition !== initial.condition) {
    request.condition = values.condition;
  }
  for (const field of ['pricePerDay', 'itemPrice', 'deposit']) {
    const amount = parseAmount(values[field]);
    if (amount !== parseAmount(initial[field])) {
      request[field] = amount;
    }
  }
  if (!samePeriods(values.availablePeriods, initial.availablePeriods)) {
    request.availablePeriods = toPeriods(values.availablePeriods);
  }
  const coordsChanged =
    values.latitude !== initial.latitude || values.longitude !== initial.longitude;
  if (address !== initial.address) {
    request.address = address;
  }
  if (coordsChanged || request.address !== undefined) {
    request.latitude = values.latitude;
    request.longitude = values.longitude;
  }
  const imagesChanged = !sameImages(values.images, initial.images);
  if (imagesChanged) {
    request.imageOrder = toImageOrder(values.images);
  }
  if (Object.keys(request).length === 0) {
    return null;
  }
  return { request, images: imagesChanged ? toFiles(values.images) : [] };
}
