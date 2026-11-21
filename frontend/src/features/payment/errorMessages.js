// 결제·정산 내역 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다(HTTP 메시지 문자열로 분기하지 않는다).
// 키는 docs/openapi.json GET /api/members/me/payments·/settlements 응답 예시의 코드 전부.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '조회 조건을 확인해 주세요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);

/** from > to 400(details field to). 기간 시트가 미리 막지만, 오늘 날짜가 서버와 엇갈리면 올 수 있다 */
export function isRangeError(error) {
  return (
    error?.code === 'COMMON_INVALID_INPUT' &&
    (error.details ?? []).some((detail) => detail.field === 'from' || detail.field === 'to')
  );
}
