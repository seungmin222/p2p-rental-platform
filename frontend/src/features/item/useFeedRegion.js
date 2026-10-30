import { useAuthStore } from '../../shared/api';
import { useMe } from '../auth/queries';
import { useGuestRegionStore } from '../region/guestRegionStore';
import { resolveFeedRegion } from './homeRegion';

/**
 * 홈·검색의 동네(결정 규칙은 homeRegion.js). retry는 로그인했는데 내 정보를 못 받았을 때 다시 부른다.
 * 내 정보는 로그인했을 때만 부른다(비회원이 부르면 401 → 재발급 실패 → 로그인 화면 이동).
 */
export function useFeedRegion() {
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);
  // 로그아웃 직후 홈이 내 정보를 다시 부르지 않게 한다(useMe 설명). 대표 동네가 바뀌면 동네 뮤테이션이 이 쿼리를 무효화한다
  const meQuery = useMe({ enabled: isLoggedIn, refetchOnMount: false });
  const guestRegion = useGuestRegionStore((state) => state.guestRegion);
  const region = resolveFeedRegion({
    isLoggedIn,
    me: meQuery.data,
    isMePending: meQuery.isPending,
    isMeError: meQuery.isError,
    guestRegion,
  });
  return { ...region, retry: meQuery.refetch };
}
