import { useMutation, useQueryClient } from '@tanstack/react-query';

import { rentalKeys } from '../rental/queries';

import { confirmPayment } from './api';
import { runOnce } from './paymentResult';
import { handleStartPaymentError, startPayment } from './startPayment';

/** 대여 상세의 결제하기 */
export function usePayRental(rentalId) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => startPayment(rentalId),
    onError: (error) => handleStartPaymentError(queryClient, rentalId, error),
  });
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
