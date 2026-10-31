// 물품 등록·수정·삭제·수정 조회·권장 보증금 목(openapi.json 그대로). 등록·수정한 물품은 메모리(itemDb)에만 있어 새로고침하면 시드로 돌아간다.
// 사진은 objectURL로 보여 준다(핸들러가 페이지에서 돌아 그대로 쓸 수 있고, 새로고침하면 함께 사라진다).
// 핸들러 순서: /api/items/deposit-guide는 /api/items/:itemId와 경로가 겹쳐 handlers.js가 이 배열을 물품 상세보다 앞에 둔다.
import { delay, http } from 'msw';

import { todayInSeoul } from '../shared/lib/date';
import { findMockItem, itemDb, MOCK_CATEGORIES, MOCK_MEMBERS } from './item';
import {
  applyImageOrder,
  calculateDepositGuide,
  checkImageFiles,
  findPeriodConflicts,
  findRentalsBlockingDelete,
  isValidImageCount,
  validateItemCreate,
  validateItemUpdate,
} from './itemWriteLogic';
import { toMemberResponse } from './memberLogic';
import { memberDb, MOCK_REGION_MASTER } from './memberSeed';
import { readPathId } from './pageList';
import { db, nowServer, occupiesPeriod } from './rentalStore';
import { fail, ok } from './response';
import { authenticateMockRequest } from './session';

const MESSAGES = {
  AUTH_UNAUTHORIZED: '로그인이 필요합니다.',
  COMMON_INVALID_INPUT: '입력값이 올바르지 않습니다.',
  COMMON_UNSUPPORTED_MEDIA_TYPE: '지원하지 않는 요청 형식입니다.',
  IMAGE_COUNT_INVALID: '이미지 장수가 올바르지 않습니다.',
  IMAGE_INVALID_TYPE: 'jpg 또는 png 이미지만 업로드할 수 있습니다.',
  IMAGE_SIZE_EXCEEDED: '이미지 용량이 너무 큽니다.',
  ITEM_HAS_ACTIVE_RENTAL: '진행 중인 대여가 있어 삭제할 수 없습니다.',
  ITEM_NOT_FOUND: '물품을 찾을 수 없습니다.',
  ITEM_NOT_OWNER: '물품 등록자만 처리할 수 있습니다.',
  ITEM_PERIOD_CONFLICT: '예약이 있는 날짜는 대여 가능 기간에서 뺄 수 없습니다.',
  REGION_NOT_VERIFIED: '대표 동네 인증이 필요합니다.',
};

const STATUS_BY_CODE = {
  AUTH_UNAUTHORIZED: 401,
  COMMON_INVALID_INPUT: 400,
  COMMON_UNSUPPORTED_MEDIA_TYPE: 415,
  IMAGE_COUNT_INVALID: 400,
  IMAGE_INVALID_TYPE: 400,
  IMAGE_SIZE_EXCEEDED: 400,
  ITEM_HAS_ACTIVE_RENTAL: 409,
  ITEM_NOT_FOUND: 404,
  ITEM_NOT_OWNER: 403,
  ITEM_PERIOD_CONFLICT: 409,
  REGION_NOT_VERIFIED: 403,
};

class MockItemError extends Error {
  constructor(code, details = []) {
    super(code);
    this.code = code;
    this.details = details;
  }
}

function reject(code, details) {
  throw new MockItemError(code, details);
}

/** 예외를 실패 응답으로. 지연을 조금 넣어 제출 중 상태를 볼 수 있게 한다 */
function handle(resolver) {
  return async (info) => {
    await delay(400);
    try {
      return await resolver(info);
    } catch (error) {
      if (error instanceof MockItemError) {
        return fail(STATUS_BY_CODE[error.code], error.code, MESSAGES[error.code], error.details);
      }
      throw error;
    }
  };
}

function requireMember(request) {
  return authenticateMockRequest(request) ?? reject('AUTH_UNAUTHORIZED');
}

function requireItemId(params) {
  return (
    readPathId(params.itemId) ??
    reject('COMMON_INVALID_INPUT', [{ field: 'itemId', reason: '형식이 올바르지 않습니다.' }])
  );
}

/** 없거나 삭제 404, 남의 물품 403. 저장소 항목을 돌려준다(이 물품을 고치는 쪽이 쓴다) */
function requireOwnedRecord(itemId, memberId) {
  const record = itemDb.items.find((item) => item.itemId === itemId);
  if (!record || record.isDeleted) {
    reject('ITEM_NOT_FOUND');
  }
  if (record.ownerId !== memberId) {
    reject('ITEM_NOT_OWNER');
  }
  return record;
}

/** multipart 읽기. request 파트가 json이 아니면 415 */
async function readMultipart(request) {
  const form = await request.formData();
  const part = form.get('request');
  if (!part || typeof part === 'string' || !part.type.startsWith('application/json')) {
    reject('COMMON_UNSUPPORTED_MEDIA_TYPE');
  }
  return { body: JSON.parse(await part.text()), files: form.getAll('images') };
}

function toStoredImages(files) {
  return files.map((file) => {
    itemDb.nextImageId += 1;
    const url = URL.createObjectURL(file);
    return { imageId: itemDb.nextImageId, url, thumbnailUrl: url };
  });
}

function categoryIds() {
  return MOCK_CATEGORIES.map((category) => category.categoryId);
}

/** ItemEditResponse */
function toEditResponse(itemId) {
  const item = findMockItem(itemId);
  return {
    itemId: item.itemId,
    name: item.name,
    description: item.description,
    categoryId: item.categoryId,
    condition: item.condition,
    pricePerDay: item.pricePerDay,
    itemPrice: item.itemPrice,
    deposit: item.deposit,
    images: item.images,
    availablePeriods: item.availablePeriods,
    address: item.address,
    latitude: item.latitude,
    longitude: item.longitude,
    status: 'AVAILABLE',
  };
}

export const itemWriteHandlers = [
  // 검사 순서: request part 400(415) → 403 REGION_NOT_VERIFIED → 사진 400
  http.post(
    '/api/items',
    handle(async ({ request }) => {
      const member = requireMember(request);
      const { body, files } = await readMultipart(request);
      const errors = validateItemCreate(body, {
        categoryIds: categoryIds(),
        today: todayInSeoul(),
      });
      if (errors.length > 0) {
        reject('COMMON_INVALID_INPUT', errors);
      }
      const { nickname, primaryRegion } = toMemberResponse(
        memberDb,
        member.memberId,
        MOCK_REGION_MASTER,
      );
      if (!primaryRegion?.isVerified) {
        reject('REGION_NOT_VERIFIED');
      }
      if (!isValidImageCount(files.length)) {
        reject('IMAGE_COUNT_INVALID');
      }
      const imageError = checkImageFiles(files);
      if (imageError) {
        reject(imageError);
      }
      // 시드에 없는 회원(가입 계정)이 등록해도 상세·대여 목이 제공자 정보를 찾게 한다
      MOCK_MEMBERS[member.memberId] ??= { nickname, regionName: primaryRegion.regionName };
      itemDb.nextItemId += 1;
      itemDb.items.push({
        itemId: itemDb.nextItemId,
        regionId: primaryRegion.regionId,
        ownerId: member.memberId,
        name: body.name,
        description: body.description,
        categoryId: body.categoryId,
        condition: body.condition,
        pricePerDay: body.pricePerDay,
        itemPrice: body.itemPrice,
        deposit: body.deposit,
        address: body.address,
        latitude: body.latitude,
        longitude: body.longitude,
        periods: body.availablePeriods,
        images: toStoredImages(files),
        bumpedAt: nowServer(),
      });
      return ok({ itemId: itemDb.nextItemId }, { status: 201 });
    }),
  ),

  // 검사 순서: 입력 400 → 404 → 403
  http.get(
    '/api/members/me/items/:itemId',
    handle(({ params, request }) => {
      const member = requireMember(request);
      const itemId = requireItemId(params);
      requireOwnedRecord(itemId, member.memberId);
      return ok(toEditResponse(itemId));
    }),
  ),

  // 검사 순서: request part 400 → 404 → 403 → 이미지 400 → 409
  http.patch(
    '/api/items/:itemId',
    handle(async ({ params, request }) => {
      const member = requireMember(request);
      const itemId = requireItemId(params);
      const { body, files } = await readMultipart(request);
      const errors = validateItemUpdate(body, { categoryIds: categoryIds() });
      if (body.imageOrder == null && files.length > 0) {
        errors.push({ field: 'imageOrder', reason: '이미지 순서가 필요합니다.' });
      }
      if (errors.length > 0) {
        reject('COMMON_INVALID_INPUT', errors);
      }
      const record = requireOwnedRecord(itemId, member.memberId);

      const imageError = checkImageFiles(files);
      if (imageError) {
        reject(imageError);
      }
      let images;
      if (body.imageOrder != null) {
        const applied = applyImageOrder(
          findMockItem(itemId).images,
          body.imageOrder,
          toStoredImages(files),
        );
        if (applied.error) {
          reject('COMMON_INVALID_INPUT', [applied.error]);
        }
        if (!isValidImageCount(applied.images.length)) {
          reject('IMAGE_COUNT_INVALID');
        }
        images = applied.images;
      }

      if (body.availablePeriods) {
        const occupying = db.rentals.filter(
          (rental) => rental.itemId === itemId && occupiesPeriod(rental),
        );
        const conflicts = findPeriodConflicts(occupying, body.availablePeriods);
        if (conflicts.length > 0) {
          reject('ITEM_PERIOD_CONFLICT', conflicts);
        }
        record.periods = body.availablePeriods;
      }
      if (images) {
        record.images = images;
      }
      for (const field of [
        'name',
        'description',
        'categoryId',
        'condition',
        'pricePerDay',
        'itemPrice',
        'deposit',
        'address',
        'latitude',
        'longitude',
      ]) {
        if (body[field] !== undefined) {
          record[field] = body[field];
        }
      }
      return ok(toEditResponse(itemId));
    }),
  ),

  // 소프트 삭제. 검사 순서: 입력 400 → 404 → 403 → 409
  http.delete(
    '/api/items/:itemId',
    handle(({ params, request }) => {
      const member = requireMember(request);
      const record = requireOwnedRecord(requireItemId(params), member.memberId);
      const blocking = findRentalsBlockingDelete(db.rentals, record.itemId);
      if (blocking.length > 0) {
        reject('ITEM_HAS_ACTIVE_RENTAL', blocking);
      }
      record.isDeleted = true;
      return ok(null);
    }),
  ),

  http.get(
    '/api/items/deposit-guide',
    handle(({ request }) => {
      requireMember(request);
      const { searchParams } = new URL(request.url);
      const categoryId = Number(searchParams.get('categoryId'));
      const itemPrice = Number(searchParams.get('itemPrice'));
      if (!Number.isInteger(itemPrice) || itemPrice < 0) {
        reject('COMMON_INVALID_INPUT', [
          { field: 'itemPrice', reason: '형식이 올바르지 않습니다.' },
        ]);
      }
      const guide = calculateDepositGuide(categoryId, itemPrice);
      return guide
        ? ok(guide)
        : reject('COMMON_INVALID_INPUT', [
            { field: 'categoryId', reason: '사용할 수 없는 카테고리입니다.' },
          ]);
    }),
  ),
];
