import { useIsMutating, useMutation, useQueryClient } from '@tanstack/react-query';

import { rentalKeys } from '../rental/queries';

import { confirmPayment } from './api';
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
      ]),
  });
}
