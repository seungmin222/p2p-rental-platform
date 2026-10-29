import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { flattenPages, getNextPageParam } from '../../shared/lib/listParams';
import { fetchItem, fetchMyItems, toItemSummary, toMyItem } from './api';

export const itemKeys = {
  detail: (itemId) => ['items', itemId],
  mine: () => ['items', 'me'],
  mineTab: (tab) => ['items', 'me', { tab }],
};

/** 물품 상세를 요약 모양으로(features/item/api.js toItemSummary) */
export function useItemSummary(itemId) {
  return useQuery({
    queryKey: itemKeys.detail(itemId),
    queryFn: () => fetchItem(itemId),
    select: toItemSummary,
    staleTime: 10_000,
    // 경로의 id가 숫자가 아니면 부르지 않는다(화면이 바로 "찾을 수 없어요")
    enabled: Number.isInteger(itemId),
  });
}

// 모듈 함수라 참조가 바뀌지 않아, 데이터가 그대로면 이전 결과를 돌려준다
function selectMyItems(data) {
  return flattenPages(data, toMyItem);
}

/** REQ-020 내 물품(탭별, page/hasNext). data는 지금까지 받은 페이지를 합친 카드 배열 */
export function useMyItems(tab) {
  return useInfiniteQuery({
    queryKey: itemKeys.mineTab(tab),
    queryFn: ({ pageParam }) => fetchMyItems({ tab, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam,
    select: selectMyItems,
  });
}
