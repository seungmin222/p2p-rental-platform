import { describe, expect, it } from 'vitest';

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

const context = { categoryIds: [1, 2], today: '2026-11-01' };
const body = {
  name: '텐트',
  description: '설명',
  categoryId: 1,
  condition: 'USED',
  pricePerDay: 1000,
  itemPrice: 0,
  deposit: 3_000_000,
  address: '인천 연수구',
  latitude: 37.39,
  longitude: 126.63,
  availablePeriods: [{ startDate: '2026-11-01', endDate: '2026-11-30' }],
};

function fields(errors) {
  return errors.map((error) => error.field);
}

describe('validateItemCreate', () => {
  it('맞는 본문은 오류가 없다', () => {
    expect(validateItemCreate(body, context)).toEqual([]);
  });

  it('없는 필드는 전부 오류다', () => {
    expect(fields(validateItemCreate({}, context))).toEqual(
      expect.arrayContaining(['name', 'categoryId', 'latitude', 'longitude', 'availablePeriods']),
    );
  });

  it.each([
    ['pricePerDay', 999, true],
    ['pricePerDay', 0, false],
    ['pricePerDay', 1000, false],
    ['pricePerDay', 1_000_001, true],
    ['deposit', 999, true],
    ['deposit', 3_000_001, true],
    ['itemPrice', 10_000_001, true],
    ['itemPrice', 10_000_000, false],
  ])('%s %i → 오류 %s', (field, value, isError) => {
    const errors = fields(validateItemCreate({ ...body, [field]: value }, context));
    expect(errors.includes(field)).toBe(isError);
  });

  it('이름은 코드 포인트로 센다', () => {
    expect(validateItemCreate({ ...body, name: '😀'.repeat(50) }, context)).toEqual([]);
    expect(fields(validateItemCreate({ ...body, name: '😀'.repeat(51) }, context))).toEqual([
      'name',
    ]);
  });

  it('없는 카테고리·지난 시작일·겹치는 기간은 오류다', () => {
    expect(fields(validateItemCreate({ ...body, categoryId: 9 }, context))).toEqual(['categoryId']);
    const past = [{ startDate: '2026-10-31', endDate: '2026-11-02' }];
    expect(fields(validateItemCreate({ ...body, availablePeriods: past }, context))).toEqual([
      'availablePeriods',
    ]);
    const overlapping = [
      { startDate: '2026-11-01', endDate: '2026-11-05' },
      { startDate: '2026-11-05', endDate: '2026-11-06' },
    ];
    expect(fields(validateItemCreate({ ...body, availablePeriods: overlapping }, context))).toEqual(
      ['availablePeriods'],
    );
  });
});

describe('validateItemUpdate', () => {
  it('보낸 필드만 본다', () => {
    expect(validateItemUpdate({ deposit: 0 }, context)).toEqual([]);
    expect(fields(validateItemUpdate({ deposit: 500 }, context))).toEqual(['deposit']);
  });

  it('지난 시작일은 허용한다', () => {
    const periods = [{ startDate: '2020-01-01', endDate: '2020-01-02' }];
    expect(validateItemUpdate({ availablePeriods: periods }, context)).toEqual([]);
  });

  it('주소를 바꾸면 좌표도 함께 와야 한다', () => {
    expect(fields(validateItemUpdate({ address: '새 주소' }, context))).toEqual(['latitude']);
    expect(
      validateItemUpdate({ address: '새 주소', latitude: 37, longitude: 126 }, context),
    ).toEqual([]);
  });
});

describe('사진 검사', () => {
  it('형식 → 용량 순으로 코드를 돌려준다', () => {
    expect(checkImageFiles([{ type: 'image/gif', size: 1 }])).toBe('IMAGE_INVALID_TYPE');
    expect(checkImageFiles([{ type: 'image/png', size: 10 * 1024 * 1024 + 1 }])).toBe(
      'IMAGE_SIZE_EXCEEDED',
    );
    expect(checkImageFiles([{ type: 'image/jpeg', size: 10 * 1024 * 1024 }])).toBeNull();
  });

  it('장수는 1~10장', () => {
    expect([0, 1, 10, 11].map(isValidImageCount)).toEqual([false, true, true, false]);
  });
});

describe('applyImageOrder', () => {
  const current = [{ imageId: 1 }, { imageId: 2 }, { imageId: 3 }];
  const added = [{ imageId: 100 }];

  it('목록 순서대로 유지·추가하고 빠진 기존 이미지는 지운다', () => {
    const order = [{ newImageIndex: 0 }, { imageId: 3 }, { imageId: 1 }];
    expect(applyImageOrder(current, order, added).images.map((image) => image.imageId)).toEqual([
      100, 3, 1,
    ]);
  });

  it.each([
    ['없는 imageId', [{ imageId: 9 }, { newImageIndex: 0 }]],
    ['없는 순번', [{ newImageIndex: 1 }]],
    ['가리키지 않은 파일', [{ imageId: 1 }]],
    ['둘 다 있는 칸', [{ imageId: 1, newImageIndex: 0 }]],
    ['둘 다 없는 칸', [{}, { newImageIndex: 0 }]],
    ['같은 이미지 두 번', [{ imageId: 1 }, { imageId: 1 }, { newImageIndex: 0 }]],
  ])('%s면 imageOrder 오류', (_, order) => {
    expect(applyImageOrder(current, order, added).error.field).toBe('imageOrder');
  });
});

describe('findRentalsBlockingDelete', () => {
  it('진행 중인 대여만 details 모양으로 돌려준다', () => {
    const rentals = [
      {
        rentalId: 12,
        itemId: 1,
        status: 'RESERVED',
        startDate: '2026-11-03',
        endDate: '2026-11-05',
      },
      {
        rentalId: 13,
        itemId: 1,
        status: 'COMPLETED',
        startDate: '2026-10-03',
        endDate: '2026-10-05',
      },
      {
        rentalId: 14,
        itemId: 2,
        status: 'RENTING',
        startDate: '2026-10-03',
        endDate: '2026-11-05',
      },
    ];
    expect(findRentalsBlockingDelete(rentals, 1)).toEqual([
      { field: 'rentals', reason: '2026-11-03 ~ 2026-11-05 대여 예정 (rentalId 12)' },
    ]);
  });
});

describe('findPeriodConflicts', () => {
  const rental = { startDate: '2026-11-03', endDate: '2026-11-05' };

  it('예약 날짜가 빠지면 그 기간을 돌려준다', () => {
    expect(
      findPeriodConflicts([rental], [{ startDate: '2026-11-04', endDate: '2026-11-30' }]),
    ).toEqual([{ field: 'availablePeriods', reason: '2026-11-03 ~ 2026-11-05' }]);
  });

  it('맞닿은 구간들로 덮이면 충돌이 아니다', () => {
    expect(
      findPeriodConflicts(
        [rental],
        [
          { startDate: '2026-11-01', endDate: '2026-11-03' },
          { startDate: '2026-11-04', endDate: '2026-11-10' },
        ],
      ),
    ).toEqual([]);
  });
});

describe('calculateDepositGuide', () => {
  it('카테고리 비율대로 계산한다(policies.md §4)', () => {
    expect(calculateDepositGuide(3, 200000)).toEqual({ minDeposit: 100000, maxDeposit: 140000 });
    expect(calculateDepositGuide(8, 100000)).toEqual({ minDeposit: 30000, maxDeposit: 30000 });
  });

  it('없는 카테고리는 null', () => {
    expect(calculateDepositGuide(99, 1000)).toBeNull();
  });
});
