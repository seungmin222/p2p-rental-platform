import { http } from 'msw';

import { ok } from './response';

export const authHandlers = [
  // 응답 형태는 openapi.json AuthTokenResponse와 같다. 항상 성공시키는 것은 로그인 화면 전이라서이며
  // (앱 시작 시 재발급으로 RequireAuth를 통과시킨다) feat/fe-auth-region에서 교체.
  http.post('/api/auth/reissue', () => ok({ accessToken: 'mock-access-token' })),
];
