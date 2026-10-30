// 물품 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다.
// 키는 docs/openapi.json 물품 API 응답 예시의 코드. 등록·수정·삭제 오류(ITEM_NOT_OWNER 등)는 feat/fe-item-form에서 더한다.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '요청을 처리하지 못했어요. 다시 시도해 주세요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  ITEM_NOT_FOUND: '삭제됐거나 찾을 수 없는 물품이에요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);
