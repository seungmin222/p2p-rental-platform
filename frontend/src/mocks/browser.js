import { setupWorker } from 'msw/browser';

import { handlers } from './handlers';
import { failNext } from './paymentScenario';
import { clearSavedDb } from './rentalStore';
import { expireMockAccessToken } from './session';

export const worker = setupWorker(...handlers);

// 개발 확인용(콘솔에서 window.__mockAuth.expireAccessToken()). 이 파일은 main.jsx가 개발 서버 + VITE_USE_MOCK일 때만
// 동적 import하므로 운영 번들에는 들어가지 않는다
window.__mockAuth = { expireAccessToken: expireMockAccessToken };

// failNext(code): 다음 승인·반환 한 번을 실패시킨다(paymentScenario.js). reset(): 대여·결제 목을 시드로 되돌린다(로그인은 유지)
window.__mockPayment = {
  failNext,
  reset() {
    clearSavedDb();
    window.location.reload();
  },
};
