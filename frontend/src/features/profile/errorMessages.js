// 사용자 프로필 화면의 오류 문구(code → 문구). 키는 docs/openapi.json 프로필·등록 물품 응답 예시의 코드 전부.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '사용자를 찾을 수 없어요',
  MEMBER_NOT_FOUND: '사용자를 찾을 수 없어요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);
