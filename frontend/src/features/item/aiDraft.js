// AI 등록 보조(REQ-067) 순수 함수: 응답 → 채울 칸, 결과 안내 문구. 폼 값 모양은 itemForm.js createItemFormValues.
import { ITEM_IMAGES } from './constants';
import { getErrorMessage } from './errorMessages';

/** 폼 칸 ← 응답 필드. 금액 칸은 문자열로 들고 있다(itemForm.js). 상태(condition)·보증금은 채우지 않는다 */
const DRAFT_FIELDS = [
  { name: 'categoryId', pick: (draft) => draft.categoryId },
  { name: 'name', pick: (draft) => draft.title },
  { name: 'description', pick: (draft) => draft.description },
  { name: 'pricePerDay', pick: (draft) => draft.pricePerDay, toValue: String },
  { name: 'itemPrice', pick: (draft) => draft.itemPrice, toValue: String },
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

/**
 * DONE 응답에서 채울 칸. null 필드와 사용자가 고친 칸은 건너뛴다.
 * @param {object} draft AiDraftResponse
 * @param {{ current: object, initial: object, aiFilled: object }} values 지금 폼 값·처음 값·AI가 넣은 값
 * @returns {{ name: string, value: unknown }[]}
 */
export function getDraftFills(draft, { current, initial, aiFilled }) {
  if (draft.status !== 'DONE') {
    return [];
  }
  return DRAFT_FIELDS.flatMap(({ name, pick, toValue }) => {
    const raw = pick(draft);
    if (raw == null || isUserEdited(current[name], initial[name], aiFilled[name])) {
      return [];
    }
    return [{ name, value: toValue ? toValue(raw) : raw }];
  });
}

/**
 * 결과 안내. ETC면 카테고리 문구를 덧붙인다.
 * @returns {{ tone: 'surface' | 'accent', messages: string[] }}
 */
export function getDraftNotice(draft) {
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
  return { tone: 'surface', messages };
}

/** 요청 실패 안내. 사진 오류(400)는 기존 사진 문구, 그 밖은 FAILED와 같다 */
export function getDraftErrorNotice(error) {
  if (IMAGE_ERROR_CODES.includes(error?.code)) {
    return { tone: 'accent', messages: [getErrorMessage(error)] };
  }
  return getDraftNotice({ status: 'FAILED' });
}
