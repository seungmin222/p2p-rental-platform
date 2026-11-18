import { describe, expect, it } from 'vitest';

import { COMPLIMENT, REVIEW_CONTENT_MAX } from './constants';
import {
  getReviewBlockReason,
  isRentalUnavailable,
  toReviewRequest,
  validateReviewContent,
} from './reviewData';

const ORDER = Object.keys(COMPLIMENT);

describe('getReviewBlockReason', () => {
  it('쓸 수 있으면 null', () => {
    expect(getReviewBlockReason({ status: 'COMPLETED', canWriteReview: true })).toBeNull();
  });

  it('이미 썼으면 WRITTEN', () => {
    expect(
      getReviewBlockReason({ status: 'COMPLETED', canWriteReview: false, reviewWritten: true }),
    ).toBe('WRITTEN');
  });

  it('완료인데 못 쓰고 안 썼으면 기한 지남, 완료 전이면 NOT_COMPLETED', () => {
    expect(
      getReviewBlockReason({ status: 'COMPLETED', canWriteReview: false, reviewWritten: false }),
    ).toBe('DEADLINE_PASSED');
    expect(
      getReviewBlockReason({ status: 'RETURNED', canWriteReview: false, reviewWritten: false }),
    ).toBe('NOT_COMPLETED');
  });
});

describe('validateReviewContent', () => {
  it('코드 포인트로 센다(이모지 1자)', () => {
    expect(validateReviewContent('😀'.repeat(REVIEW_CONTENT_MAX))).toBe(true);
    expect(validateReviewContent('a'.repeat(REVIEW_CONTENT_MAX + 1))).toMatch(/이하/);
    expect(validateReviewContent('')).toBe(true);
  });
});

describe('toReviewRequest', () => {
  it('칭찬은 목록 순서, 공백 내용은 빼고 앞뒤 공백은 자른다', () => {
    expect(
      toReviewRequest(
        { satisfaction: 'BEST', compliments: ['CLEAN_USE', 'ON_TIME'], content: '  ' },
        ORDER,
      ),
    ).toEqual({ satisfaction: 'BEST', compliments: ['ON_TIME', 'CLEAN_USE'] });
    expect(
      toReviewRequest({ satisfaction: 'BAD', compliments: [], content: ' 별로 ' }, ORDER),
    ).toEqual({ satisfaction: 'BAD', compliments: [], content: ' 별로 ' });
  });
});

describe('isRentalUnavailable', () => {
  it('없는 대여·당사자 아님 실패면 데이터가 있어도 true', () => {
    for (const code of ['RENTAL_NOT_FOUND', 'RENTAL_NOT_PARTICIPANT']) {
      expect(isRentalUnavailable({ isError: true, error: { code }, data: undefined })).toBe(true);
      expect(isRentalUnavailable({ isError: true, error: { code }, data: { rentalId: 1 } })).toBe(
        true,
      );
    }
  });

  it('다른 실패나 성공이면 false', () => {
    expect(
      isRentalUnavailable({ isError: true, error: { code: 'CLIENT_NETWORK' }, data: undefined }),
    ).toBe(false);
    expect(isRentalUnavailable({ isError: false, error: null, data: { rentalId: 1 } })).toBe(false);
  });
});
