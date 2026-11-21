// 인증 목(REQ-002·003·036). 응답 형태는 openapi.json AuthTokenResponse·에러 응답 예시와 같다. RT 쿠키 대신 session.js 세션.
// 약관 재동의(REQ-037): memberDb.termsOutdatedMemberIds(시드 user2)면 requiresTermsAgreement true.
// 비밀번호 재설정 메일은 보내지 않고 링크를 콘솔에 찍는다(window.__mockAuth.lastPasswordResetLink()로도 볼 수 있다).
import { delay, http } from 'msw';

import {
  confirmPasswordResetInDb,
  findAccount,
  requestPasswordResetInDb,
  requiresTermsAgreement,
  validateLogin,
} from './memberLogic';
import { memberDb } from './memberSeed';
import { fail, ok } from './response';
import {
  authenticateMockAuthorization,
  endMockSession,
  endMockSessionOf,
  reissueMockSession,
  startMockSession,
} from './session';
import { applySavedTermsAgreements } from './terms';

let lastResetLink = null;

// 재설정 토큰은 메일 링크를 새 탭·새로고침으로 열어도 남게 localStorage에 둔다(메모리 db는 새로고침하면 시드로 돌아간다).
// 바뀐 비밀번호는 다른 목처럼 메모리에만 있어 새로고침하면 시드 비밀번호로 돌아간다.
const RESET_TOKENS_KEY = 'mock.passwordResetTokens';

function loadResetTokens() {
  try {
    memberDb.passwordResetTokens = JSON.parse(localStorage.getItem(RESET_TOKENS_KEY)) ?? {};
  } catch {
    memberDb.passwordResetTokens = {};
  }
}

function saveResetTokens() {
  try {
    localStorage.setItem(RESET_TOKENS_KEY, JSON.stringify(memberDb.passwordResetTokens));
  } catch {
    // 저장소를 못 쓰면 같은 탭에서만 링크가 맞는다
  }
}

/** 개발 확인용: 마지막으로 "보낸" 재설정 링크(없으면 null) */
export function getLastPasswordResetLink() {
  return lastResetLink;
}

/** Base64URL 43자(32바이트) 토큰. 백엔드와 같은 형식 */
function createResetToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes))
    .replaceAll('+', '-')
    .replaceAll('/', '_')
    .replace(/=+$/, '');
}

function toTokenResponse(accessToken) {
  applySavedTermsAgreements();
  const member = authenticateMockAuthorization(`Bearer ${accessToken}`);
  return {
    accessToken,
    requiresTermsAgreement: member ? requiresTermsAgreement(memberDb, member.memberId) : false,
  };
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

export const authHandlers = [
  http.post('/api/auth/login', async ({ request }) => {
    await delay(400);
    const body = await readJson(request);
    const details = validateLogin(body);
    if (details.length > 0) {
      return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', details);
    }
    const member = findAccount(memberDb.members, body);
    if (!member) {
      return fail(401, 'AUTH_INVALID_CREDENTIALS', '이메일 또는 비밀번호가 올바르지 않습니다.');
    }
    return ok(toTokenResponse(startMockSession(member.memberId)));
  }),

  // 쿠키가 없거나 이미 무효여도 성공(data null)
  http.post('/api/auth/logout', async () => {
    await delay(300);
    endMockSession();
    return ok(null);
  }),

  http.post('/api/auth/reissue', () => {
    const accessToken = reissueMockSession();
    return accessToken
      ? ok(toTokenResponse(accessToken))
      : fail(401, 'AUTH_REFRESH_TOKEN_INVALID', '로그인이 만료되었습니다. 다시 로그인해 주세요.');
  }),

  // 가입 여부·요청 제한과 무관하게 항상 성공(data null)
  http.post('/api/auth/password-reset', async ({ request }) => {
    await delay(400);
    const body = await readJson(request);
    const email = body?.email;
    if (typeof email !== 'string' || !/^[^\s@]+@[^\s@]+$/.test(email.trim())) {
      return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', [
        { field: 'email', reason: '올바른 형식의 이메일 주소여야 합니다' },
      ]);
    }
    loadResetTokens();
    const token = requestPasswordResetInDb(memberDb, email, Date.now(), createResetToken);
    saveResetTokens();
    if (token) {
      lastResetLink = `${window.location.origin}/password-reset?token=${token}`;
      console.info('[mock] 비밀번호 재설정 링크:', lastResetLink);
    }
    return ok(null);
  }),

  http.post('/api/auth/password-reset/confirm', async ({ request }) => {
    await delay(400);
    loadResetTokens();
    const result = confirmPasswordResetInDb(memberDb, await readJson(request), Date.now());
    saveResetTokens();
    if (result.details) {
      return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', result.details);
    }
    if (result.code) {
      return fail(400, result.code, '비밀번호 재설정 링크가 만료되었거나 유효하지 않습니다.');
    }
    // 모든 기기 로그아웃: 목 세션이 그 회원이면 끝낸다
    endMockSessionOf(result.memberId);
    return ok(null);
  }),
];
