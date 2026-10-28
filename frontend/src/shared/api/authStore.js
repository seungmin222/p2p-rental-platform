import { create } from 'zustand';

/**
 * Access Token은 메모리에만 둔다(localStorage·sessionStorage 저장 금지).
 * Refresh Token은 HttpOnly 쿠키라 JS가 다루지 않는다.
 */
export const useAuthStore = create((set) => ({
  accessToken: null,
  setAccessToken: (accessToken) => set({ accessToken }),
  clearAccessToken: () => set({ accessToken: null }),
}));
