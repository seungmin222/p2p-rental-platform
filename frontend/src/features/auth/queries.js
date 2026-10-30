import { useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useAuthStore } from '../../shared/api';
import { fetchMe, login, logout, signup } from './api';

export const memberKeys = {
  // 내 동네 목록(['members', 'me', 'regions'])은 이 키의 하위다(feat/fe-region)
  me: () => ['members', 'me'],
};

const LOGOUT_MUTATION_KEY = ['auth', 'logout'];

function meQueryOptions() {
  return { queryKey: memberKeys.me(), queryFn: fetchMe, staleTime: 10_000 };
}

/**
 * 내 정보(닉네임·프로필 이미지·대표 동네 요약).
 * - 비회원 화면(홈·검색)에서 같이 쓰면 enabled를 로그인 여부로 넘긴다 — 토큰이 없을 때 부르면 401 → 재발급 실패 → 로그인 화면 이동이다.
 * - refetchOnMount에 refetchIfInvalidated를 넘기면 이미 받은 데이터는 무효화됐을 때만 다시 받는다.
 */
export function useMe({ enabled = true, refetchOnMount = true } = {}) {
  return useQuery({ ...meQueryOptions(), enabled, refetchOnMount });
}

/**
 * 홈·검색의 refetchOnMount. 로그아웃은 홈으로 먼저 이동한 뒤 AT를 지우는데(useLogout), 그 사이에 홈이 내 정보를 다시 부르면
 * 서버가 이미 세션을 끝냈을 때 401 → 로그인 화면으로 가 버린다. 그래서 stale이라는 이유만으로는 다시 받지 않는다.
 * 대표 동네가 바뀌면 무효화되는데(동네 뮤테이션) /regions에는 구독자가 없어 표시만 남으므로, 그 표시가 있을 때만 다시 받는다.
 */
export function refetchIfInvalidated(query) {
  return query.state.isInvalidated;
}

/**
 * 로그인. 성공하면 이전 사용자의 캐시를 지우고 새 AT를 저장한다(이동은 화면이 정한다).
 * variables에 비밀번호가 들어가므로 gcTime 0: 화면이 내려가면 MutationCache에서 바로 지운다(성공 시는 clear()가 지움).
 */
export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: login,
    gcTime: 0,
    onSuccess: ({ accessToken }) => {
      queryClient.clear();
      useAuthStore.getState().setAccessToken(accessToken);
    },
  });
}

/**
 * redirect가 없을 때 로그인 후 갈 곳: 대표 동네가 없으면 동네 설정, 있으면 홈. 내 정보 조회가 실패하면 홈.
 * 동네 화면에는 로그인 직후라는 표시(state.fromLogin)를 넘긴다(읽는 쪽은 feat/fe-region).
 */
export async function fetchPostLoginDestination(queryClient) {
  try {
    const me = await queryClient.fetchQuery(meQueryOptions());
    if (me?.primaryRegion == null) {
      return { to: '/regions', state: { fromLogin: true } };
    }
  } catch {
    // 내 정보를 못 받아도 로그인은 됐으므로 홈으로
  }
  return { to: '/', state: undefined };
}

/** 회원가입. variables에 비밀번호가 들어가므로 gcTime 0(useLogin과 같은 이유) */
export function useSignup() {
  return useMutation({ mutationFn: signup, gcTime: 0 });
}

/**
 * 로그아웃. 서버가 성공하면 화면이 내려갔어도 반드시 로컬(AT·캐시)을 지운다 — 정리를 훅 옵션 onSuccess에 두는 이유는
 * 컴포넌트가 내려가면 mutate에 넘긴 호출별 onSuccess가 불리지 않아서다.
 * leaveRef.current는 화면이 떠 있는 동안만 "그 화면을 떠나는 함수"다(LogoutButton). 있으면 먼저 떠난 뒤 지운다
 * (/me는 RequireAuth 안이라 AT를 먼저 지우면 /login?redirect=/me로 간다). 이미 내려갔으면 이동 없이 정리만 한다.
 * 진행 중인 내 정보 요청은 먼저 끊는다 — 서버가 세션을 끝낸 뒤 401로 돌아오면 재발급 실패 → 로그인 화면이다.
 * 로그아웃이 실패하면 끊은 요청을 다시 받는다.
 */
export function logoutMutationOptions(queryClient, leaveRef) {
  return {
    mutationKey: LOGOUT_MUTATION_KEY,
    mutationFn: logout,
    onMutate: () => queryClient.cancelQueries({ queryKey: memberKeys.me(), exact: true }),
    onSuccess: async () => {
      await leaveRef?.current?.();
      clearLocalSession(queryClient);
    },
    onError: () => queryClient.invalidateQueries({ queryKey: memberKeys.me(), exact: true }),
  };
}

export function useLogout(leaveRef) {
  const queryClient = useQueryClient();
  return useMutation(logoutMutationOptions(queryClient, leaveRef));
}

/** 로그아웃 요청부터 AT를 지울 때까지 true. 이 동안 들어온 화면은 내 정보를 부르지 않는다(useFeedRegion) */
export function useIsLoggingOut() {
  return useIsMutating({ mutationKey: LOGOUT_MUTATION_KEY }) > 0;
}

/** 로컬 로그인 상태(AT·캐시)를 지운다. 로그아웃 API가 성공했거나 재발급이 실패했을 때만 부른다(그 밖에는 쿠키가 남아 다음 시작 때 복원되므로) */
export function clearLocalSession(queryClient) {
  useAuthStore.getState().clearAccessToken();
  queryClient.clear();
}
