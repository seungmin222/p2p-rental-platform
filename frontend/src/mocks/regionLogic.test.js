import { describe, expect, it } from 'vitest';

import { createMemberDb, MOCK_REGION_MASTER } from './memberSeed';
import {
  addMemberRegion,
  deleteMemberRegion,
  distanceKm,
  findRegionByCoord,
  listMemberRegions,
  readCoord,
  searchRegions,
  setPrimaryRegion,
  validateKeyword,
  verifyMemberRegion,
} from './regionLogic';

// 위도 1도 = 지구 반지름 6371km 기준 약 111.195km
const KM_PER_LAT_DEGREE = (6371 * Math.PI) / 180;
const SONGDO1 = { lat: 37.3925, lng: 126.639 };
// 서울 강남역 부근: 어느 시드 동네에서도 3km 밖
const GANGNAM = { lat: 37.4979, lng: 127.0276 };

describe('searchRegions', () => {
  it('행정동 이름(name) 부분 일치, 앞뒤 공백은 지운다', () => {
    expect(searchRegions(MOCK_REGION_MASTER, ' 송도 ').map((r) => r.name)).toEqual([
      '송도1동',
      '송도2동',
    ]);
    expect(searchRegions(MOCK_REGION_MASTER, '1동').map((r) => r.name)).toEqual([
      '송도1동',
      '연수1동',
      '동춘1동',
      '옥련1동',
    ]);
  });

  it('fullName에만 있는 말은 찾지 않는다(openapi: 행정동 이름)', () => {
    expect(searchRegions(MOCK_REGION_MASTER, '연수구')).toEqual([]);
    expect(searchRegions(MOCK_REGION_MASTER, '없는동')).toEqual([]);
  });

  it('응답은 RegionResponse 필드만(좌표 없음)', () => {
    expect(searchRegions(MOCK_REGION_MASTER, '청학')).toEqual([
      { regionId: 1106, name: '청학동', fullName: '인천광역시 연수구 청학동' },
    ]);
  });

  it('빈 keyword는 400 details', () => {
    expect(validateKeyword('  ')).toEqual([{ field: 'keyword', reason: '공백일 수 없습니다' }]);
    expect(validateKeyword(null)).toHaveLength(1);
    expect(validateKeyword('송')).toEqual([]);
  });
});

describe('readCoord', () => {
  it('숫자·숫자 문자열(쿼리)을 받는다', () => {
    expect(readCoord({ lat: '37.5', lng: 127 })).toEqual({ coord: { lat: 37.5, lng: 127 } });
  });

  it('없거나 숫자가 아니거나 범위를 넘으면 details', () => {
    expect(readCoord({}).details.map((d) => d.field)).toEqual(['lat', 'lng']);
    expect(readCoord({ lat: 'abc', lng: '' }).details).toHaveLength(2);
    expect(readCoord({ lat: 91, lng: -181 }).details).toEqual([
      { field: 'lat', reason: '-90 이상 90 이하여야 합니다' },
      { field: 'lng', reason: '-180 이상 180 이하여야 합니다' },
    ]);
  });
});

describe('findRegionByCoord', () => {
  const single = [{ regionId: 1, name: '가동', fullName: '가시 가동', lat: 37, lng: 127 }];

  it('3km 경계: 안이면 그 동네, 밖이면 null', () => {
    const inside = { lat: 37 + 2.9 / KM_PER_LAT_DEGREE, lng: 127 };
    const outside = { lat: 37 + 3.1 / KM_PER_LAT_DEGREE, lng: 127 };
    expect(distanceKm(single[0], inside)).toBeCloseTo(2.9, 5);
    expect(findRegionByCoord(single, inside)?.regionId).toBe(1);
    expect(findRegionByCoord(single, outside)).toBeNull();
  });

  it('가장 가까운 시드 중심의 동네', () => {
    expect(findRegionByCoord(MOCK_REGION_MASTER, SONGDO1).name).toBe('송도1동');
    expect(findRegionByCoord(MOCK_REGION_MASTER, { lat: 37.428, lng: 126.664 }).name).toBe(
      '청학동',
    );
    expect(findRegionByCoord(MOCK_REGION_MASTER, GANGNAM)).toBeNull();
  });
});

describe('listMemberRegions', () => {
  it('대표 먼저, 그다음 추가한 순서. MemberRegionResponse 형태', () => {
    const db = createMemberDb();
    setPrimaryRegion(db, 1, 2);
    const list = listMemberRegions(db, 1, MOCK_REGION_MASTER);
    expect(list.map((r) => [r.memberRegionId, r.isPrimary])).toEqual([
      [2, true],
      [1, false],
    ]);
    expect(Object.keys(list[0]).sort()).toEqual(
      [
        'fullName',
        'isPrimary',
        'isVerified',
        'memberRegionId',
        'regionId',
        'regionName',
        'verifiedAt',
        'verifyExpiresAt',
      ].sort(),
    );
    expect(list[0]).toMatchObject({ regionName: '연수1동', isVerified: false });
  });

  it('동네가 없으면 빈 배열', () => {
    expect(listMemberRegions(createMemberDb(), 3, MOCK_REGION_MASTER)).toEqual([]);
  });
});

describe('addMemberRegion', () => {
  it('첫 동네는 자동 대표, 인증 전 상태', () => {
    const db = createMemberDb();
    const { memberRegion } = addMemberRegion(db, 3, 1102, MOCK_REGION_MASTER);
    expect(memberRegion).toMatchObject({ memberId: 3, regionId: 1102, isPrimary: true });
    expect(memberRegion.verifiedAt).toBeNull();
    expect(memberRegion.memberRegionId).toBe(100);

    const second = addMemberRegion(db, 3, 1103, MOCK_REGION_MASTER).memberRegion;
    expect(second.isPrimary).toBe(false);
  });

  it('검사 순서: regionId 400 → 이미 추가 409 → 최대 개수 409', () => {
    const db = createMemberDb();
    // 회원 1은 이미 최대(2개). 없는 행정동이면 개수보다 400이 먼저
    expect(addMemberRegion(db, 1, 9999, MOCK_REGION_MASTER).error).toEqual({
      status: 400,
      code: 'COMMON_INVALID_INPUT',
      details: [{ field: 'regionId', reason: '존재하지 않는 행정동입니다' }],
    });
    expect(addMemberRegion(db, 1, undefined, MOCK_REGION_MASTER).error.details[0]).toEqual({
      field: 'regionId',
      reason: '널이어서는 안됩니다',
    });
    // 이미 추가한 동네면 개수보다 "이미 추가"가 먼저
    expect(addMemberRegion(db, 1, 1101, MOCK_REGION_MASTER).error).toEqual({
      status: 409,
      code: 'REGION_ALREADY_ADDED',
    });
    expect(addMemberRegion(db, 1, 1102, MOCK_REGION_MASTER).error).toEqual({
      status: 409,
      code: 'REGION_LIMIT_EXCEEDED',
    });
    expect(db.memberRegions.filter((mr) => mr.memberId === 1)).toHaveLength(2);
  });

  it('최대 개수는 정책 값으로 센다', () => {
    const db = createMemberDb();
    expect(addMemberRegion(db, 1, 1102, MOCK_REGION_MASTER, { maxPerMember: 3 }).error).toBe(
      undefined,
    );
  });
});

describe('setPrimaryRegion', () => {
  it('이 동네만 대표, 남의·없는 동네는 false', () => {
    const db = createMemberDb();
    expect(setPrimaryRegion(db, 1, 2)).toBe(true);
    expect(db.memberRegions.filter((mr) => mr.memberId === 1 && mr.isPrimary)).toEqual([
      expect.objectContaining({ memberRegionId: 2 }),
    ]);
    expect(setPrimaryRegion(db, 1, 3)).toBe(false);
    expect(setPrimaryRegion(db, 1, 999)).toBe(false);
  });
});

describe('deleteMemberRegion', () => {
  it('대표를 지우면 남은 동네가 대표가 된다', () => {
    const db = createMemberDb();
    expect(deleteMemberRegion(db, 1, 1)).toBe(true);
    expect(listMemberRegions(db, 1, MOCK_REGION_MASTER)).toEqual([
      expect.objectContaining({ memberRegionId: 2, isPrimary: true }),
    ]);
  });

  it('대표가 아닌 동네를 지우면 대표는 그대로', () => {
    const db = createMemberDb();
    deleteMemberRegion(db, 1, 2);
    expect(listMemberRegions(db, 1, MOCK_REGION_MASTER)).toEqual([
      expect.objectContaining({ memberRegionId: 1, isPrimary: true }),
    ]);
  });

  it('마지막 동네를 지우면 대표 없음', () => {
    const db = createMemberDb();
    expect(deleteMemberRegion(db, 4, 4)).toBe(true);
    expect(listMemberRegions(db, 4, MOCK_REGION_MASTER)).toEqual([]);
  });

  it('남의·없는 동네는 false(404)이고 아무것도 지우지 않는다', () => {
    const db = createMemberDb();
    expect(deleteMemberRegion(db, 1, 3)).toBe(false);
    expect(deleteMemberRegion(db, 1, 999)).toBe(false);
    expect(db.memberRegions).toHaveLength(4);
  });
});

describe('verifyMemberRegion', () => {
  const now = new Date('2026-10-30T01:00:00Z'); // 서울 10:00

  it('좌표의 동네가 같으면 verifiedAt = 지금, 만료 = +30일', () => {
    const db = createMemberDb();
    const { memberRegion } = verifyMemberRegion(
      db,
      1,
      2,
      { lat: 37.418, lng: 126.679 },
      MOCK_REGION_MASTER,
      now,
    );
    expect(memberRegion.verifiedAt).toBe('2026-10-30T10:00:00');
    expect(listMemberRegions(db, 1, MOCK_REGION_MASTER, now)[1]).toMatchObject({
      memberRegionId: 2,
      isVerified: true,
      verifyExpiresAt: '2026-11-29T10:00:00',
    });
  });

  it('다른 동네 좌표면 400 REGION_VERIFY_MISMATCH, 인증 시각은 그대로', () => {
    const db = createMemberDb();
    const before = db.memberRegions.find((mr) => mr.memberRegionId === 2).verifiedAt;
    expect(verifyMemberRegion(db, 1, 2, SONGDO1, MOCK_REGION_MASTER, now).error).toEqual({
      status: 400,
      code: 'REGION_VERIFY_MISMATCH',
    });
    expect(db.memberRegions.find((mr) => mr.memberRegionId === 2).verifiedAt).toBe(before);
  });

  it('어느 동네에도 속하지 않는 좌표도 400 REGION_VERIFY_MISMATCH(가정)', () => {
    const db = createMemberDb();
    expect(verifyMemberRegion(db, 1, 1, GANGNAM, MOCK_REGION_MASTER, now).error.code).toBe(
      'REGION_VERIFY_MISMATCH',
    );
  });

  it('남의·없는 동네는 404', () => {
    const db = createMemberDb();
    expect(verifyMemberRegion(db, 1, 3, SONGDO1, MOCK_REGION_MASTER, now).error).toEqual({
      status: 404,
      code: 'COMMON_NOT_FOUND',
    });
  });
});
