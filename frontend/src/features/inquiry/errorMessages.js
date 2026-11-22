// 문의·분쟁 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다(HTTP 메시지 문자열로 분기하지 않는다).
// 키는 docs/openapi.json POST·GET /api/inquiries 응답 예시의 코드 전부.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '내용을 확인해 주세요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  RENTAL_NOT_PARTICIPANT: '이 대여의 대여자·제공자만 접수할 수 있어요',
  RENTAL_NOT_FOUND: '대여를 찾을 수 없어요',
  INQUIRY_DISPUTE_NOT_ALLOWED: '결제 전 대여는 분쟁을 접수할 수 없어요',
  INQUIRY_DISPUTE_ALREADY_OPEN:
    '이 대여에 처리 중인 분쟁이 있어요. 내 문의에서 처리 상태를 확인해 주세요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);

/** 이미 열린 분쟁: 폼 안에 "내 문의 보기" 링크를 함께 보인다 */
export function isDisputeAlreadyOpenError(error) {
  return error?.code === 'INQUIRY_DISPUTE_ALREADY_OPEN';
}

/** 대여 상태가 화면과 달라 거절(결제 대기로 보이지 않았는데 결제 대기): 대여 상세를 다시 불러온다 */
export function isDisputeNotAllowedError(error) {
  return error?.code === 'INQUIRY_DISPUTE_NOT_ALLOWED';
}
