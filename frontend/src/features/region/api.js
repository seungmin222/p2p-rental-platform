// 동네 API 호출(REQ-004). 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
// 좌표는 인자로 받아 그대로 보내기만 하고 보관하지 않는다.
import { apiClient } from '../../shared/api';

/** GET /api/regions?keyword= → RegionResponse[] (공개) */
export function searchRegions(keyword) {
  return apiClient.get('/regions', { params: { keyword } });
}

/** GET /api/regions/by-coord?lat=&lng= → RegionResponse (공개, 못 찾으면 404 COMMON_NOT_FOUND) */
export function fetchRegionByCoord({ lat, lng }) {
  return apiClient.get('/regions/by-coord', { params: { lat, lng } });
}

/** GET /api/members/me/regions → MemberRegionResponse[] (대표 동네 먼저) */
export function fetchMyRegions() {
  return apiClient.get('/members/me/regions');
}

/** POST /api/members/me/regions { regionId } → MemberRegionResponse (인증 전 상태, 첫 동네면 대표) */
export function addMyRegion(regionId) {
  return apiClient.post('/members/me/regions', { regionId });
}

/** PATCH /api/members/me/regions/{memberRegionId}/verify { lat, lng } → MemberRegionResponse */
export function verifyMyRegion(memberRegionId, { lat, lng }) {
  return apiClient.patch(`/members/me/regions/${memberRegionId}/verify`, { lat, lng });
}

/** PATCH /api/members/me/regions/{memberRegionId}/primary → MemberRegionResponse[] (바뀐 뒤 목록 전체) */
export function setPrimaryMyRegion(memberRegionId) {
  return apiClient.patch(`/members/me/regions/${memberRegionId}/primary`);
}

/** DELETE /api/members/me/regions/{memberRegionId} → null */
export function deleteMyRegion(memberRegionId) {
  return apiClient.delete(`/members/me/regions/${memberRegionId}`);
}
