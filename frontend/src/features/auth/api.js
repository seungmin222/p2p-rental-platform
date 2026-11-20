import { apiClient } from '../../shared/api';

/** POST /api/auth/login (REQ-002) → AuthTokenResponse { accessToken, requiresTermsAgreement }. RT는 HttpOnly 쿠키로 온다 */
export function login({ email, password }) {
  return apiClient.post('/auth/login', { email, password });
}

/** POST /api/auth/logout (REQ-002). 공개 API라 Authorization을 붙이지 않는다(shared/api). data null */
export function logout() {
  return apiClient.post('/auth/logout');
}

/**
 * POST /api/members (REQ-001) → MemberSignupResponse { memberId }.
 * agreements { termsOfService, privacyCollection, ageOver14 }는 필수 약관 동의(REQ-037, 셋 다 true여야 함)
 */
export function signup({ email, password, nickname, agreements }) {
  return apiClient.post('/members', { email, password, nickname, agreements });
}

/** POST /api/auth/password-reset { email } (REQ-036). 가입 여부와 상관없이 성공(data null) */
export function requestPasswordReset({ email }) {
  return apiClient.post('/auth/password-reset', { email });
}

/** POST /api/auth/password-reset/confirm { token, newPassword } (REQ-036). data null, 성공하면 모든 기기 로그아웃 */
export function confirmPasswordReset({ token, newPassword }) {
  return apiClient.post('/auth/password-reset/confirm', { token, newPassword });
}

/** GET /api/members/me → MemberResponse(대표 동네 요약 primaryRegion 포함, 없으면 null) */
export function fetchMe({ signal } = {}) {
  return apiClient.get('/members/me', { signal });
}
