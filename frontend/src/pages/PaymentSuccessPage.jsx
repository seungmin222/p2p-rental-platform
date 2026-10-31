import { useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import { clearPendingPayment, readPendingPayment } from '../features/payment/pendingPayment';
import {
  CONFIRM_ACTION,
  getConfirmOutcome,
  parseSuccessParams,
} from '../features/payment/paymentResult';
import { useConfirmPayment } from '../features/payment/queries';
import { Button, ButtonLink, ErrorState, Spinner, toast } from '../shared/ui';

/** SCR-07 결제 성공 복귀. 금액 검증은 서버가 하고, 승인 결과에 따라 대여 상세로 보낸다. */
export default function PaymentSuccessPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const body = useMemo(() => parseSuccessParams(searchParams), [searchParams]);
  const { mutate, data: confirmed, error, isPending } = useConfirmPayment();

  useEffect(() => {
    if (body) {
      mutate(body);
    }
  }, [body, mutate]);

  useEffect(() => {
    if (confirmed) {
      clearPendingPayment();
      if (confirmed.status === 'APPROVED') {
        toast.show({ message: '결제를 완료했어요' });
      }
      // 복귀 주소에 paymentKey가 있어 기록에 남기지 않는다
      navigate(`/rentals/${confirmed.rentalId}`, { replace: true });
    }
  }, [confirmed, navigate]);

  if (!body) {
    return (
      <>
        <PageHeader title="결제 확인" />
        <ErrorState title="결제 정보가 올바르지 않아요" message="결제를 다시 진행해 주세요." />
        <div className="flex justify-center">
          <ButtonLink to="/me/rentals" variant="secondary">
            빌린 내역으로
          </ButtonLink>
        </div>
      </>
    );
  }

  if (error) {
    const outcome = getConfirmOutcome(error);
    const rentalId = readPendingPayment();
    return (
      <>
        <PageHeader title="결제 확인" />
        <ErrorState title={outcome.title} message={outcome.message} />
        <div className="flex justify-center gap-2">
          {outcome.action === CONFIRM_ACTION.RETRY && (
            <Button onClick={() => mutate(body)}>다시 확인</Button>
          )}
          {outcome.action === CONFIRM_ACTION.TO_HOME ? (
            <ButtonLink to="/" variant="secondary" replace>
              홈으로
            </ButtonLink>
          ) : (
            <ButtonLink
              to={rentalId ? `/rentals/${rentalId}` : '/me/rentals'}
              variant="secondary"
              replace
            >
              {rentalId ? '대여 상세로' : '빌린 내역으로'}
            </ButtonLink>
          )}
        </div>
      </>
    );
  }

  return (
    <>
      <PageHeader title="결제 확인" />
      <div
        role="status"
        aria-busy={isPending}
        className="flex flex-col items-center gap-3 py-16 text-center"
      >
        <Spinner />
        <p className="text-body">결제를 확인하고 있어요</p>
      </div>
    </>
  );
}
