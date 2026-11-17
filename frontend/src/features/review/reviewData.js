// 후기 화면 순수 함수(테스트 있음).
import { countCodePoints } from '../../shared/lib/validators';
import { REVIEW_CONTENT_MAX } from './constants';

/**
 * 후기 작성 화면을 막는 이유. 작성할 수 있으면 null.
 * 판단은 서버 값(canWriteReview·reviewWritten)만 쓴다. 기한은 프론트가 다시 계산하지 않는다.
 * @param {object} rental RentalDetailResponse
 * @returns {'WRITTEN' | 'DEADLINE_PASSED' | 'NOT_COMPLETED' | null}
 */
export function getReviewBlockReason(rental) {
  if (rental.canWriteReview) {
    return null;
  }
  if (rental.reviewWritten) {
    return 'WRITTEN';
  }
  // 완료됐는데 쓸 수 없고 쓰지도 않았으면 기한이 지난 것이다
  return rental.status === 'COMPLETED' ? 'DEADLINE_PASSED' : 'NOT_COMPLETED';
}

/** 내용(선택): 코드 포인트로 REVIEW_CONTENT_MAX자 이하 */
export function validateReviewContent(text) {
  return (
    countCodePoints(text) <= REVIEW_CONTENT_MAX || `${REVIEW_CONTENT_MAX}자 이하로 입력해 주세요`
  );
}

/**
 * 폼 값 → ReviewCreateRequest. 내용이 공백뿐이면 보내지 않는다(서버도 내용 없음으로 저장).
 * 칭찬은 고른 순서와 상관없이 목록 순서로 보낸다.
 */
export function toReviewRequest({ satisfaction, compliments, content }, complimentOrder) {
  const body = {
    satisfaction,
    compliments: complimentOrder.filter((code) => compliments.includes(code)),
  };
  const trimmed = content.trim();
  if (trimmed) {
    body.content = trimmed;
  }
  return body;
}
