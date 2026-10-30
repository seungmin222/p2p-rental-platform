import { MutationObserver, QueryClient, QueryObserver } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { queryClient as appQueryClient } from '../../app/queryClient';
import { ApiError } from '../../shared/api';
import * as api from './api';
import { getErrorMessage, LOCATION_DENIED_MESSAGE } from './errorMessages';
import { LOCATION_ERROR, LocationError } from './geolocation';
import {
  addRegionMutationOptions,
  locateRegionMutationOptions,
  regionKeys,
  verifyRegionMutationOptions,
} from './queries';

vi.mock('./api', () => ({
  addMyRegion: vi.fn(),
  deleteMyRegion: vi.fn(),
  fetchMyRegions: vi.fn(),
  fetchRegionByCoord: vi.fn(),
  searchRegions: vi.fn(),
  setPrimaryMyRegion: vi.fn(),
  verifyMyRegion: vi.fn(),
}));

// 다른 값과 겹치지 않는 표본 좌표
const COORDS = { lat: 37.123456, lng: 126.654321 };
const REGION = { regionId: 1101, name: '송도1동', fullName: '인천광역시 연수구 송도1동' };
const MY_REGION = {
  memberRegionId: 7,
  regionId: 1101,
  regionName: '송도1동',
  fullName: '인천광역시 연수구 송도1동',
  isPrimary: true,
  isVerified: false,
  verifiedAt: null,
  verifyExpiresAt: null,
};
const VERIFIED = {
  ...MY_REGION,
  isVerified: true,
  verifiedAt: '2026-10-30T10:00:00',
  verifyExpiresAt: '2026-11-29T10:00:00',
};

/** 객체 안의 모든 숫자·문자열(함수는 건너뜀, 순환 참조 방지) */
function collectPrimitives(value, seen = new Set(), out = []) {
  if (typeof value === 'number' || typeof value === 'string') {
    out.push(value);
  } else if (value && typeof value === 'object' && !seen.has(value)) {
    seen.add(value);
    const keys = value instanceof Error ? ['message', 'code', ...Object.keys(value)] : null;
    for (const key of keys ?? Object.keys(value)) {
      collectPrimitives(value[key], seen, out);
    }
  }
  return out;
}

/** MutationCache·QueryCache 어디에도 표본 좌표가 숫자나 문자열로 남지 않았는지 */
function expectNoCoordsInCache(client) {
  const mutations = client
    .getMutationCache()
    .getAll()
    .map((m) => m.state);
  const queries = client
    .getQueryCache()
    .getAll()
    .map((q) => ({ key: q.queryKey, state: q.state }));
  const values = collectPrimitives({ mutations, queries });
  expect(values.length).toBeGreaterThan(0);
  for (const value of values) {
    expect(value).not.toBe(COORDS.lat);
    expect(value).not.toBe(COORDS.lng);
    if (typeof value === 'string') {
      expect(value).not.toContain('37.123456');
      expect(value).not.toContain('126.654321');
    }
  }
}

function run(client, options, variables) {
  return new MutationObserver(client, options).mutate(variables);
}

let client;
beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient();
  client.setQueryData(regionKeys.mine(), [MY_REGION]);
});

describe('좌표가 캐시에 남지 않는다', () => {
  it('인증 성공: 좌표는 API에만 가고 목록 항목은 응답으로 바뀐다', async () => {
    api.verifyMyRegion.mockResolvedValue(VERIFIED);
    await run(client, verifyRegionMutationOptions(client), {
      memberRegionId: 7,
      locate: async () => ({ ...COORDS }),
    });
    expect(api.verifyMyRegion).toHaveBeenCalledWith(7, COORDS);
    expect(client.getQueryData(regionKeys.mine())).toEqual([VERIFIED]);
    expectNoCoordsInCache(client);
  });

  it('인증 실패(ApiError): error에도 좌표가 없다', async () => {
    api.verifyMyRegion.mockRejectedValue(
      new ApiError({ code: 'REGION_VERIFY_MISMATCH', message: '다름', status: 400 }),
    );
    await expect(
      run(client, verifyRegionMutationOptions(client), {
        memberRegionId: 7,
        locate: async () => ({ ...COORDS }),
      }),
    ).rejects.toMatchObject({ code: 'REGION_VERIFY_MISMATCH' });
    expect(api.verifyMyRegion).toHaveBeenCalledWith(7, COORDS);
    expectNoCoordsInCache(client);
  });

  it('현재 위치 찾기: data는 RegionResponse, 좌표는 onLocated로만', async () => {
    api.fetchRegionByCoord.mockResolvedValue({ ...REGION });
    const received = [];
    const data = await run(client, locateRegionMutationOptions(), {
      locate: async () => ({ ...COORDS }),
      onLocated: (coords) => received.push(coords),
    });
    expect(data).toEqual(REGION);
    expect(received).toEqual([COORDS]);
    expect(api.fetchRegionByCoord).toHaveBeenCalledWith(COORDS);
    expectNoCoordsInCache(client);
  });

  it('찾은 위치로 추가 → 인증: ref에서 꺼낸 좌표로 인증하고 캐시에는 남지 않는다', async () => {
    api.fetchRegionByCoord.mockResolvedValue({ ...REGION });
    api.addMyRegion.mockResolvedValue({ ...MY_REGION, memberRegionId: 8 });
    api.fetchMyRegions.mockResolvedValue([{ ...MY_REGION, memberRegionId: 8 }]);
    api.verifyMyRegion.mockResolvedValue({ ...VERIFIED, memberRegionId: 8 });
    const coordsRef = { current: null };

    await run(client, locateRegionMutationOptions(), {
      locate: async () => ({ ...COORDS }),
      onLocated: (coords) => {
        coordsRef.current = coords;
      },
    });
    const added = await run(client, addRegionMutationOptions(client), { regionId: 1101 });
    await run(client, verifyRegionMutationOptions(client), {
      memberRegionId: added.memberRegionId,
      locate: async () => {
        const coords = coordsRef.current;
        coordsRef.current = null;
        return coords;
      },
    });

    expect(api.verifyMyRegion).toHaveBeenCalledWith(8, COORDS);
    expect(coordsRef.current).toBeNull();
    expectNoCoordsInCache(client);
  });
});

describe('실패 뒤 목록 갱신', () => {
  it('REGION_LIMIT_EXCEEDED면 목록을 다시 받은 뒤에 뮤테이션이 끝난다', async () => {
    api.addMyRegion.mockRejectedValue(
      new ApiError({ code: 'REGION_LIMIT_EXCEEDED', message: '초과', status: 409 }),
    );
    const fresh = [MY_REGION, { ...MY_REGION, memberRegionId: 9, isPrimary: false }];
    let resolveList;
    api.fetchMyRegions.mockReturnValue(
      new Promise((resolve) => {
        resolveList = resolve;
      }),
    );
    // 목록을 보여 주는 화면이 있는 상태(무효화하면 다시 받는다)
    const unsubscribe = new QueryObserver(client, {
      queryKey: regionKeys.mine(),
      queryFn: api.fetchMyRegions,
      staleTime: Infinity,
    }).subscribe(() => {});

    let settled = false;
    const done = run(client, addRegionMutationOptions(client), { regionId: 1102 })
      .catch(() => {})
      .finally(() => {
        settled = true;
      });
    await vi.waitFor(() => expect(api.fetchMyRegions).toHaveBeenCalled());
    expect(settled).toBe(false);
    expect(client.isMutating()).toBe(1);

    resolveList(fresh);
    await done;
    expect(client.isMutating()).toBe(0);
    expect(client.getQueryData(regionKeys.mine())).toEqual(fresh);
    unsubscribe();
  });

  it('목록과 무관한 실패(검증 400 등)는 다시 받지 않는다', async () => {
    api.addMyRegion.mockRejectedValue(
      new ApiError({ code: 'COMMON_INVALID_INPUT', message: '입력', status: 400 }),
    );
    await run(client, addRegionMutationOptions(client), { regionId: 1 }).catch(() => {});
    expect(api.fetchMyRegions).not.toHaveBeenCalled();
  });
});

describe('LocationError는 ApiError 경로를 타지 않는다', () => {
  it('앱 QueryClient로 실행해도 한 번만 시도하고 API(인터셉터·재발급)를 부르지 않는다', async () => {
    const locate = vi.fn(async () => {
      throw new LocationError(LOCATION_ERROR.DENIED);
    });
    const error = await run(appQueryClient, verifyRegionMutationOptions(appQueryClient), {
      memberRegionId: 7,
      locate,
    }).catch((e) => e);

    expect(error).toBeInstanceOf(LocationError);
    expect(error).not.toBeInstanceOf(ApiError);
    expect(error.status).toBeUndefined();
    // 뮤테이션은 기본 retry 0(앱 QueryClient의 retry 함수는 queries에만 있다)
    expect(appQueryClient.getDefaultOptions().mutations?.retry).toBeUndefined();
    expect(locate).toHaveBeenCalledTimes(1);
    // axios를 거치지 않으므로 401 인터셉터·재발급도 없다
    expect(api.verifyMyRegion).not.toHaveBeenCalled();
    appQueryClient.getMutationCache().clear();
  });

  it('문구는 LocationError.code로 찾는다', () => {
    expect(getErrorMessage(new LocationError(LOCATION_ERROR.DENIED))).toBe(LOCATION_DENIED_MESSAGE);
    expect(getErrorMessage(new LocationError(LOCATION_ERROR.TIMEOUT))).toContain('시간');
    // 코드가 문구 표에 있으니 message(code 문자열)로 떨어지지 않는다
    for (const code of Object.values(LOCATION_ERROR)) {
      expect(getErrorMessage(new LocationError(code))).not.toBe(code);
    }
  });
});
