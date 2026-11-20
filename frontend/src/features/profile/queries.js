import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { flattenPages, getNextPageParam } from '../../shared/lib/listParams';
import { toItemCard } from '../item/api';
import { fetchMemberItems, fetchMemberProfile } from './api';

export const profileKeys = {
  // 받은 후기 ['members', memberId, 'reviews'](review/queries.js)와 같은 줄기. 내 정보 ['members', 'me']와는 둘째 자리가 달라 겹치지 않는다
  detail: (memberId) => ['members', memberId],
  items: (memberId) => ['members', memberId, 'items'],
};

/** 사용자 프로필. memberId가 null(잘못된 경로)이면 부르지 않는다 */
export function useMemberProfile(memberId) {
  return useQuery({
    queryKey: profileKeys.detail(memberId),
    queryFn: () => fetchMemberProfile(memberId),
    staleTime: 30_000,
    enabled: memberId != null,
  });
}

/** 등록 물품(page/hasNext, 무한 스크롤). data는 지금까지 받은 페이지를 합친 카드 배열(toItemCard) */
export function useMemberItems(memberId, { enabled = true } = {}) {
  return useInfiniteQuery({
    queryKey: profileKeys.items(memberId),
    queryFn: ({ pageParam }) => fetchMemberItems(memberId, pageParam),
    initialPageParam: 0,
    getNextPageParam,
    select: (data) => flattenPages(data, toItemCard),
    staleTime: 30_000,
    enabled: enabled && memberId != null,
  });
}
