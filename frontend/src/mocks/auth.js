import { http } from 'msw';

import { ok } from './response';

export const authHandlers = [
  // TODO: 백엔드 확정 전 임시 — 로그인 API가 없어 앱 시작 시 재발급을 항상 성공시켜 RequireAuth를 통과시킨다.
  // 응답 { accessToken }은 shared/api/reissue.js extractAccessToken의 가정과 같다. feat/fe-auth-region에서 교체.
  http.post('/api/auth/reissue', () => ok({ accessToken: 'mock-access-token' })),
];
