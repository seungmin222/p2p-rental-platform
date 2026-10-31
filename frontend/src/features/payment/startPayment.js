import { createErrorMessageGetter } from '../../shared/lib/formErrors';
import { toast } from '../../shared/ui';
import { rentalKeys } from '../rental/queries';

import { createPaymentOrder } from './api';
import { savePendingPayment } from './pendingPayment';
import { isWindowClosedCode, openTossPaymentWindow } from './tossPayment';

export const paymentKeys = {
  starts: () => ['payments', 'start'],
  start: (rentalId) => ['payments', 'start', rentalId],
};

const getOrderErrorMessage = createErrorMessageGetter({
  PAYMENT_EXPIRED: '결제 시간이 지났어요. 다시 예약해 주세요',
  PAYMENT_RENTER_ONLY: '대여자만 결제할 수 있어요',
  RENTAL_INVALID_TRANSITION: '상태가 바뀌었어요. 다시 불러왔어요',
  RENTAL_NOT_FOUND: '대여 정보를 볼 수 없어요',
});

/** 결제창을 닫았거나 주문을 만들지 못한 경우. 대여는 결제 대기로 남아 대여 상세에서 다시 결제할 수 있다. */
function handleStartPaymentError(queryClient, rentalId, error) {
  queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) });
  if (isWindowClosedCode(error?.code)) {
    toast.show({ message: '결제를 취소했어요. 시간 안에 다시 결제할 수 있어요' });
    return;
  }
  toast.show({ message: getOrderErrorMessage(error), tone: 'danger' });
}

/**
 * 주문을 만들고 결제창을 연다. 예약 화면은 곧바로 벗어나므로 컴포넌트가 아니라 뮤테이션 캐시에서 실행하고,
 * 대여 상세는 같은 키로 진행 여부를 본다(useIsPaymentStarting). 진행 중이면 아무것도 하지 않아 요청과 실패 처리가 한 번씩만 일어난다.
 */
export function startPayment(queryClient, rentalId) {
  const mutationKey = paymentKeys.start(rentalId);
  if (queryClient.isMutating({ mutationKey }) > 0) {
    return Promise.resolve();
  }
  return queryClient
    .getMutationCache()
    .build(queryClient, {
      mutationKey,
      mutationFn: async () => {
        const order = await createPaymentOrder(rentalId);
        savePendingPayment(rentalId);
        await openTossPaymentWindow(order);
      },
      onError: (error) => handleStartPaymentError(queryClient, rentalId, error),
    })
    .execute()
    .catch(() => {
      // onError에서 처리했다
    });
}

/** 진행 중으로 남은 결제 시작을 지운다. 결제창으로 페이지를 옮긴 뒤 돌아오면 그 Promise는 끝나지 않는다. */
export function releaseStartingPayments(queryClient) {
  const cache = queryClient.getMutationCache();
  for (const mutation of cache.findAll({ mutationKey: paymentKeys.starts(), status: 'pending' })) {
    cache.remove(mutation);
  }
}

/** 토스 페이지에서 뒤로 가기로 bfcache 복원되면 결제하기가 로딩에 멈추지 않게 진행 상태를 푼다 */
export function watchPageRestore(queryClient, target = window) {
  target.addEventListener('pageshow', (event) => {
    if (event.persisted) {
      releaseStartingPayments(queryClient);
      queryClient.invalidateQueries({ queryKey: rentalKeys.all });
    }
  });
}
