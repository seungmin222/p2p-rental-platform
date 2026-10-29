import { useQuery } from '@tanstack/react-query';

import { fetchItem, toItemSummary } from './api';

export const itemKeys = {
  detail: (itemId) => ['items', itemId],
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
