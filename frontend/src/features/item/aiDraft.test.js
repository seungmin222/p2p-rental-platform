import { describe, expect, it } from 'vitest';

import {
  getDraftDeposit,
  getDraftErrorNotice,
  getDraftFiles,
  getDraftFills,
  getDraftNotice,
  isUserEdited,
} from './aiDraft';
import { createItemFormValues } from './itemForm';

const DRAFT = {
  status: 'DONE',
  categoryId: 1,
  categoryCode: 'CAMPING',
  categoryName: '캠핑·아웃도어',
  suggestedCategoryName: null,
  title: '4인용 돔 텐트',
  description: '설명',
  itemPrice: 200000,
  pricePerDay: 15000,
  minDeposit: 60000,
  maxDeposit: 100000,
};

function fills(draft, { current = {}, aiFilled = {} } = {}) {
  const initial = createItemFormValues();
  return getDraftFills(draft, { current: { ...initial, ...current }, initial, aiFilled });
}

describe('getDraftFiles', () => {
  it('새 파일만 순서대로, 최대 10장', () => {
    const files = Array.from({ length: 12 }, (_, i) => ({ name: `${i}.jpg` }));
    const images = [{ id: 'old', url: '/a.jpg' }, ...files.map((file, i) => ({ id: i, file }))];
    expect(getDraftFiles(images)).toEqual(files.slice(0, 10));
  });
});

describe('isUserEdited', () => {
  it('처음 값이거나 AI가 넣은 값 그대로면 고친 것이 아니다', () => {
    expect(isUserEdited('', '', undefined)).toBe(false);
    expect(isUserEdited('텐트', '', '텐트')).toBe(false);
    expect(isUserEdited('텐트2', '', '텐트')).toBe(true);
  });
});

describe('getDraftDeposit', () => {
  it.each([
    [null, null],
    [0, 0],
    [600, 1000],
    [1000, 1000],
    [60000, 60000],
    [3000000, 3000000],
    [5000000, 3000000],
  ])('권장 하한 %s → %s', (minDeposit, expected) => {
    expect(getDraftDeposit(minDeposit)).toBe(expected);
  });
});

describe('getDraftFills', () => {
  it('빈 폼이면 카테고리·이름·설명·금액·보증금(권장 하한)을 채운다(금액은 문자열, 상태는 안 채움)', () => {
    expect(fills(DRAFT)).toEqual([
      { name: 'categoryId', value: 1 },
      { name: 'name', value: '4인용 돔 텐트' },
      { name: 'description', value: '설명' },
      { name: 'pricePerDay', value: '15000' },
      { name: 'itemPrice', value: '200000' },
      { name: 'deposit', value: '60000' },
    ]);
  });

  it('보증금: 하한이 null이면 채우지 않고, 1,000원 미만·상한 초과는 맞춰 채운다', () => {
    const names = fills({ ...DRAFT, minDeposit: null, maxDeposit: null }).map((fill) => fill.name);
    expect(names).not.toContain('deposit');
    expect(fills({ ...DRAFT, minDeposit: 600 })).toContainEqual({ name: 'deposit', value: '1000' });
    expect(fills({ ...DRAFT, minDeposit: 5000000 })).toContainEqual({
      name: 'deposit',
      value: '3000000',
    });
    expect(fills({ ...DRAFT, minDeposit: 0 })).toContainEqual({ name: 'deposit', value: '0' });
  });

  it('보증금도 사용자가 고친 값은 덮지 않고, AI가 넣은 값은 다시 누르면 바꾼다', () => {
    const edited = fills(DRAFT, { current: { deposit: '50000' }, aiFilled: { deposit: '40000' } });
    expect(edited.map((fill) => fill.name)).not.toContain('deposit');
    const refilled = fills(DRAFT, {
      current: { deposit: '40000' },
      aiFilled: { deposit: '40000' },
    });
    expect(refilled).toContainEqual({ name: 'deposit', value: '60000' });
  });

  it('사용자가 고친 칸과 null 필드는 건너뛴다', () => {
    const result = fills(
      { ...DRAFT, description: null, pricePerDay: null },
      { current: { name: '내가 쓴 이름', categoryId: 3 } },
    );
    expect(result.map((fill) => fill.name)).toEqual(['itemPrice', 'deposit']);
  });

  it('0원은 null이 아니라 채운다', () => {
    expect(fills({ ...DRAFT, pricePerDay: 0 })).toContainEqual({ name: 'pricePerDay', value: '0' });
  });

  it('다시 부르면 AI가 넣은 그대로인 칸만 바꾼다', () => {
    const aiFilled = { name: '이전 제목', itemPrice: '100000' };
    const result = fills(DRAFT, {
      current: { name: '이전 제목', itemPrice: '120000' },
      aiFilled,
    });
    const names = result.map((fill) => fill.name);
    expect(names).toContain('name');
    expect(names).not.toContain('itemPrice');
  });

  it.each(['FAILED', 'LIMIT_EXCEEDED'])('%s면 채우지 않는다', (status) => {
    expect(fills({ status })).toEqual([]);
  });
});

describe('getDraftNotice', () => {
  it('DONE', () => {
    expect(getDraftNotice(DRAFT)).toEqual({
      tone: 'surface',
      messages: ['AI가 초안을 채웠어요. 내용을 확인하고 고쳐 주세요'],
    });
  });

  it('보증금을 채웠을 때만 보증금 문구를 덧붙인다(보통·올림·자름)', () => {
    const deposit = [{ name: 'deposit' }];
    expect(getDraftNotice(DRAFT, [{ name: 'name' }]).messages).toHaveLength(1);
    expect(getDraftNotice(DRAFT, deposit).messages[1]).toBe(
      '보증금은 권장 범위에서 가장 낮은 금액으로 넣었어요',
    );
    expect(getDraftNotice({ ...DRAFT, minDeposit: 600 }, deposit).messages[1]).toBe(
      '보증금은 등록할 수 있는 최소 금액(1,000원)으로 넣었어요',
    );
    expect(getDraftNotice({ ...DRAFT, minDeposit: 5000000 }, deposit).messages[1]).toBe(
      '보증금은 등록 상한(3,000,000원)으로 넣었어요',
    );
  });

  it('ETC면 후보 이름을, 없으면 앞 문장만 덧붙인다', () => {
    const etc = { ...DRAFT, categoryCode: 'ETC' };
    expect(getDraftNotice({ ...etc, suggestedCategoryName: '악기' }).messages[1]).toBe(
      "맞는 카테고리가 없어 '기타'로 골랐어요. AI가 떠올린 분류: 악기",
    );
    expect(getDraftNotice(etc).messages[1]).toBe("맞는 카테고리가 없어 '기타'로 골랐어요");
  });

  it('FAILED·LIMIT_EXCEEDED는 직접 입력 안내', () => {
    expect(getDraftNotice({ status: 'FAILED' }).messages).toEqual([
      'AI 초안을 만들지 못했어요. 직접 입력해 주세요',
    ]);
    expect(getDraftNotice({ status: 'LIMIT_EXCEEDED' }).messages).toEqual([
      '오늘 AI 사용 횟수를 모두 썼어요. 직접 입력해 주세요',
    ]);
  });
});

describe('getDraftErrorNotice', () => {
  it('사진 오류는 사진 문구, 그 밖은 FAILED 문구', () => {
    expect(getDraftErrorNotice({ code: 'IMAGE_INVALID_TYPE' }).messages).toEqual([
      'jpg 또는 png 사진만 올릴 수 있어요',
    ]);
    expect(getDraftErrorNotice({ code: 'CLIENT_NETWORK_ERROR' }).messages).toEqual([
      'AI 초안을 만들지 못했어요. 직접 입력해 주세요',
    ]);
  });
});
