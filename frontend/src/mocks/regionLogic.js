// 동네 목의 순수 함수(REQ-004). 저장소(memberSeed.js의 db)·행정동 마스터를 받아 계산하고 node에서 테스트한다.
// 목은 화면 상수(features)를 가져오지 않는 규칙이라 정책 값을 여기 따로 둔다(memberLogic.js와 같은 이유).
// 인증 유효기간(region.verify-valid-days)은 memberLogic.js MOCK_MEMBER_POLICY.verifyValidDays를 toVerification이 쓴다
import { toVerification } from './memberLogic';
import { toServerDateTime } from './rentalSeed';

// policies.md region.max-per-member
export const MOCK_REGION_POLICY = { maxPerMember: 2 };

// 좌표 → 행정동 흉내: 가장 가까운 시드 중심이 이 거리 안이면 그 동네. 목 전용 값이며 region.range(피드 범위)와 무관하다.
// 실제 서버는 카카오 로컬 API로 행정동 경계를 판정한다
export const MOCK_NEAREST_MAX_KM = 3;

const EARTH_RADIUS_KM = 6371;
const NOT_NULL_MESSAGE = '널이어서는 안됩니다';
const COORD_RANGES = { lat: { min: -90, max: 90 }, lng: { min: -180, max: 180 } };

/** RegionResponse { regionId, name, fullName } */
export function toRegionResponse(region) {
  return { regionId: region.regionId, name: region.name, fullName: region.fullName };
}

/**
 * GET /api/regions: 행정동 이름(name)에 keyword가 들어간 행정동(openapi 설명대로 fullName은 보지 않는다).
 * keyword는 앞뒤 공백을 지워 비교한다. 결과가 없으면 빈 배열
 */
export function searchRegions(master, keyword) {
  const text = keyword.trim();
  return master.filter((region) => region.name.includes(text)).map(toRegionResponse);
}

/** keyword 검증(@NotBlank 흉내). details[] 반환, 통과면 빈 배열 */
export function validateKeyword(keyword) {
  return typeof keyword === 'string' && keyword.trim() !== ''
    ? []
    : [{ field: 'keyword', reason: '공백일 수 없습니다' }];
}

/**
 * 좌표 검증(RegionCoordRequest: lat -90~90, lng -180~180, 필수). 값은 숫자 또는 숫자 문자열(쿼리).
 * 통과하면 { coord: { lat, lng } }, 아니면 { details }
 */
export function readCoord(input) {
  const details = [];
  const coord = {};
  for (const [field, { min, max }] of Object.entries(COORD_RANGES)) {
    const raw = input?.[field];
    const value = typeof raw === 'string' && raw.trim() !== '' ? Number(raw) : raw;
    if (typeof value !== 'number' || !Number.isFinite(value)) {
      details.push({ field, reason: NOT_NULL_MESSAGE });
    } else if (value < min || value > max) {
      details.push({ field, reason: `${min} 이상 ${max} 이하여야 합니다` });
    } else {
      coord[field] = value;
    }
  }
  return details.length > 0 ? { details } : { coord };
}

function toRadians(degree) {
  return (degree * Math.PI) / 180;
}

/** 두 좌표 사이 거리(km, haversine) */
export function distanceKm(a, b) {
  const dLat = toRadians(b.lat - a.lat);
  const dLng = toRadians(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
}

/** GET /api/regions/by-coord: 가장 가까운 시드 중심이 maxKm 안이면 그 행정동(마스터 항목), 아니면 null(404) */
export function findRegionByCoord(master, coord, maxKm = MOCK_NEAREST_MAX_KM) {
  let nearest = null;
  let nearestKm = Infinity;
  for (const region of master) {
    const km = distanceKm(coord, region);
    if (km < nearestKm) {
      nearest = region;
      nearestKm = km;
    }
  }
  return nearestKm <= maxKm ? nearest : null;
}

/** MemberRegionResponse */
export function toMemberRegionResponse(memberRegion, master, now = new Date()) {
  const region = master.find((r) => r.regionId === memberRegion.regionId);
  return {
    memberRegionId: memberRegion.memberRegionId,
    regionId: region.regionId,
    regionName: region.name,
    fullName: region.fullName,
    isPrimary: memberRegion.isPrimary,
    ...toVerification(memberRegion.verifiedAt, now),
  };
}

function ownRegions(db, memberId) {
  return db.memberRegions.filter((mr) => mr.memberId === memberId);
}

/** GET /api/members/me/regions: 대표 동네 먼저, 나머지는 추가한 순서(memberRegionId 오름차순) */
export function listMemberRegions(db, memberId, master, now = new Date()) {
  return ownRegions(db, memberId)
    .toSorted(
      (a, b) => Number(b.isPrimary) - Number(a.isPrimary) || a.memberRegionId - b.memberRegionId,
    )
    .map((mr) => toMemberRegionResponse(mr, master, now));
}

/** 내 동네 하나. 없거나 남의 것이면 null(404 COMMON_NOT_FOUND) */
export function findOwnMemberRegion(db, memberId, memberRegionId) {
  return (
    db.memberRegions.find(
      (mr) => mr.memberRegionId === memberRegionId && mr.memberId === memberId,
    ) ?? null
  );
}

/**
 * POST /api/members/me/regions. 검사 순서는 regionId 400 → 이미 추가 409 → 개수 409로 가정
 * (fe-auth-region notes "백엔드에 확인할 것" 3). 첫 동네면 자동 대표, 인증 전 상태로 추가.
 * 성공 { memberRegion }(db에 넣음), 실패 { error: { status, code, details? } }
 */
export function addMemberRegion(db, memberId, regionId, master, policy = MOCK_REGION_POLICY) {
  if (!Number.isInteger(regionId) || !master.some((r) => r.regionId === regionId)) {
    const reason = regionId == null ? NOT_NULL_MESSAGE : '존재하지 않는 행정동입니다';
    return {
      error: {
        status: 400,
        code: 'COMMON_INVALID_INPUT',
        details: [{ field: 'regionId', reason }],
      },
    };
  }
  const mine = ownRegions(db, memberId);
  if (mine.some((mr) => mr.regionId === regionId)) {
    return { error: { status: 409, code: 'REGION_ALREADY_ADDED' } };
  }
  if (mine.length >= policy.maxPerMember) {
    return { error: { status: 409, code: 'REGION_LIMIT_EXCEEDED' } };
  }
  const memberRegion = {
    memberRegionId: db.nextMemberRegionId++,
    memberId,
    regionId,
    isPrimary: mine.length === 0,
    verifiedAt: null,
  };
  db.memberRegions.push(memberRegion);
  return { memberRegion };
}

/** PATCH .../primary: 이 동네만 대표로. 없으면 false(404) */
export function setPrimaryRegion(db, memberId, memberRegionId) {
  if (!findOwnMemberRegion(db, memberId, memberRegionId)) {
    return false;
  }
  for (const mr of ownRegions(db, memberId)) {
    mr.isPrimary = mr.memberRegionId === memberRegionId;
  }
  return true;
}

/** DELETE: 지운다. 대표 동네였으면 남은 동네 중 먼저 추가한 것이 대표가 된다. 없으면 false(404) */
export function deleteMemberRegion(db, memberId, memberRegionId) {
  const target = findOwnMemberRegion(db, memberId, memberRegionId);
  if (!target) {
    return false;
  }
  db.memberRegions = db.memberRegions.filter((mr) => mr !== target);
  if (target.isPrimary) {
    const [next] = ownRegions(db, memberId).toSorted((a, b) => a.memberRegionId - b.memberRegionId);
    if (next) {
      next.isPrimary = true;
    }
  }
  return true;
}

/**
 * PATCH .../verify: 좌표의 행정동(by-coord와 같은 판정) == 이 동네면 verifiedAt = 지금.
 * 다르거나 어느 동네에도 속하지 않으면 400 REGION_VERIFY_MISMATCH(어느 동네에도 없을 때의 응답은 openapi에 없어 가정,
 * fe-auth-region notes "백엔드에 확인할 것" 2). 좌표는 저장하지 않는다.
 * 성공 { memberRegion }, 실패 { error: { status, code } }
 */
export function verifyMemberRegion(db, memberId, memberRegionId, coord, master, now = new Date()) {
  const target = findOwnMemberRegion(db, memberId, memberRegionId);
  if (!target) {
    return { error: { status: 404, code: 'COMMON_NOT_FOUND' } };
  }
  if (findRegionByCoord(master, coord)?.regionId !== target.regionId) {
    return { error: { status: 400, code: 'REGION_VERIFY_MISMATCH' } };
  }
  target.verifiedAt = toServerDateTime(now);
  return { memberRegion: target };
}
