import { QueryClient, QueryObserver } from '@tanstack/query-core';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { memberKeys, refetchIfInvalidated } from './queries';

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
