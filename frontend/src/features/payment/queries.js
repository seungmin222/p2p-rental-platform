import {
  useInfiniteQuery,
  useIsMutating,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';

import { flattenPages, getNextPageParam } from '../../shared/lib/listParams';
import { rentalKeys } from '../rental/queries';

import { confirmPayment, fetchPaymentHistory, fetchSettlements } from './api';
import { paymentHistoryKeys } from './keys';
import { toHistoryParams } from './paymentHistory';
import { runOnce } from './paymentResult';
import { paymentKeys } from './startPayment';

/** 이 대여의 주문 생성·결제창이 진행 중인가(어느 화면에서 시작했든) */
export function useIsPaymentStarting(rentalId) {
  return useIsMutating({ mutationKey: paymentKeys.start(rentalId) }) > 0;
}

function isItemCalendarQuery({ queryKey }) {
  return queryKey[0] === 'items' && queryKey[2] === 'calendar';
}

/** variables: { paymentKey, orderId, amount }. paymentKey가 캐시에 오래 남지 않게 gcTime 0 */
export function useConfirmPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => runOnce(`confirm:${body.orderId}`, () => confirmPayment(body)),
    gcTime: 0,
    // 승인·실패 모두 대여 상태와 물품 캘린더 점유가 바뀔 수 있다
    // (연장료 승인 실패·PAYMENT_CANCELED_AFTER_EXPIRY면 결제 대기 연장 점유가 풀린다).
    // 실패 응답에는 itemId가 없어 모든 물품 캘린더(['items', id, 'calendar', ...])를 무효화한다.
    onSettled: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: rentalKeys.all }),
        queryClient.invalidateQueries({ predicate: isItemCalendarQuery }),
        // 결제·보증금 내역(REQ-033)에 새 결제가 생긴다
        queryClient.invalidateQueries({ queryKey: paymentHistoryKeys.all }),
      ]),
  });
}

export { paymentHistoryKeys };

/** REQ-033 결제·보증금 내역. filter: { type, from, to }(paymentHistory.js readHistoryFilter) */
export function usePaymentHistory(filter) {
  return useInfiniteQuery({
    queryKey: paymentHistoryKeys.list(filter),
    queryFn: ({ pageParam }) => fetchPaymentHistory(toHistoryParams(filter, pageParam)),
    initialPageParam: 0,
    getNextPageParam,
    select: flattenPages,
  });
}

// 합계는 페이지마다 같은 값이 오므로 가장 최근에 받은 페이지 것을 쓴다
function selectSettlements(data) {
  const last = data.pages.at(-1);
  return {
    totals: {
      scheduledTotal: last.scheduledTotal,
      heldTotal: last.heldTotal,
      paidTotal: last.paidTotal,
    },
    items: data.pages.flatMap((page) => page.settlements?.items ?? []),
  };
}

/** REQ-039 정산 내역(제공자 본인). 응답의 settlements가 PageResponse라 다음 페이지는 그 안의 page/hasNext로 정한다 */
export function useSettlements() {
  return useInfiniteQuery({
    queryKey: paymentHistoryKeys.settlements(),
    queryFn: ({ pageParam }) => fetchSettlements(pageParam),
    initialPageParam: 0,
    getNextPageParam: (lastPage) => getNextPageParam(lastPage?.settlements),
    select: selectSettlements,
  });
}
