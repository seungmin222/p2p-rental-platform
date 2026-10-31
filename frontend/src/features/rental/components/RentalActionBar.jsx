import { useEffect, useId, useState } from 'react';

import BottomActionBar from '../../../app/layout/BottomActionBar';
import { parseServerDateTime } from '../../../shared/lib/format';
import { Button, ConfirmModal, toast } from '../../../shared/ui';
import { usePayRental } from '../../payment/queries';
import { PAYMENT_ENABLED } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useConfirmReturn, useRespondCancelAgreement } from '../queries';
import { getRentalActions, RENTAL_ACTION as A } from '../rentalActions';
import AbnormalSheet from './AbnormalSheet';
import CancelModal from './CancelModal';
import { FormError } from './FormParts';
import HandoverSheet from './HandoverSheet';
import ReturnRequestSheet from './ReturnRequestSheet';

/** 결제 만료까지 남은 초(1초마다 갱신). 만료 시각이 없으면 null */
function usePaymentSecondsLeft(expiresAt) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!expiresAt) {
      return undefined;
    }
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [expiresAt]);
  const expiry = parseServerDateTime(expiresAt)?.getTime();
  return expiry == null ? null : Math.max(0, Math.floor((expiry - now) / 1000));
}

/** 결제 만료까지 남은 시간 "12:04" */
function PaymentCountdown({ seconds }) {
  if (seconds == null) {
    return null;
  }
  if (seconds === 0) {
    return <p className="text-center text-xs text-danger">결제 시간이 지났어요</p>;
  }
  const mm = String(Math.floor(seconds / 60)).padStart(2, '0');
  const ss = String(seconds % 60).padStart(2, '0');
  return (
    <p className="text-center text-xs text-ink-muted">
      결제 시간이{' '}
      <b className="text-danger tabular-nums">
        {mm}:{ss}
      </b>{' '}
      남았어요. 지나면 예약이 자동 취소돼요
    </p>
  );
}

/** 양측 동의 취소 요청에 동의·거절 */
function RespondModal({ rental, action, onClose }) {
  const mutation = useRespondCancelAgreement(rental.rentalId);
  const isAccept = action === 'ACCEPT';

  function handleConfirm() {
    mutation.mutate(
      { agreementId: rental.activeCancelAgreement.agreementId, action },
      {
        onSuccess: () => {
          onClose();
          toast.show({
            message: isAccept ? '취소에 동의했어요. 예약이 취소됐어요' : '취소 요청을 거절했어요',
          });
        },
      },
    );
  }

  return (
    <ConfirmModal
      open
      onClose={onClose}
      onConfirm={handleConfirm}
      title={isAccept ? '취소 요청에 동의할까요?' : '취소 요청을 거절할까요?'}
      confirmLabel={isAccept ? '동의' : '거절'}
      cancelLabel="닫기"
      danger={isAccept}
      isLoading={mutation.isPending}
    >
      <div className="flex flex-col gap-2">
        <p>
          {isAccept
            ? `동의하면 예약이 취소돼요.${PAYMENT_ENABLED ? ' 결제 금액은 전액 환불돼요.' : ''}`
            : '거절하면 예약은 그대로 유지돼요.'}
        </p>
        {mutation.error && <FormError message={getErrorMessage(mutation.error)} />}
      </div>
    </ConfirmModal>
  );
}

/** 상태 확인 '정상'. 사진 없이 { result: 'NORMAL' } */
function NormalConfirmModal({ rental, onClose }) {
  const mutation = useConfirmReturn(rental.rentalId);

  function handleConfirm() {
    mutation.mutate(
      { request: { result: 'NORMAL' }, images: [] },
      {
        onSuccess: (result) => {
          onClose();
          // 보증금 반환이 실패했거나 결과를 모르면 200 + RETURNED다(재시도 배치가 완료한다)
          toast.show({
            message:
              result.status === 'COMPLETED'
                ? '정상으로 확인했어요. 거래가 완료됐어요'
                : '정상으로 확인했어요. 보증금 반환을 처리하고 있어요',
          });
        },
      },
    );
  }

  return (
    <ConfirmModal
      open
      onClose={onClose}
      onConfirm={handleConfirm}
      title="정상으로 확인할까요?"
      confirmLabel="정상 확인"
      cancelLabel="닫기"
      isLoading={mutation.isPending}
    >
      <div className="flex flex-col gap-2">
        <p>
          돌려받은 물품에 이상이 없으면 확인해 주세요.
          {PAYMENT_ENABLED ? ' 보증금이 반환되고 거래가 완료돼요.' : ' 확인하면 거래가 완료돼요.'}
        </p>
        {mutation.error && <FormError message={getErrorMessage(mutation.error)} />}
      </div>
    </ConfirmModal>
  );
}

/** 열린 모달·시트(한 번에 하나). 열린 동안만 렌더해 폼이 매번 새로 시작한다. */
function ActionDialog({ type, rental, today, onClose }) {
  switch (type) {
    case A.CANCEL:
      return <CancelModal mode="cancel" rental={rental} today={today} onClose={onClose} />;
    case A.REQUEST_CANCEL_AGREEMENT:
      return <CancelModal mode="agreement" rental={rental} today={today} onClose={onClose} />;
    case A.ACCEPT_CANCEL_AGREEMENT:
      return <RespondModal rental={rental} action="ACCEPT" onClose={onClose} />;
    case A.REJECT_CANCEL_AGREEMENT:
      return <RespondModal rental={rental} action="REJECT" onClose={onClose} />;
    case A.HANDOVER:
      return <HandoverSheet rental={rental} onClose={onClose} />;
    case A.RETURN_REQUEST:
      return <ReturnRequestSheet rental={rental} onClose={onClose} />;
    case A.CONFIRM_ABNORMAL:
      return <AbnormalSheet rental={rental} onClose={onClose} />;
    case A.CONFIRM_NORMAL:
      return <NormalConfirmModal rental={rental} onClose={onClose} />;
    default:
      return null;
  }
}

/**
 * 대여 상세 하단 바. 무엇을 보여 줄지는 getRentalActions(표 하나)만 보고 정한다.
 * 비활성 버튼은 사유를 버튼 아래에 쓰고 aria-describedby로 잇는다. "결제하기"는 모달 없이 토스 결제창을 연다.
 */
export default function RentalActionBar({ rental, returnInspection, today }) {
  const reasonId = useId();
  const [openType, setOpenType] = useState(null);
  const { actions, notice } = getRentalActions(rental, returnInspection, today);
  const payMutation = usePayRental(rental.rentalId);
  const secondsLeft = usePaymentSecondsLeft(notice?.paymentExpiresAt);

  // 열어 둔 액션이 표에서 사라졌으면(다시 불러온 상태가 바뀜) 그 모달은 그대로 두고 닫힐 때까지 기다린다
  const dialog = openType && (
    <ActionDialog type={openType} rental={rental} today={today} onClose={() => setOpenType(null)} />
  );

  if (actions.length === 0 && !notice) {
    return dialog;
  }

  const reasons = actions.filter((action) => action.disabledReason);

  return (
    <>
      <BottomActionBar className="flex flex-col gap-2">
        {notice?.paymentExpiresAt && <PaymentCountdown seconds={secondsLeft} />}
        {notice?.text && (
          <p
            className={`text-center text-caption ${notice.tone === 'danger' ? 'font-semibold text-danger' : 'text-ink-muted'}`}
          >
            {notice.text}
          </p>
        )}
        {actions.length > 0 && (
          <div className="flex gap-2">
            {actions.map((action) => (
              <Button
                key={action.type}
                variant={action.variant}
                className="min-w-0 flex-1 px-3"
                disabled={
                  action.disabledReason != null || (action.type === A.PAY && secondsLeft === 0)
                }
                isLoading={action.type === A.PAY && payMutation.isPending}
                aria-describedby={action.disabledReason ? reasonId : undefined}
                onClick={
                  action.type === A.PAY
                    ? () => payMutation.mutate()
                    : () => setOpenType(action.type)
                }
              >
                {action.label}
              </Button>
            ))}
          </div>
        )}
        {reasons.length > 0 && (
          <p id={reasonId} className="text-center text-xs text-ink-muted">
            {reasons.map((action) => action.disabledReason).join(' · ')}
          </p>
        )}
      </BottomActionBar>
      {dialog}
    </>
  );
}
