import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';
import { expireMockAccessToken } from './session';

export const worker = setupWorker(...handlers);

// 개발 확인용(콘솔에서 window.__mockAuth.expireAccessToken()). 이 파일은 main.jsx가 개발 서버 + VITE_USE_MOCK일 때만
// 동적 import하므로 운영 번들에는 들어가지 않는다
window.__mockAuth = { expireAccessToken: expireMockAccessToken };
