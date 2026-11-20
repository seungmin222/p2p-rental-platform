// 사용자 프로필 목(REQ-021). 응답 형태는 openapi.json MemberProfileResponse · PageResponse<ItemSummaryResponse>와 같다.
// 받은 후기(/api/members/:memberId/reviews)는 review.js. 비회원도 볼 수 있어 AT를 보지 않는다.
// /api/members/me/**(내 정보·내 물품)와 경로가 겹치므로 handlers.js 맨 끝에 둔다(memberId가 숫자가 아니면 400).
// 탈퇴 회원은 채팅·후기 목과 같은 98번: 프로필은 isWithdrawn만, 등록 물품은 빈 목록.
import { delay, http } from 'msw';

import { WITHDRAWN_MEMBER_ID } from './chatSeed';
import { toSummary } from './feed';
import { listMockItems } from './item';
import { compareLatest } from './itemLogic';
import { memberDb, MOCK_REGION_MASTER } from './memberSeed';
import { readPage, readPathId, toPageResponse } from './pageList';
import { db } from './rentalStore';
import { fail, ok } from './response';

// policies.md profile-items.page
const PROFILE_ITEMS_PAGE = 20;

function invalidPathId() {
  return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', [
    { field: 'memberId', reason: '형식이 올바르지 않습니다.' },
  ]);
}

function notFound() {
  return fail(404, 'MEMBER_NOT_FOUND', '회원을 찾을 수 없습니다.');
}

function findMember(memberId) {
  return memberDb.members.find((member) => member.memberId === memberId) ?? null;
}

function toProfile(member) {
  const primary = memberDb.memberRegions.find(
    (item) => item.memberId === member.memberId && item.isPrimary,
  );
  const region = primary && MOCK_REGION_MASTER.find((item) => item.regionId === primary.regionId);
  return {
    memberId: member.memberId,
    nickname: member.nickname,
    profileImageUrl: member.profileImageUrl,
    regionName: region?.name ?? null,
    joinedAt: member.createdAt,
    completedRentalCount: db.rentals.filter(
      (rental) =>
        rental.status === 'COMPLETED' &&
        (rental.renterId === member.memberId || rental.ownerId === member.memberId),
    ).length,
    isWithdrawn: false,
  };
}

const WITHDRAWN_PROFILE = {
  memberId: WITHDRAWN_MEMBER_ID,
  nickname: null,
  profileImageUrl: null,
  regionName: null,
  joinedAt: null,
  completedRentalCount: null,
  isWithdrawn: true,
};

export const profileHandlers = [
  http.get('/api/members/:memberId', async ({ params }) => {
    await delay(300);
    const memberId = readPathId(params.memberId);
    if (memberId == null) {
      return invalidPathId();
    }
    if (memberId === WITHDRAWN_MEMBER_ID) {
      return ok(WITHDRAWN_PROFILE);
    }
    const member = findMember(memberId);
    return member ? ok(toProfile(member)) : notFound();
  }),

  // AVAILABLE만(삭제·숨김 제외), bumpedAt·id 내림차순, 20건 고정. 대여 중인 물품도 AVAILABLE이라 포함된다
  http.get('/api/members/:memberId/items', async ({ params, request }) => {
    await delay(300);
    const memberId = readPathId(params.memberId);
    if (memberId == null) {
      return invalidPathId();
    }
    const page = readPage(new URL(request.url).searchParams.get('page'));
    if (page == null) {
      return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', [
        { field: 'page', reason: '형식이 올바르지 않습니다.' },
      ]);
    }
    if (memberId !== WITHDRAWN_MEMBER_ID && !findMember(memberId)) {
      return notFound();
    }
    const items = listMockItems()
      .filter((item) => item.ownerId === memberId && !item.isDeleted)
      .sort(compareLatest)
      .map(toSummary);
    return ok(toPageResponse(items, page, PROFILE_ITEMS_PAGE));
  }),
];
