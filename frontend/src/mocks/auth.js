// 인증 목(REQ-002·003). 응답 형태는 openapi.json AuthTokenResponse·에러 응답 예시와 같다. RT 쿠키 대신 session.js 세션.
import { delay, http } from 'msw';

import { findAccount, validateLogin } from './memberLogic';
import { memberDb } from './memberSeed';
import { fail, ok } from './response';
import { endMockSession, reissueMockSession, startMockSession } from './session';

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
    return ok({ accessToken: startMockSession(member.memberId) });
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
      ? ok({ accessToken })
      : fail(401, 'AUTH_REFRESH_TOKEN_INVALID', '로그인이 만료되었습니다. 다시 로그인해 주세요.');
  }),
];
