import { createErrorMessageGetter } from '../../shared/lib/formErrors';
import { toast } from '../../shared/ui';
import { rentalKeys } from '../rental/queries';

import { createPaymentOrder } from './api';
import { savePendingPayment } from './pendingPayment';
import { runOnce } from './paymentResult';
import { isWindowClosedCode, openTossPaymentWindow } from './tossPayment';

const getOrderErrorMessage = createErrorMessageGetter({
  PAYMENT_EXPIRED: '결제 시간이 지났어요. 다시 예약해 주세요',
  PAYMENT_RENTER_ONLY: '대여자만 결제할 수 있어요',
  RENTAL_INVALID_TRANSITION: '상태가 바뀌었어요. 다시 불러왔어요',
  RENTAL_NOT_FOUND: '대여 정보를 볼 수 없어요',
});

/** 주문을 만들고 결제창을 연다. 예약 화면은 곧바로 벗어나므로 컴포넌트 수명과 무관한 함수로 둔다. */
export function startPayment(rentalId) {
  return runOnce(`start:${rentalId}`, async () => {
    const order = await createPaymentOrder(rentalId);
    savePendingPayment(rentalId);
    await openTossPaymentWindow(order);
  });
}

/** 결제창을 닫았거나 주문을 만들지 못한 경우. 대여는 결제 대기로 남아 대여 상세에서 다시 결제할 수 있다. */
export function handleStartPaymentError(queryClient, rentalId, error) {
  queryClient.invalidateQueries({ queryKey: rentalKeys.detail(rentalId) });
  if (isWindowClosedCode(error?.code)) {
    toast.show({ message: '결제를 취소했어요. 시간 안에 다시 결제할 수 있어요' });
    return;
  }
  toast.show({ message: getOrderErrorMessage(error), tone: 'danger' });
}
