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
  });
}
