// 후기 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다(HTTP 메시지 문자열로 분기하지 않는다).
// 키는 docs/openapi.json 후기 작성·받은 후기 목록 응답 예시의 코드 전부.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';
import { REVIEW_WRITE_DEADLINE_DAYS } from './constants';

const MESSAGES = {
  COMMON_INVALID_INPUT: '입력한 내용을 확인해 주세요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  RENTAL_NOT_FOUND: '대여 정보를 볼 수 없어요',
  RENTAL_NOT_PARTICIPANT: '대여 정보를 볼 수 없어요',
  RENTAL_INVALID_TRANSITION: '거래가 완료된 대여에만 후기를 쓸 수 있어요',
  REVIEW_ALREADY_WRITTEN: '이미 후기를 보냈어요',
  REVIEW_DEADLINE_PASSED: `후기 작성 기간(거래 완료 후 ${REVIEW_WRITE_DEADLINE_DAYS}일)이 지났어요`,
  MEMBER_NOT_FOUND: '회원을 찾을 수 없어요',
};

// 다시 써도 통과할 수 없는 거절. 토스트로 알리고 대여 상세를 다시 불러와 화면을 "쓸 수 없음"으로 바꾼다(queries.js)
const BLOCKED_CODES = [
  'RENTAL_NOT_FOUND',
  'RENTAL_NOT_PARTICIPANT',
  'RENTAL_INVALID_TRANSITION',
  'REVIEW_ALREADY_WRITTEN',
  'REVIEW_DEADLINE_PASSED',
];

/** code에 맞는 문구. 모르는 code면 ApiError.message(CLIENT_* 포함 이미 한국어)를 쓴다. */
export const getErrorMessage = createErrorMessageGetter(MESSAGES);

export function isReviewBlockedError(error) {
  return BLOCKED_CODES.includes(error?.code);
}
