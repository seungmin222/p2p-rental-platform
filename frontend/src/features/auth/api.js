import { apiClient } from '../../shared/api';

/** POST /api/auth/login (REQ-002) → AuthTokenResponse { accessToken }. RT는 HttpOnly 쿠키로 온다 */
export function login({ email, password }) {
  return apiClient.post('/auth/login', { email, password });
}

/** POST /api/auth/logout (REQ-002). 공개 API라 Authorization을 붙이지 않는다(shared/api). data null */
export function logout() {
  return apiClient.post('/auth/logout');
}

/** POST /api/members (REQ-001) → MemberSignupResponse { memberId } */
export function signup({ email, password, nickname }) {
  return apiClient.post('/members', { email, password, nickname });
}

/** GET /api/members/me → MemberResponse(대표 동네 요약 primaryRegion 포함, 없으면 null) */
export function fetchMe({ signal } = {}) {
  return apiClient.get('/members/me', { signal });
}
