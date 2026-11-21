import { useSearchParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import {
  PAYMENT_KIND,
  readPendingPayment,
  readPendingPaymentKind,
} from '../features/payment/pendingPayment';
import { isWindowClosedCode } from '../features/payment/tossPayment';
import { ButtonLink, ErrorState } from '../shared/ui';

/** SCR-07 결제 실패 복귀. 승인을 부르지 않았으므로 대여(연장료 결제면 연장, REQ-038)는 결제 대기로 남아 있다. */
export default function PaymentFailPage() {
  const [searchParams] = useSearchParams();
  const isClosed = isWindowClosedCode(searchParams.get('code'));
  const rentalId = readPendingPayment();
  const isExtension = readPendingPaymentKind() === PAYMENT_KIND.EXTENSION;

  return (
    <>
      <PageHeader title="결제 실패" />
      <ErrorState
        title={
          isClosed
            ? '결제를 취소했어요'
            : isExtension
              ? '연장료를 결제하지 못했어요'
              : '결제하지 못했어요'
        }
        message={
          isClosed
            ? isExtension
              ? '연장 결제 시간 안에 대여 상세에서 다시 결제할 수 있어요. 지나면 연장이 취소돼요.'
              : '결제 시간 안에 대여 상세에서 다시 결제할 수 있어요.'
            : (searchParams.get('message') ?? '잠시 후 다시 시도해 주세요.')
        }
      />
      <div className="flex justify-center">
        <ButtonLink
          to={rentalId ? `/rentals/${rentalId}` : '/me/rentals'}
          variant="secondary"
          replace
        >
          {rentalId ? '대여 상세로' : '빌린 내역으로'}
        </ButtonLink>
      </div>
    </>
  );
}
