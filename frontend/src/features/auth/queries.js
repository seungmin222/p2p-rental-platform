import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { useAuthStore } from '../../shared/api';
import { fetchMe, login, logout, signup } from './api';

export const memberKeys = {
  // 내 동네 목록(['members', 'me', 'regions'])은 이 키의 하위다(feat/fe-region)
  me: () => ['members', 'me'],
};

function meQueryOptions() {
  return { queryKey: memberKeys.me(), queryFn: fetchMe, staleTime: 10_000 };
}

/**
 * 내 정보(닉네임·프로필 이미지·대표 동네 요약).
 * - 비회원 화면(홈·검색)에서 같이 쓰면 enabled를 로그인 여부로 넘긴다 — 토큰이 없을 때 부르면 401 → 재발급 실패 → 로그인 화면 이동이다.
 * - refetchOnMount를 false로 넘기면 이미 받은 데이터가 있을 때 화면에 들어올 때 다시 부르지 않는다. 로그아웃은 홈으로 먼저 이동한 뒤
 *   AT를 지우는데(useLogout), 그 사이에 홈이 내 정보를 다시 부르면 서버가 이미 세션을 끝냈을 때 401 → 로그인 화면으로 가 버린다.
 */
export function useMe({ enabled = true, refetchOnMount = true } = {}) {
  return useQuery({ ...meQueryOptions(), enabled, refetchOnMount });
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
 */
export function useLogout(leaveRef) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: async () => {
      await leaveRef?.current?.();
      clearLocalSession(queryClient);
    },
  });
}

/** 로컬 로그인 상태(AT·캐시)를 지운다. 로그아웃 API가 성공한 뒤에만 부른다(실패면 쿠키가 남아 다음 시작 때 복원되므로) */
export function clearLocalSession(queryClient) {
  useAuthStore.getState().clearAccessToken();
  queryClient.clear();
}
