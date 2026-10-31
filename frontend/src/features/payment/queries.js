import { useIsMutating, useMutation, useQueryClient } from '@tanstack/react-query';

import { rentalKeys } from '../rental/queries';

import { confirmPayment } from './api';
import { runOnce } from './paymentResult';
import { paymentKeys } from './startPayment';

/** 이 대여의 주문 생성·결제창이 진행 중인가(어느 화면에서 시작했든) */
export function useIsPaymentStarting(rentalId) {
  return useIsMutating({ mutationKey: paymentKeys.start(rentalId) }) > 0;
}

/** variables: { paymentKey, orderId, amount }. paymentKey가 캐시에 오래 남지 않게 gcTime 0 */
export function useConfirmPayment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) => runOnce(`confirm:${body.orderId}`, () => confirmPayment(body)),
    gcTime: 0,
    // 승인·실패 모두 대여 상태가 바뀔 수 있다
    onSettled: () => queryClient.invalidateQueries({ queryKey: rentalKeys.all }),
  });
}
