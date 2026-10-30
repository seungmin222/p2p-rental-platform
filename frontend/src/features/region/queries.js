import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { memberKeys } from '../auth/queries';
import { itemKeys } from '../item/queries';
import {
  addMyRegion,
  deleteMyRegion,
  fetchMyRegions,
  fetchRegionByCoord,
  searchRegions,
  setPrimaryMyRegion,
  verifyMyRegion,
} from './api';
import { getCurrentCoords } from './geolocation';
import { replaceMemberRegion } from './myRegions';

// 좌표 규칙(fe-auth-region notes): TanStack Query는 뮤테이션 variables·data를 MutationCache에 두고 Devtools에도 보이므로
// 좌표를 variables·data·queryKey에 넣지 않는다. 좌표를 돌려주는 함수(locate)를 variables로 받아 mutationFn 안에서만 부른다.
// queries.test.js가 캐시를 훑어 확인한다. 뮤테이션 옵션을 함수로 내보내는 것은 그 테스트가 React 없이 실행하려고.

export const regionKeys = {
  // ['members', 'me']의 하위라 내 정보 무효화에 함께 걸린다
  mine: () => [...memberKeys.me(), 'regions'],
  search: (keyword) => ['regions', { keyword }],
};

// 이 화면의 뮤테이션 전부. 하나라도 진행 중이면 목록 버튼을 모두 막는다(useRegionMutating)
const MUTATION_KEY = ['region'];

/** 내 동네 목록(대표 먼저, 서버 순서 그대로) */
export function useMyRegions() {
  return useQuery({ queryKey: regionKeys.mine(), queryFn: fetchMyRegions, staleTime: 10_000 });
}

/** 동네 검색. keyword는 디바운스하고 앞뒤 공백을 지운 값, 빈 문자열이면 부르지 않는다 */
export function useRegionSearch(keyword) {
  return useQuery({
    queryKey: regionKeys.search(keyword),
    queryFn: () => searchRegions(keyword),
    // 행정동 마스터는 거의 바뀌지 않는다
    staleTime: 5 * 60_000,
    enabled: keyword !== '',
  });
}

/** 목록·내 정보를 다시 불러온다(동네가 지워졌거나 개수가 달라 서버와 어긋났을 때) */
function refetchMine(queryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: regionKeys.mine() }),
    queryClient.invalidateQueries({ queryKey: memberKeys.me(), exact: true }),
  ]);
}

// 대표 동네가 바뀌면 피드 기준이 바뀐다
function invalidateFeed(queryClient) {
  return queryClient.invalidateQueries({ queryKey: itemKeys.feed() });
}

const STALE_LIST_CODES = ['COMMON_NOT_FOUND', 'REGION_ALREADY_ADDED', 'REGION_LIMIT_EXCEEDED'];

function refetchIfStale(queryClient, error) {
  if (STALE_LIST_CODES.includes(error?.code)) {
    refetchMine(queryClient);
  }
}

/**
 * 현재 위치 → 행정동(by-coord). variables { locate?, onLocated? }, data는 RegionResponse만.
 * 좌표는 mutationFn 안에서 받아 onLocated(호출부의 ref에 넣는 콜백)로만 넘긴다. 비회원은 onLocated를 넘기지 않는다.
 */
export function locateRegionMutationOptions() {
  return {
    mutationKey: [...MUTATION_KEY, 'locate'],
    mutationFn: async ({ locate = getCurrentCoords, onLocated } = {}) => {
      const coords = await locate();
      const region = await fetchRegionByCoord(coords);
      onLocated?.(coords);
      return region;
    },
  };
}

/**
 * 동네 인증. variables { memberRegionId, locate? }(locate 기본은 브라우저 현재 위치).
 * 응답(동네 하나)은 순서·대표를 바꾸지 않으므로 목록의 그 항목만 바꾼다. 목록에 아직 없으면(추가 직후 재조회 중) 다시 불러온다.
 */
export function verifyRegionMutationOptions(queryClient) {
  return {
    mutationKey: [...MUTATION_KEY, 'verify'],
    mutationFn: async ({ memberRegionId, locate = getCurrentCoords }) =>
      verifyMyRegion(memberRegionId, await locate()),
    onSuccess: async (updated) => {
      // 추가 직후 재조회가 인증 전 목록을 늦게 덮어쓰지 않게 먼저 멈춘다
      await queryClient.cancelQueries({ queryKey: regionKeys.mine() });
      const next = replaceMemberRegion(queryClient.getQueryData(regionKeys.mine()), updated);
      if (next) {
        queryClient.setQueryData(regionKeys.mine(), next);
        await queryClient.invalidateQueries({ queryKey: memberKeys.me(), exact: true });
      } else {
        await refetchMine(queryClient);
      }
    },
    onError: (error) => refetchIfStale(queryClient, error),
  };
}

/** 동네 추가. variables { regionId }. 서버 순서·대표를 따르려고 목록을 다시 불러온 뒤 끝난다 */
export function addRegionMutationOptions(queryClient) {
  return {
    mutationKey: [...MUTATION_KEY, 'add'],
    mutationFn: ({ regionId }) => addMyRegion(regionId),
    onSuccess: (added) =>
      Promise.all([refetchMine(queryClient), added.isPrimary && invalidateFeed(queryClient)]),
    onError: (error) => refetchIfStale(queryClient, error),
  };
}

/** 대표 동네 변경. 응답이 바뀐 뒤 목록 전체라 그대로 넣는다 */
export function setPrimaryRegionMutationOptions(queryClient) {
  return {
    mutationKey: [...MUTATION_KEY, 'primary'],
    mutationFn: ({ memberRegionId }) => setPrimaryMyRegion(memberRegionId),
    onSuccess: (list) => {
      queryClient.setQueryData(regionKeys.mine(), list);
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: memberKeys.me(), exact: true }),
        invalidateFeed(queryClient),
      ]);
    },
    onError: (error) => refetchIfStale(queryClient, error),
  };
}

/**
 * 동네 삭제. variables { memberRegionId, isPrimary }(isPrimary는 누른 시점의 목록 값, 피드 무효화 판단에만 쓴다).
 * 대표 승계는 서버가 하므로 목록을 다시 불러온 뒤 끝난다
 */
export function deleteRegionMutationOptions(queryClient) {
  return {
    mutationKey: [...MUTATION_KEY, 'delete'],
    mutationFn: ({ memberRegionId }) => deleteMyRegion(memberRegionId),
    onSuccess: (_data, { isPrimary }) =>
      Promise.all([refetchMine(queryClient), isPrimary && invalidateFeed(queryClient)]),
    onError: (error) => refetchIfStale(queryClient, error),
  };
}

export function useLocateRegion() {
  return useMutation(locateRegionMutationOptions());
}

export function useVerifyRegion() {
  return useMutation(verifyRegionMutationOptions(useQueryClient()));
}

export function useAddRegion() {
  return useMutation(addRegionMutationOptions(useQueryClient()));
}

export function useSetPrimaryRegion() {
  return useMutation(setPrimaryRegionMutationOptions(useQueryClient()));
}

export function useDeleteRegion() {
  return useMutation(deleteRegionMutationOptions(useQueryClient()));
}

/**
 * 동네 뮤테이션이 하나라도 진행 중인지. 대표 변경 직후 목록이 갱신되기 전에 다른 버튼을 누르지 않게
 * 목록 전체를 막는 데 쓴다(뮤테이션은 목록 갱신까지 끝나야 끝난다)
 */
export function useRegionMutating() {
  return useIsMutating({ mutationKey: MUTATION_KEY }) > 0;
}
