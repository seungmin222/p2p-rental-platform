// AI 등록 보조(REQ-067) 순수 함수: 응답 → 채울 칸, 결과 안내 문구. 폼 값 모양은 itemForm.js createItemFormValues.
import { ITEM_DEPOSIT, ITEM_IMAGES, ITEM_MIN_PAID_AMOUNT } from './constants';
import { getErrorMessage } from './errorMessages';

/**
 * 보증금 칸에 넣을 값: 권장 범위 하한(minDeposit)을 item.deposit(0원 또는 1,000원 ~ 3,000,000원)에 맞춘다.
 * 0원은 그대로, 0 초과 1,000원 미만은 1,000원으로 올리고, 상한을 넘으면 자른다. null이면 null.
 */
export function getDraftDeposit(minDeposit) {
  if (minDeposit == null || minDeposit === 0) {
    return minDeposit;
  }
  return Math.min(Math.max(minDeposit, ITEM_MIN_PAID_AMOUNT), ITEM_DEPOSIT.MAX);
}

/** 폼 칸 ← 응답 필드. 금액 칸은 문자열로 들고 있다(itemForm.js). 상태(condition)는 채우지 않는다 */
const DRAFT_FIELDS = [
  { name: 'categoryId', pick: (draft) => draft.categoryId },
  { name: 'name', pick: (draft) => draft.title },
  { name: 'description', pick: (draft) => draft.description },
  { name: 'pricePerDay', pick: (draft) => draft.pricePerDay, toValue: String },
  { name: 'itemPrice', pick: (draft) => draft.itemPrice, toValue: String },
  { name: 'deposit', pick: (draft) => getDraftDeposit(draft.minDeposit), toValue: String },
];

export const AI_DRAFT_FIELD_NAMES = DRAFT_FIELDS.map((field) => field.name);

const IMAGE_ERROR_CODES = ['IMAGE_COUNT_INVALID', 'IMAGE_INVALID_TYPE', 'IMAGE_SIZE_EXCEEDED'];

/** 보낼 사진: 이번에 고른 새 파일만 화면 순서대로(백엔드도 item.images 장수를 본다) */
export function getDraftFiles(images) {
  return images
    .filter((image) => image.file)
    .map((image) => image.file)
    .slice(0, ITEM_IMAGES.MAX_COUNT);
}

/**
 * 사용자가 고친 칸인지. 처음 값과 다르고, AI가 마지막으로 넣은 값과도 다르면 사용자가 손댄 것이다.
 * react-hook-form isDirty 대신 값으로 본다: AI가 넣은 칸은 다시 누르면 새 값으로 바꿔야 하기 때문이다.
 */
export function isUserEdited(current, initial, aiFilled) {
  return current !== initial && current !== aiFilled;
}

/** 보증금 하한은 AI 원가·카테고리 기준이라, 사용자가 둘 중 하나를 고쳤으면 범위 안내와 어긋나 채우지 않는다 */
const DEPOSIT_BASIS_FIELDS = ['itemPrice', 'categoryId'];

/**
 * DONE 응답에서 채울 칸. null 필드와 사용자가 고친 칸은 건너뛴다.
 * 물품 가격·카테고리를 사용자가 고쳤으면 보증금도 건너뛴다(범위 안내만 남는다).
 * @param {object} draft AiDraftResponse
 * @param {{ current: object, initial: object, aiFilled: object }} values 지금 폼 값·처음 값·AI가 넣은 값
 * @returns {{ name: string, value: unknown }[]}
 */
export function getDraftFills(draft, { current, initial, aiFilled }) {
  if (draft.status !== 'DONE') {
    return [];
  }
  const edited = (name) => isUserEdited(current[name], initial[name], aiFilled[name]);
  const depositBasisEdited = DEPOSIT_BASIS_FIELDS.some(edited);
  return DRAFT_FIELDS.flatMap(({ name, pick, toValue }) => {
    const raw = pick(draft);
    if (raw == null || edited(name) || (name === 'deposit' && depositBasisEdited)) {
      return [];
    }
    return [{ name, value: toValue ? toValue(raw) : raw }];
  });
}

/** 보증금을 채웠을 때 덧붙일 문구. 올리거나 자른 값은 권장 범위 밖이라 따로 알린다 */
function getDepositNotice(minDeposit) {
  if (minDeposit > 0 && minDeposit < ITEM_MIN_PAID_AMOUNT) {
    return `보증금은 등록할 수 있는 최소 금액(${ITEM_MIN_PAID_AMOUNT.toLocaleString()}원)으로 넣었어요`;
  }
  if (minDeposit > ITEM_DEPOSIT.MAX) {
    return `보증금은 등록 상한(${ITEM_DEPOSIT.MAX.toLocaleString()}원)으로 넣었어요`;
  }
  return '보증금은 권장 범위에서 가장 낮은 금액으로 넣었어요';
}

/**
 * 결과 안내. ETC면 카테고리 문구를, 보증금을 채웠으면 보증금 문구를 덧붙인다.
 * @param {object} draft AiDraftResponse
 * @param {{ name: string }[]} [fills] getDraftFills 결과(실제로 채운 칸)
 * @returns {{ tone: 'surface' | 'accent', messages: string[] }}
 */
export function getDraftNotice(draft, fills = []) {
  if (draft.status === 'LIMIT_EXCEEDED') {
    return { tone: 'accent', messages: ['오늘 AI 사용 횟수를 모두 썼어요. 직접 입력해 주세요'] };
  }
  if (draft.status !== 'DONE') {
    return { tone: 'accent', messages: ['AI 초안을 만들지 못했어요. 직접 입력해 주세요'] };
  }
  const messages = ['AI가 초안을 채웠어요. 내용을 확인하고 고쳐 주세요'];
  if (draft.categoryCode === 'ETC') {
    messages.push(
      draft.suggestedCategoryName
        ? `맞는 카테고리가 없어 '기타'로 골랐어요. AI가 떠올린 분류: ${draft.suggestedCategoryName}`
        : "맞는 카테고리가 없어 '기타'로 골랐어요",
    );
  }
  if (fills.some((fill) => fill.name === 'deposit')) {
    messages.push(getDepositNotice(draft.minDeposit));
  }
  return { tone: 'surface', messages };
}

/** 요청 실패 안내. 사진 오류(400)는 기존 사진 문구, 그 밖은 FAILED와 같다 */
export function getDraftErrorNotice(error) {
  if (IMAGE_ERROR_CODES.includes(error?.code)) {
    return { tone: 'accent', messages: [getErrorMessage(error)] };
  }
  return getDraftNotice({ status: 'FAILED' });
}
