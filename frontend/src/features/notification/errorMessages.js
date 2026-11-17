// 알림 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다. 키는 docs/openapi.json 알림 API 응답 예시의 코드.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '알림을 불러오지 못했어요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  NOTIFICATION_NOT_FOUND: '알림을 찾을 수 없어요',
  NOTIFICATION_NOT_OWNER: '본인의 알림만 볼 수 있어요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);
