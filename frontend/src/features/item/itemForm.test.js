import { describe, expect, it } from 'vitest';

import {
  createItemFormValues,
  parseAmount,
  toCreatePayload,
  toImageOrder,
  toUpdatePayload,
  validateAddress,
  validateDeposit,
  validateImages,
  validateItemPrice,
  validateName,
  validatePeriods,
  validatePricePerDay,
} from './itemForm';

const fileA = { name: 'a.png' };
const fileB = { name: 'b.png' };

const initial = {
  ...createItemFormValues(),
  name: '텐트',
  description: '설명',
  images: [
    { id: 11, url: '/1.jpg' },
    { id: 12, url: '/2.jpg' },
  ],
  categoryId: 1,
  condition: 'USED',
  pricePerDay: '15000',
  itemPrice: '200000',
  deposit: '50000',
  availablePeriods: [{ startDate: '2026-11-01', endDate: '2026-11-30' }],
  address: '인천 연수구',
  latitude: 37.39,
  longitude: 126.63,
};

describe('parseAmount', () => {
  it('숫자와 쉼표만 받는다', () => {
    expect(parseAmount('15,000')).toBe(15000);
    expect(parseAmount('0')).toBe(0);
    expect(parseAmount('')).toBeNull();
    expect(parseAmount('12.5')).toBeNull();
    expect(parseAmount('-1')).toBeNull();
  });
});

describe('금액 검증', () => {
  it.each([
    ['0', true],
    ['999', false],
    ['1000', true],
    ['1000000', true],
    ['1000001', false],
    ['abc', false],
  ])('일 대여료 %s → 통과 %s', (text, passes) => {
    expect(validatePricePerDay(text) === true).toBe(passes);
  });

  it.each([
    ['0', true],
    ['999', false],
    ['3000000', true],
    ['3000001', false],
  ])('보증금 %s → 통과 %s', (text, passes) => {
    expect(validateDeposit(text) === true).toBe(passes);
  });

  it.each([
    ['0', true],
    ['500', true],
    ['10000000', true],
    ['10000001', false],
  ])('물품 가격 %s → 통과 %s', (text, passes) => {
    expect(validateItemPrice(text) === true).toBe(passes);
  });
});

describe('글자 수', () => {
  it('코드 포인트로 센다', () => {
    expect(validateName('😀'.repeat(50))).toBe(true);
    expect(validateName('😀'.repeat(51))).not.toBe(true);
  });

  it('공백만 있으면 비어 있는 것이다', () => {
    expect(validateName('   ')).not.toBe(true);
    expect(validateAddress('  ')).not.toBe(true);
  });
});

describe('validatePeriods', () => {
  const today = '2026-11-01';

  it('맞는 기간은 통과한다(맞닿지 않은 여러 구간)', () => {
    expect(
      validatePeriods(
        [
          { startDate: '2026-11-10', endDate: '2026-11-12' },
          { startDate: '2026-11-01', endDate: '2026-11-09' },
        ],
        { today },
      ),
    ).toBe(true);
  });

  it.each([
    ['없음', []],
    ['빈 칸', [{ startDate: '2026-11-01', endDate: '' }]],
    ['뒤집힘', [{ startDate: '2026-11-03', endDate: '2026-11-01' }]],
    ['지난 시작일', [{ startDate: '2026-10-31', endDate: '2026-11-01' }]],
    [
      '겹침',
      [
        { startDate: '2026-11-01', endDate: '2026-11-05' },
        { startDate: '2026-11-05', endDate: '2026-11-06' },
      ],
    ],
  ])('%s면 오류', (_, periods) => {
    expect(validatePeriods(periods, { today })).not.toBe(true);
  });

  it('수정은 today를 넘기지 않아 지난 시작일을 허용한다', () => {
    expect(validatePeriods([{ startDate: '2020-01-01', endDate: '2020-01-02' }])).toBe(true);
  });
});

describe('validateImages', () => {
  it('등록은 1장 이상', () => {
    expect(validateImages([])).not.toBe(true);
    expect(validateImages([{ id: 'local-1', file: fileA }])).toBe(true);
  });

  it('수정에서 사진이 그대로면 없어도 통과, 지우면 오류', () => {
    expect(validateImages([], { initialImages: [] })).toBe(true);
    expect(validateImages([], { initialImages: [{ id: 1 }] })).not.toBe(true);
  });
});

describe('toCreatePayload', () => {
  it('금액은 숫자로, 텍스트는 trim, 사진은 파일만 순서대로', () => {
    const values = {
      ...initial,
      name: ' 텐트 ',
      images: [
        { id: 'local-1', file: fileB },
        { id: 'local-2', file: fileA },
      ],
    };
    const { request, images } = toCreatePayload(values);
    expect(images).toEqual([fileB, fileA]);
    expect(request).toEqual({
      name: '텐트',
      description: '설명',
      categoryId: 1,
      condition: 'USED',
      pricePerDay: 15000,
      itemPrice: 200000,
      deposit: 50000,
      availablePeriods: [{ startDate: '2026-11-01', endDate: '2026-11-30' }],
      address: '인천 연수구',
      latitude: 37.39,
      longitude: 126.63,
    });
  });
});

describe('toImageOrder', () => {
  it('기존은 imageId, 새 사진은 파일 순번', () => {
    expect(
      toImageOrder([
        { id: 'local-5', file: fileA },
        { id: 11, url: '/1.jpg' },
        { id: 'local-6', file: fileB },
      ]),
    ).toEqual([{ newImageIndex: 0 }, { imageId: 11 }, { newImageIndex: 1 }]);
  });
});

describe('toUpdatePayload', () => {
  it('바뀐 것이 없으면 null(공백만 다른 이름 포함)', () => {
    expect(toUpdatePayload(initial, { ...initial })).toBeNull();
    expect(toUpdatePayload(initial, { ...initial, name: ' 텐트 ' })).toBeNull();
    expect(toUpdatePayload(initial, { ...initial, deposit: '50,000' })).toBeNull();
  });

  it('바뀐 필드만 보낸다', () => {
    expect(toUpdatePayload(initial, { ...initial, deposit: '0', condition: 'LIKE_NEW' })).toEqual({
      request: { deposit: 0, condition: 'LIKE_NEW' },
      images: [],
    });
  });

  it('사진이 그대로면 imageOrder·images를 보내지 않는다', () => {
    const { request, images } = toUpdatePayload(initial, { ...initial, name: '새 이름' });
    expect(request).not.toHaveProperty('imageOrder');
    expect(images).toEqual([]);
  });

  it('순서만 바꿔도 imageOrder를 보내고 파일은 없다', () => {
    const values = { ...initial, images: [...initial.images].reverse() };
    expect(toUpdatePayload(initial, values)).toEqual({
      request: { imageOrder: [{ imageId: 12 }, { imageId: 11 }] },
      images: [],
    });
  });

  it('새 사진은 파일과 순번, 빠진 기존 사진은 목록에서 빠진다', () => {
    const values = {
      ...initial,
      images: [{ id: 'local-1', file: fileA }, initial.images[1]],
    };
    expect(toUpdatePayload(initial, values)).toEqual({
      request: { imageOrder: [{ newImageIndex: 0 }, { imageId: 12 }] },
      images: [fileA],
    });
  });

  it('주소를 바꾸면 기존 좌표도 함께 보낸다', () => {
    expect(toUpdatePayload(initial, { ...initial, address: '새 주소' }).request).toEqual({
      address: '새 주소',
      latitude: 37.39,
      longitude: 126.63,
    });
  });

  it('위치만 다시 가져오면 좌표 둘 다 보낸다', () => {
    expect(toUpdatePayload(initial, { ...initial, latitude: 37.5 }).request).toEqual({
      latitude: 37.5,
      longitude: 126.63,
    });
  });

  it('기간은 바뀌면 전체를 보낸다', () => {
    const availablePeriods = [
      ...initial.availablePeriods,
      { startDate: '2026-12-01', endDate: '2026-12-02' },
    ];
    expect(toUpdatePayload(initial, { ...initial, availablePeriods }).request).toEqual({
      availablePeriods,
    });
  });
});
