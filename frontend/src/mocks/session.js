// 목 로그인 세션. RT 쿠키(HttpOnly, 새로고침해도 남음)를 흉내 내려고 로그인한 memberId를 localStorage 한 키에 둔다.
// 개발 서버 + VITE_USE_MOCK에서만 쓰는 목 코드라 localStorage를 써도 된다. 앱 코드의 "AT는 메모리에만" 규칙과는
// 무관하다(여기 두는 것은 AT가 아니라 서버가 아는 세션이고, 목 AT 자체는 아래 메모리 변수에만 있다).
import { readBearerToken, toMockAccessToken } from './memberLogic';
import { memberDb } from './memberSeed';

const SESSION_KEY = 'mock.session';

// 현재 유효한 AT. 새로고침하면 사라지고 앱 시작 재발급으로 새로 받는다
let currentAccessToken = null;
let accessTokenSequence = 0;

function readSessionMemberId() {
  try {
    const memberId = JSON.parse(localStorage.getItem(SESSION_KEY))?.memberId;
    return Number.isInteger(memberId) ? memberId : null;
  } catch {
    return null;
  }
}

function findMember(memberId) {
  return memberDb.members.find((member) => member.memberId === memberId) ?? null;
}

/** 새 AT를 발급해 현재 AT로 둔다 */
function issueAccessToken(memberId) {
  accessTokenSequence += 1;
  currentAccessToken = toMockAccessToken(memberId, accessTokenSequence);
  return currentAccessToken;
}

/** 로그인: 세션을 만들고 AT 발급 */
export function startMockSession(memberId) {
  try {
    localStorage.setItem(SESSION_KEY, JSON.stringify({ memberId }));
  } catch {
    // 저장소를 못 쓰면 새로고침 때 로그인이 풀릴 뿐이다
  }
  return issueAccessToken(memberId);
}

/** 로그아웃: 세션·AT 삭제. 세션이 없어도 성공(백엔드와 같음) */
export function endMockSession() {
  currentAccessToken = null;
  try {
    localStorage.removeItem(SESSION_KEY);
  } catch {
    // 지울 것이 없으면 그대로
  }
}

/**
 * 재발급: 세션이 있고 그 회원이 목에 있으면 새 AT, 아니면 null(401 AUTH_REFRESH_TOKEN_INVALID).
 * 가입한 계정은 메모리에만 있어 새로고침 뒤에는 회원이 없으므로 null이 된다.
 */
export function reissueMockSession() {
  const memberId = readSessionMemberId();
  if (memberId === null || !findMember(memberId)) {
    endMockSession();
    return null;
  }
  return issueAccessToken(memberId);
}

/**
 * 보호 API 목의 인증: Authorization이 현재 AT면 회원, 아니면 null(401 AUTH_UNAUTHORIZED).
 * 보호 API 목(members/me)과 물품 상세의 isOwner, 대여·결제 목의 "나"가 쓴다(대여 목은 AT가 없으면 MOCK_ME로 본다).
 */
export function authenticateMockRequest(request) {
  const token = readBearerToken(request.headers.get('Authorization'));
  if (token === null || token !== currentAccessToken) {
    return null;
  }
  return findMember(Number(token.split('.')[1]));
}

/** 개발 확인용: 현재 AT만 무효로 만든다(세션은 유지). 다음 보호 API가 401 → 재발급 1회 → 재시도되는지 볼 때 */
export function expireMockAccessToken() {
  currentAccessToken = null;
}
