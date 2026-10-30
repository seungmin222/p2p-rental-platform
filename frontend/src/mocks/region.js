// 동네 검색·좌표 변환 목(REQ-004). 공개 API(openapi에 security 없음)라 AT를 보지 않는다.
// 좌표 → 행정동은 가장 가까운 시드 중심이 3km 안인지로 흉내 낸다(regionLogic.js MOCK_NEAREST_MAX_KM).
import { delay, http } from 'msw';

import { MOCK_REGION_MASTER } from './memberSeed';
import {
  findRegionByCoord,
  readCoord,
  searchRegions,
  toRegionResponse,
  validateKeyword,
} from './regionLogic';
import { fail, ok } from './response';

function invalidInput(details) {
  return fail(400, 'COMMON_INVALID_INPUT', '입력값이 올바르지 않습니다.', details);
}

export const regionHandlers = [
  http.get('/api/regions/by-coord', async ({ request }) => {
    await delay(400);
    const params = new URL(request.url).searchParams;
    const { coord, details } = readCoord({ lat: params.get('lat'), lng: params.get('lng') });
    if (details) {
      return invalidInput(details);
    }
    const region = findRegionByCoord(MOCK_REGION_MASTER, coord);
    if (!region) {
      return fail(404, 'COMMON_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.');
    }
    return ok(toRegionResponse(region));
  }),

  http.get('/api/regions', async ({ request }) => {
    await delay(300);
    const keyword = new URL(request.url).searchParams.get('keyword');
    const details = validateKeyword(keyword);
    if (details.length > 0) {
      return invalidInput(details);
    }
    return ok(searchRegions(MOCK_REGION_MASTER, keyword));
  }),
];
