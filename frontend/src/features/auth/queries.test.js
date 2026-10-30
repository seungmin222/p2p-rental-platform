import { MutationObserver, QueryClient, QueryObserver } from '@tanstack/query-core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useAuthStore } from '../../shared/api';
import { logout } from './api';
import { logoutMutationOptions, memberKeys, refetchIfInvalidated } from './queries';

vi.mock('./api', () => ({ logout: vi.fn() }));

const ME = { memberId: 1, primaryRegion: { regionId: 1101, regionName: '송도1동' } };

// useFeedRegion이 useMe에 넘기는 옵션과 같다. 화면이 마운트되는 것은 observer 구독으로 흉내 낸다
function mountObserver(queryClient, queryFn) {
  const observer = new QueryObserver(queryClient, {
    queryKey: memberKeys.me(),
    queryFn,
    staleTime: 0,
    refetchOnMount: refetchIfInvalidated,
  });
  return observer.subscribe(() => {});
}

describe('refetchIfInvalidated (홈·검색의 내 정보)', () => {
  const unsubscribes = [];

  function mount(queryClient, queryFn) {
    unsubscribes.push(mountObserver(queryClient, queryFn));
  }

  afterEach(() => {
    unsubscribes.splice(0).forEach((unsubscribe) => unsubscribe());
  });

  it('무효화 없이 마운트하면 stale이어도 다시 받지 않는다 — 로그아웃 경로', () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(memberKeys.me(), ME);
    const queryFn = vi.fn().mockResolvedValue(ME);

    mount(queryClient, queryFn);

    expect(queryFn).not.toHaveBeenCalled();
  });

  it('무효화된 뒤 마운트하면 다시 받는다 — 대표 동네 변경 뒤 홈으로', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(memberKeys.me(), ME);
    // /regions에는 내 정보 구독자가 없어 무효화 표시만 남는다
    await queryClient.invalidateQueries({ queryKey: memberKeys.me(), exact: true });
    const queryFn = vi.fn().mockResolvedValue(ME);

    mount(queryClient, queryFn);

    expect(queryFn).toHaveBeenCalledTimes(1);
  });

  it('데이터가 없으면 처음 불러온다', () => {
    const queryFn = vi.fn().mockResolvedValue(ME);

    mount(new QueryClient(), queryFn);

    expect(queryFn).toHaveBeenCalledTimes(1);
  });
});

describe('logoutMutationOptions', () => {
  beforeEach(() => {
    useAuthStore.getState().setAccessToken('token');
  });

  function runLogout(queryClient, leaveRef) {
    return new MutationObserver(queryClient, logoutMutationOptions(queryClient, leaveRef)).mutate();
  }

  it('홈으로 떠나는 동안은 로그아웃 중이고 AT도 남아 있다 — 그 뒤에 지운다', async () => {
    logout.mockResolvedValue(null);
    const queryClient = new QueryClient();
    let seen = null;
    const leaveRef = {
      current: () => {
        seen = {
          mutating: queryClient.isMutating({ mutationKey: ['auth', 'logout'] }),
          token: useAuthStore.getState().accessToken,
        };
      },
    };

    await runLogout(queryClient, leaveRef);

    expect(seen).toEqual({ mutating: 1, token: 'token' });
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('진행 중인 내 정보 요청을 먼저 끊는다 — 401로 돌아와 로그인 화면으로 가지 않게', async () => {
    logout.mockResolvedValue(null);
    const queryClient = new QueryClient();
    let signal = null;
    queryClient
      .fetchQuery({
        queryKey: memberKeys.me(),
        queryFn: (context) => {
          signal = context.signal;
          return new Promise(() => {});
        },
      })
      .catch(() => {});

    await runLogout(queryClient, { current: null });

    expect(signal.aborted).toBe(true);
  });

  it('로그아웃이 실패하면 AT를 두고 내 정보를 다시 받게 표시한다', async () => {
    logout.mockRejectedValue(new Error('network'));
    const queryClient = new QueryClient();
    queryClient.setQueryData(memberKeys.me(), ME);

    await expect(runLogout(queryClient, { current: null })).rejects.toThrow('network');

    expect(useAuthStore.getState().accessToken).toBe('token');
    expect(queryClient.getQueryState(memberKeys.me()).isInvalidated).toBe(true);
  });
});
