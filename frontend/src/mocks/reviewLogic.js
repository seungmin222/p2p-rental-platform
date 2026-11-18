// 후기 목(REQ-034)의 순수 함수. 저장소(rentalStore.js의 db.reviews)를 받아 계산하고 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값을 여기 따로 둔다.

// policies.md review.write-deadline-days · review.content.max · review.page
export const REVIEW_POLICY = {
  WRITE_DEADLINE_DAYS: 7,
  CONTENT_MAX: 300,
  PAGE: 20,
};

const SATISFACTIONS = ['BAD', 'GOOD', 'BEST'];
// Enum 선언 순서(응답 compliments 순서)
const COMPLIMENTS = ['ON_TIME', 'AS_DESCRIBED', 'CLEAN_USE'];

const DAY_MS = 24 * 60 * 60 * 1000;

/** 서버 일시(오프셋 없는 서울 시각) → epoch ms */
function toEpoch(serverDateTime) {
  return new Date(`${serverDateTime}+09:00`).getTime();
}

/** completedAt + 기한일 정각까지 포함 */
export function isWithinDeadline(completedAt, nowMs) {
  if (!completedAt) {
    return false;
  }
  return nowMs <= toEpoch(completedAt) + REVIEW_POLICY.WRITE_DEADLINE_DAYS * DAY_MS;
}

export function hasWritten(reviews, rentalId, writerId) {
  return reviews.some((review) => review.rentalId === rentalId && review.writerId === writerId);
}

/** RentalDetailResponse·RentalHistoryResponse의 canWriteReview */
export function canWriteReview(rental, reviews, memberId, nowMs) {
  return (
    rental.status === 'COMPLETED' &&
    isWithinDeadline(rental.completedAt, nowMs) &&
    !hasWritten(reviews, rental.rentalId, memberId)
  );
}

/**
 * 요청 본문 검사(백엔드: Bean Validation → 서비스의 내용 길이, 대여 조회보다 먼저).
 * 통과하면 null, 아니면 COMMON_INVALID_INPUT details[].
 */
export function checkReviewBody(body) {
  if (!SATISFACTIONS.includes(body?.satisfaction)) {
    return [{ field: 'satisfaction', reason: '필수 값입니다.' }];
  }
  const compliments = body.compliments ?? [];
  if (!Array.isArray(compliments) || compliments.some((c) => !COMPLIMENTS.includes(c))) {
    return [{ field: 'compliments', reason: '형식이 올바르지 않습니다.' }];
  }
  if (body.content != null && [...body.content].length > REVIEW_POLICY.CONTENT_MAX) {
    return [{ field: 'content', reason: `${REVIEW_POLICY.CONTENT_MAX}자 이하로 입력해 주세요.` }];
  }
  return null;
}

/**
 * 당사자 확인(404·403) 뒤의 가드. 순서: 상태 → 기한 → 1회. 막히면 에러 코드, 통과하면 null.
 */
export function checkWriteGuard(rental, reviews, writerId, nowMs) {
  if (rental.status !== 'COMPLETED') {
    return 'RENTAL_INVALID_TRANSITION';
  }
  if (!isWithinDeadline(rental.completedAt, nowMs)) {
    return 'REVIEW_DEADLINE_PASSED';
  }
  if (hasWritten(reviews, rental.rentalId, writerId)) {
    return 'REVIEW_ALREADY_WRITTEN';
  }
  return null;
}

/** 저장할 후기. 칭찬은 중복을 빼고 Enum 순서로, 내용은 공백만이면 null */
export function buildReview(body, { reviewId, rental, writerId, writerRole, createdAt }) {
  const picked = new Set(body.compliments ?? []);
  const content = body.content?.trim() ? body.content : null;
  return {
    reviewId,
    rentalId: rental.rentalId,
    writerId,
    targetId: writerRole === 'RENTER' ? rental.ownerId : rental.renterId,
    writerRole,
    satisfaction: body.satisfaction,
    compliments: COMPLIMENTS.filter((c) => picked.has(c)),
    content,
    createdAt,
  };
}

/** ReviewResponse */
export function toReviewResponse(review) {
  return {
    reviewId: review.reviewId,
    rentalId: review.rentalId,
    targetId: review.targetId,
    satisfaction: review.satisfaction,
    compliments: review.compliments,
    content: review.content,
    createdAt: review.createdAt,
  };
}

/**
 * 받은 후기(ReceivedReviewResponse 배열), 최신 작성 순(같으면 id 역순).
 * findWriter(memberId)는 { nickname, profileImageUrl } 또는 탈퇴면 null.
 */
export function listReceivedReviews(reviews, memberId, findWriter) {
  return reviews
    .filter((review) => review.targetId === memberId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.reviewId - a.reviewId)
    .map((review) => {
      const writer = findWriter(review.writerId);
      return {
        reviewId: review.reviewId,
        writer: {
          memberId: review.writerId,
          nickname: writer?.nickname ?? null,
          profileImageUrl: writer?.profileImageUrl ?? null,
          isWithdrawn: writer == null,
        },
        writerRole: review.writerRole,
        satisfaction: review.satisfaction,
        compliments: review.compliments,
        content: review.content,
        createdAt: review.createdAt,
      };
    });
}
