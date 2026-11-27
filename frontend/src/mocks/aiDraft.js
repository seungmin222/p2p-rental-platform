// AI 등록 보조 목(REQ-067, openapi AiDraftResponse 그대로). 핸들러는 itemWrite.js(사진 검사·인증을 같이 쓴다).
// 결과는 첫 사진 파일 이름으로 고른다(대소문자 무시): fail → FAILED, limit → LIMIT_EXCEEDED,
// etc → ETC + 후보 이름, partial → 설명·원가·대여료 null(보증금 범위도 null),
// cheap → 보증금 하한 1,000원 미만(공구, 600원), pricey → 하한 3,000,000원 초과(카메라, 5,000,000원), 그 밖 → DONE 캠핑 텐트.
import { calculateDepositGuide } from './itemWriteLogic';

export const AI_DRAFT_MOCK_DELAY_MS = 2000;

const EMPTY = {
  categoryId: null,
  categoryCode: null,
  categoryName: null,
  suggestedCategoryName: null,
  title: null,
  description: null,
  itemPrice: null,
  pricePerDay: null,
  minDeposit: null,
  maxDeposit: null,
};

function done(fields) {
  const { categoryId, itemPrice } = fields;
  const guide = itemPrice == null ? null : calculateDepositGuide(categoryId, itemPrice);
  return {
    ...EMPTY,
    status: 'DONE',
    ...fields,
    minDeposit: guide?.minDeposit ?? null,
    maxDeposit: guide?.maxDeposit ?? null,
  };
}

const TENT = {
  categoryId: 1,
  categoryCode: 'CAMPING',
  categoryName: '캠핑·아웃도어',
  title: '4인용 돔 텐트',
  description: '4인용 돔 텐트입니다. 폴대·팩·스트링이 함께 있습니다.',
  itemPrice: 200000,
  pricePerDay: 15000,
};

/** @param {string} fileName 첫 사진 파일 이름 */
export function buildMockDraft(fileName) {
  const name = fileName.toLowerCase();
  if (name.includes('fail')) {
    return { ...EMPTY, status: 'FAILED' };
  }
  if (name.includes('limit')) {
    return { ...EMPTY, status: 'LIMIT_EXCEEDED' };
  }
  if (name.includes('etc')) {
    return done({
      categoryId: 8,
      categoryCode: 'ETC',
      categoryName: '기타',
      suggestedCategoryName: '악기',
      title: '어쿠스틱 기타',
      description: '입문용 어쿠스틱 기타입니다. 케이스와 피크가 함께 있습니다.',
      itemPrice: 150000,
      pricePerDay: 5000,
    });
  }
  if (name.includes('cheap')) {
    return done({
      categoryId: 2,
      categoryCode: 'TOOL',
      categoryName: '공구·DIY',
      title: '스패너 세트',
      description: '자주 쓰는 크기의 스패너 세트입니다.',
      itemPrice: 3000,
      pricePerDay: 1000,
    });
  }
  if (name.includes('pricey')) {
    return done({
      categoryId: 3,
      categoryCode: 'CAMERA',
      categoryName: '촬영·영상 장비',
      title: '시네마 카메라',
      description: '영상 촬영용 시네마 카메라입니다. 배터리 2개와 메모리가 함께 있습니다.',
      itemPrice: 10000000,
      pricePerDay: 300000,
    });
  }
  if (name.includes('partial')) {
    return done({ ...TENT, description: null, itemPrice: null, pricePerDay: null });
  }
  return done(TENT);
}
