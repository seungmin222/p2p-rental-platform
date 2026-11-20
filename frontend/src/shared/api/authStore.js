import { create } from 'zustand';

/**
 * Access Token은 메모리에만 둔다(localStorage·sessionStorage 저장 금지).
 * Refresh Token은 HttpOnly 쿠키라 JS가 다루지 않는다.
 *
 * 약관 재동의(REQ-037): 로그인·재발급 응답의 requiresTermsAgreement를 AT와 같이 메모리에 둔다(서버 조회 캐시가 아니라 토큰 응답의 일부).
 * termsPromptDismissed는 재동의 모달에서 "나중에"를 누른 것 — 새로고침하거나 다시 로그인하면 다시 묻는다.
 * AT를 지우면 둘 다 처음으로 돌린다.
 */
export const useAuthStore = create((set) => ({
  accessToken: null,
  requiresTermsAgreement: false,
  termsPromptDismissed: false,
  setAccessToken: (accessToken) => set({ accessToken }),
  clearAccessToken: () =>
    set({ accessToken: null, requiresTermsAgreement: false, termsPromptDismissed: false }),
  setRequiresTermsAgreement: (requiresTermsAgreement) =>
    set({ requiresTermsAgreement: requiresTermsAgreement === true }),
  dismissTermsPrompt: () => set({ termsPromptDismissed: true }),
}));
