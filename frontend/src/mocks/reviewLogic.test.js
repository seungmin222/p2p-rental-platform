import { describe, expect, it } from 'vitest';

import {
  buildReview,
  canWriteReview,
  checkReviewBody,
  checkWriteGuard,
  isWithinDeadline,
  listReceivedReviews,
} from './reviewLogic';

const NOW = new Date('2026-11-17T12:00:00+09:00').getTime();
const completed = {
  rentalId: 1,
  renterId: 1,
  ownerId: 2,
  status: 'COMPLETED',
  completedAt: '2026-11-15T09:00:00',
};

describe('isWithinDeadline', () => {
  it('완료 후 7일 정각까지 포함', () => {
    const deadline = new Date('2026-11-17T09:00:00+09:00').getTime();
    expect(isWithinDeadline('2026-11-10T09:00:00', deadline)).toBe(true);
    expect(isWithinDeadline('2026-11-10T09:00:00', deadline + 1)).toBe(false);
    expect(isWithinDeadline(null, NOW)).toBe(false);
  });
});

describe('canWriteReview', () => {
  it('완료·기한 안·미작성이면 true', () => {
    expect(canWriteReview(completed, [], 1, NOW)).toBe(true);
  });

  it('이미 썼거나 완료가 아니면 false', () => {
    expect(canWriteReview(completed, [{ rentalId: 1, writerId: 1 }], 1, NOW)).toBe(false);
    // 상대가 쓴 후기는 내 작성 여부와 무관
    expect(canWriteReview(completed, [{ rentalId: 1, writerId: 2 }], 1, NOW)).toBe(true);
    expect(canWriteReview({ ...completed, status: 'RETURNED' }, [], 1, NOW)).toBe(false);
  });
});

describe('checkReviewBody', () => {
  it('만족도는 필수, 칭찬은 목록 안의 값만', () => {
    expect(checkReviewBody({})[0].field).toBe('satisfaction');
    expect(checkReviewBody({ satisfaction: 'GOOD', compliments: ['NOPE'] })[0].field).toBe(
      'compliments',
    );
    expect(checkReviewBody({ satisfaction: 'GOOD' })).toBeNull();
  });

  it('내용은 코드 포인트로 300자까지', () => {
    expect(checkReviewBody({ satisfaction: 'BEST', content: '😀'.repeat(300) })).toBeNull();
    expect(checkReviewBody({ satisfaction: 'BEST', content: 'a'.repeat(301) })[0].field).toBe(
      'content',
    );
  });
});

describe('checkWriteGuard', () => {
  it('상태 → 기한 → 1회 순서', () => {
    expect(checkWriteGuard({ ...completed, status: 'RENTING' }, [], 1, NOW)).toBe(
      'RENTAL_INVALID_TRANSITION',
    );
    const old = { ...completed, completedAt: '2026-11-01T09:00:00' };
    // 기한이 지났으면 이미 썼어도 기한 오류
    expect(checkWriteGuard(old, [{ rentalId: 1, writerId: 1 }], 1, NOW)).toBe(
      'REVIEW_DEADLINE_PASSED',
    );
    expect(checkWriteGuard(completed, [{ rentalId: 1, writerId: 1 }], 1, NOW)).toBe(
      'REVIEW_ALREADY_WRITTEN',
    );
    expect(checkWriteGuard(completed, [], 1, NOW)).toBeNull();
  });
});

describe('buildReview', () => {
  it('대상은 상대 당사자, 칭찬은 Enum 순서·중복 제거, 공백 내용은 null', () => {
    const review = buildReview(
      { satisfaction: 'GOOD', compliments: ['CLEAN_USE', 'ON_TIME', 'CLEAN_USE'], content: '  ' },
      { reviewId: 9, rental: completed, writerId: 1, writerRole: 'RENTER', createdAt: 'x' },
    );
    expect(review).toMatchObject({
      targetId: 2,
      compliments: ['ON_TIME', 'CLEAN_USE'],
      content: null,
    });
    const byOwner = buildReview(
      { satisfaction: 'BAD' },
      { reviewId: 10, rental: completed, writerId: 2, writerRole: 'OWNER', createdAt: 'x' },
    );
    expect(byOwner).toMatchObject({ targetId: 1, compliments: [] });
  });
});

describe('listReceivedReviews', () => {
  it('받은 것만 최신순, 탈퇴 작성자는 닉네임·사진 null', () => {
    const reviews = [
      { reviewId: 1, targetId: 1, writerId: 2, createdAt: '2026-11-01T10:00:00' },
      { reviewId: 2, targetId: 1, writerId: 98, createdAt: '2026-11-03T10:00:00' },
      { reviewId: 3, targetId: 2, writerId: 1, createdAt: '2026-11-04T10:00:00' },
    ];
    const list = listReceivedReviews(reviews, 1, (id) =>
      id === 2 ? { nickname: '캠핑조아' } : null,
    );
    expect(list.map((r) => r.reviewId)).toEqual([2, 1]);
    expect(list[0].writer).toEqual({
      memberId: 98,
      nickname: null,
      profileImageUrl: null,
      isWithdrawn: true,
    });
    expect(list[1].writer.nickname).toBe('캠핑조아');
  });
});
