// 회원 목(REQ-001, 내 정보). 내 동네 API 목(/api/members/me/regions)은 feat/fe-region에서 이 파일에 더한다.
// 가입한 계정은 메모리(memberDb)에만 있어 새로고침하면 사라진다(그 세션은 재발급 401).
import { delay, http } from 'msw';

import { findSignupConflict, toMemberResponse, validateSignup } from './memberLogic';
import { memberDb, MOCK_REGION_MASTER } from './memberSeed';
import { fail, ok } from './response';
import { toServerDateTime } from './rentalSeed';
import { authenticateMockRequest } from './session';

const CONFLICT_MESSAGES = {
  MEMBER_EMAIL_DUPLICATED: '이미 가입된 이메일입니다.',
  MEMBER_NICKNAME_DUPLICATED: '이미 사용 중인 닉네임입니다.',
};

function unauthorized() {
  return fail(401, 'AUTH_UNAUTHORIZED', '로그인이 필요합니다.');
}

export const memberHandlers = [
  http.post('/api/members', async ({ request }) => {
    await delay(400);
    const body = await request.json().catch(() => null);
    const details = validateSignup(body);
    if (details.length > 0) {
      return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', details);
    }
    const conflict = findSignupConflict(memberDb.members, body);
    if (conflict) {
      return fail(409, conflict, CONFLICT_MESSAGES[conflict]);
    }
    const memberId = memberDb.nextMemberId++;
    memberDb.members.push({
      memberId,
      email: body.email,
      password: body.password,
      nickname: body.nickname,
      profileImageUrl: null,
      role: 'USER',
      createdAt: toServerDateTime(new Date()),
    });
    return ok({ memberId }, { status: 201 });
  }),

  http.get('/api/members/me', ({ request }) => {
    const member = authenticateMockRequest(request);
    if (!member) {
      return unauthorized();
    }
    return ok(toMemberResponse(memberDb, member.memberId, MOCK_REGION_MASTER));
  }),
];
