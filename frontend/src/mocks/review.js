// 후기 API 목(REQ-034). 요청·응답 형태는 docs/openapi.json 그대로. 저장소는 대여 목과 같은 db(rentalStore.js)의 reviews.
// 검사 순서는 백엔드와 같게: 입력(만족도·칭찬·내용 길이) 400 → 대여 404 → 당사자 403 → 상태 409 → 기한 400 → 1회 409.
// 상대에게 가는 REVIEW_RECEIVED 알림은 흉내 내지 않는다(알림 목은 user1 기준이라 받는 쪽을 볼 수 없다).
import { http } from 'msw';

import { WITHDRAWN_MEMBER_ID } from './chatSeed';
import { MOCK_MEMBERS } from './item';
import { readPage, readPathId, toPageResponse } from './pageList';
import {
  currentMemberId,
  findParticipantRental,
  handle,
  invalidInput,
  reject,
  roleOf,
} from './rental';
import { db, nowServer } from './rentalStore';
import {
  buildReview,
  checkReviewBody,
  checkWriteGuard,
  listReceivedReviews,
  REVIEW_POLICY,
  toReviewResponse,
} from './reviewLogic';
import { ok } from './response';

function findWriter(memberId) {
  const member = MOCK_MEMBERS[memberId];
  return member ? { nickname: member.nickname, profileImageUrl: null } : null;
}

function memberExists(memberId) {
  return Boolean(MOCK_MEMBERS[memberId]) || memberId === WITHDRAWN_MEMBER_ID;
}

export const reviewHandlers = [
  http.post(
    '/api/rentals/:rentalId/reviews',
    handle(async ({ params, request }) => {
      const body = await request.json().catch(() => null);
      const details = checkReviewBody(body);
      if (details) {
        reject('COMMON_INVALID_INPUT', details);
      }
      const rental = findParticipantRental(params.rentalId);
      const writerId = currentMemberId();
      const code = checkWriteGuard(rental, db.reviews, writerId, Date.now());
      if (code) {
        reject(code);
      }
      const review = buildReview(body, {
        reviewId: Math.max(0, ...db.reviews.map((r) => r.reviewId)) + 1,
        rental,
        writerId,
        writerRole: roleOf(rental),
        createdAt: nowServer(),
      });
      db.reviews.push(review);
      return ok(toReviewResponse(review), { status: 201 });
    }),
  ),

  // 비회원도 볼 수 있다. 페이지 크기는 review.page 고정(size를 받지 않는다)
  http.get(
    '/api/members/:memberId/reviews',
    handle(({ params, request }) => {
      const memberId = readPathId(params.memberId);
      if (memberId == null) {
        invalidInput('memberId', '형식이 올바르지 않습니다.');
      }
      const page = readPage(new URL(request.url).searchParams.get('page'));
      if (page == null) {
        invalidInput('page', '형식이 올바르지 않습니다.');
      }
      if (!memberExists(memberId)) {
        reject('MEMBER_NOT_FOUND');
      }
      const reviews = listReceivedReviews(db.reviews, memberId, findWriter);
      return ok(toPageResponse(reviews, page, REVIEW_POLICY.PAGE));
    }),
  ),
];
