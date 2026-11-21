// 약관 화면·재동의 오류 문구(code → 문구).
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);
