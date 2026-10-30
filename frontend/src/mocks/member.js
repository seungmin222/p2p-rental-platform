// 회원 목(REQ-001, 내 정보, REQ-004 내 동네). 가입한 계정·내 동네 변경은 메모리(memberDb)에만 있어 새로고침하면 사라진다.
// 내 동네 API는 members/me처럼 현재 AT가 아니면 401. members/me의 primaryRegion도 같은 db에서 계산해 바로 반영된다.
import { delay, http } from 'msw';

import { findSignupConflict, toMemberResponse, validateSignup } from './memberLogic';
import { memberDb, MOCK_REGION_MASTER } from './memberSeed';
import { readPathId } from './pageList';
import {
  addMemberRegion,
  deleteMemberRegion,
  listMemberRegions,
  readCoord,
  setPrimaryRegion,
  toMemberRegionResponse,
  verifyMemberRegion,
} from './regionLogic';
import { fail, ok } from './response';
import { toServerDateTime } from './rentalSeed';
import { authenticateMockRequest } from './session';

const CONFLICT_MESSAGES = {
  MEMBER_EMAIL_DUPLICATED: '이미 가입된 이메일입니다.',
  MEMBER_NICKNAME_DUPLICATED: '이미 사용 중인 닉네임입니다.',
};

// 내 동네 API 오류 문구(openapi 응답 예시)
const REGION_ERROR_MESSAGES = {
  COMMON_INVALID_INPUT: '입력값이 올바르지 않습니다.',
  COMMON_NOT_FOUND: '요청한 대상을 찾을 수 없습니다.',
  REGION_ALREADY_ADDED: '이미 추가한 동네입니다.',
  REGION_LIMIT_EXCEEDED: '더 이상 동네를 추가할 수 없습니다. 기존 동네를 삭제하세요.',
  REGION_VERIFY_MISMATCH: '현재 위치가 선택한 동네와 다릅니다.',
};

function unauthorized() {
  return fail(401, 'AUTH_UNAUTHORIZED', '로그인이 필요합니다.');
}

function regionError({ status, code, details = [] }) {
  return fail(status, code, REGION_ERROR_MESSAGES[code], details);
}

// 경로 id가 Long이 아니면 타입 변환 실패 400(details 없음)
const INVALID_PATH_ID = { status: 400, code: 'COMMON_INVALID_INPUT' };
// 요청 본문 검증 실패 400(details를 붙여 쓴다)
const INVALID_BODY = { status: 400, code: 'COMMON_INVALID_INPUT' };
const NOT_FOUND = { status: 404, code: 'COMMON_NOT_FOUND' };

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

  http.get('/api/members/me/regions', async ({ request }) => {
    await delay(300);
    const member = authenticateMockRequest(request);
    if (!member) {
      return unauthorized();
    }
    return ok(listMemberRegions(memberDb, member.memberId, MOCK_REGION_MASTER));
  }),

  http.post('/api/members/me/regions', async ({ request }) => {
    await delay(400);
    const member = authenticateMockRequest(request);
    if (!member) {
      return unauthorized();
    }
    const body = await request.json().catch(() => null);
    const result = addMemberRegion(memberDb, member.memberId, body?.regionId, MOCK_REGION_MASTER);
    if (result.error) {
      return regionError(result.error);
    }
    return ok(toMemberRegionResponse(result.memberRegion, MOCK_REGION_MASTER), { status: 201 });
  }),

  http.patch('/api/members/me/regions/:memberRegionId/verify', async ({ request, params }) => {
    await delay(400);
    const member = authenticateMockRequest(request);
    if (!member) {
      return unauthorized();
    }
    const memberRegionId = readPathId(params.memberRegionId);
    if (memberRegionId === null) {
      return regionError(INVALID_PATH_ID);
    }
    const { coord, details } = readCoord(await request.json().catch(() => null));
    if (details) {
      return regionError({ ...INVALID_BODY, details });
    }
    const result = verifyMemberRegion(
      memberDb,
      member.memberId,
      memberRegionId,
      coord,
      MOCK_REGION_MASTER,
    );
    if (result.error) {
      return regionError(result.error);
    }
    return ok(toMemberRegionResponse(result.memberRegion, MOCK_REGION_MASTER));
  }),

  http.patch('/api/members/me/regions/:memberRegionId/primary', async ({ request, params }) => {
    await delay(400);
    const member = authenticateMockRequest(request);
    if (!member) {
      return unauthorized();
    }
    const memberRegionId = readPathId(params.memberRegionId);
    if (memberRegionId === null) {
      return regionError(INVALID_PATH_ID);
    }
    if (!setPrimaryRegion(memberDb, member.memberId, memberRegionId)) {
      return regionError(NOT_FOUND);
    }
    return ok(listMemberRegions(memberDb, member.memberId, MOCK_REGION_MASTER));
  }),

  http.delete('/api/members/me/regions/:memberRegionId', async ({ request, params }) => {
    await delay(400);
    const member = authenticateMockRequest(request);
    if (!member) {
      return unauthorized();
    }
    const memberRegionId = readPathId(params.memberRegionId);
    if (memberRegionId === null) {
      return regionError(INVALID_PATH_ID);
    }
    if (!deleteMemberRegion(memberDb, member.memberId, memberRegionId)) {
      return regionError(NOT_FOUND);
    }
    return ok(null);
  }),
];
