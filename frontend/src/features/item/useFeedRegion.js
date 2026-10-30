import { useQueryClient } from '@tanstack/react-query';

import { useAuthStore } from '../../shared/api';
import { memberKeys, refetchIfInvalidated, useIsLoggingOut, useMe } from '../auth/queries';
import { useGuestRegionStore } from '../region/guestRegionStore';
import { resolveFeedRegion } from './homeRegion';

/**
 * 홈·검색의 동네(결정 규칙은 homeRegion.js). retry는 로그인했는데 내 정보를 못 받았을 때 다시 부른다.
 * 내 정보는 로그인했을 때만 부른다(비회원이 부르면 401 → 재발급 실패 → 로그인 화면 이동).
 */
export function useFeedRegion() {
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);
  // 이미 받은 내 정보는 무효화됐을 때만 다시 받는다(useMe 설명). 대표 동네를 바꾸면 동네 뮤테이션이 무효화한다
  // 로그아웃 중에 홈으로 들어오면 받아 둔 내 정보가 없어도(/me에서 로딩·실패) 부르지 않는다
  const isLoggingOut = useIsLoggingOut();
  const meQuery = useMe({
    enabled: isLoggedIn && !isLoggingOut,
    refetchOnMount: refetchIfInvalidated,
  });
  const queryClient = useQueryClient();
  const guestRegion = useGuestRegionStore((state) => state.guestRegion);
  const region = resolveFeedRegion({
    isLoggedIn,
    me: meQuery.data,
    isMePending: meQuery.isPending,
    isMeError: meQuery.isError,
    // 무효화 뒤 다시 받는 동안 이전 대표 동네의 피드를 부르지 않는다. 실패하면 받아 둔 동네를 그대로 쓴다
    isMeOutdated:
      meQuery.isFetching && queryClient.getQueryState(memberKeys.me())?.isInvalidated === true,
    guestRegion,
  });
  return { ...region, retry: meQuery.refetch };
}
