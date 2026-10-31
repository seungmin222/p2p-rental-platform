import { useId } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';

import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { formatPrice } from '../../../shared/lib/format';
import { Button, Chip, Modal, TextField, toast } from '../../../shared/ui';
import {
  CANCEL_AGREEMENT_EXPIRE_HOURS,
  CANCEL_FEE,
  CANCEL_REASON,
  PAYMENT_ENABLED,
  RENTAL_TEXT_MAX,
} from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useCancelRental, useRequestCancelAgreement } from '../queries';
import { estimateCancelFee, refundOnCancel } from '../rentalData';
import { FormError } from './FormParts';

// 서버 details[].field → 폼 필드
const FIELD_MAP = { reason: 'reason', reasonText: 'reasonText' };

const MODES = {
  // 단독 취소(시작일 전)
  cancel: { title: '예약을 취소할까요?', submitLabel: '예약 취소', success: '예약을 취소했어요' },
  // 양측 동의 취소 요청(시작일부터, 인계 전)
  agreement: {
    title: '취소를 요청할까요?',
    submitLabel: '취소 요청',
    success: '취소를 요청했어요',
  },
};

/**
 * 예약 취소 / 취소 요청 모달. 사유 Chip 4개 중 하나, 기타면 사유 입력 필수.
 * 열 때마다 새로 그린다(부모가 열린 동안만 렌더) — 폼 초기화를 따로 하지 않으려고.
 */
export default function CancelModal({ mode, rental, today, onClose }) {
  const formId = useId();
  const reasonLabelId = useId();
  const config = MODES[mode];
  const cancelMutation = useCancelRental(rental.rentalId);
  const agreementMutation = useRequestCancelAgreement(rental.rentalId);
  const mutation = mode === 'cancel' ? cancelMutation : agreementMutation;
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({ defaultValues: { reason: null, reasonText: '' }, shouldFocusError: true });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });

  const reason = useWatch({ control, name: 'reason' });
  const fee = mode === 'cancel' && PAYMENT_ENABLED ? estimateCancelFee(rental, today) : 0;

  function onSubmit(values) {
    const body = { reason: values.reason };
    if (values.reason === 'OTHER') {
      body.reasonText = values.reasonText.trim();
    }
    mutation.mutate(body, {
      onSuccess: (result) => {
        onClose();
        toast.show({ message: config.success });
        // 단독 취소의 확정 수수료는 응답 기준(결제가 꺼져 있으면 받지 않으므로 보여 주지 않는다)
        if (mode === 'cancel' && PAYMENT_ENABLED && result.cancelFee > 0) {
          toast.show({ message: `취소 수수료 ${formatPrice(result.cancelFee)}이 부과됐어요` });
        }
        // 대여 상세 응답에 환불액이 없어 취소하는 이 순간에만 안내한다
        const refund =
          PAYMENT_ENABLED && mode === 'cancel' ? refundOnCancel(rental.status, result) : 0;
        if (refund > 0) {
          toast.show({ message: `${formatPrice(refund)} 환불을 진행해요` });
        }
      },
      onError: handleError,
    });
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={config.title}
      dismissible={!mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            닫기
          </Button>
          <Button type="submit" form={formId} variant="danger" isLoading={mutation.isPending}>
            {config.submitLabel}
          </Button>
        </>
      }
    >
      <form
        id={formId}
        noValidate
        onSubmit={handleSubmit(onSubmit)}
        className="flex flex-col gap-4"
      >
        {mode === 'agreement' && (
          <p className="text-caption text-ink-muted">
            상대가 동의하면 예약이 취소돼요. {CANCEL_AGREEMENT_EXPIRE_HOURS}시간 안에 응답이 없으면
            요청이 만료돼요.
          </p>
        )}
        <Controller
          name="reason"
          control={control}
          rules={{ required: '취소 사유를 골라 주세요' }}
          render={({ field, fieldState }) => (
            <div className="flex flex-col gap-2">
              <span id={reasonLabelId} className="text-caption font-semibold">
                취소 사유
              </span>
              <div
                role="group"
                aria-labelledby={reasonLabelId}
                className="flex flex-wrap gap-x-2 gap-y-2.5"
              >
                {Object.entries(CANCEL_REASON).map(([code, { label }], index) => (
                  <Chip
                    key={code}
                    ref={index === 0 ? field.ref : undefined}
                    selected={field.value === code}
                    disabled={mutation.isPending}
                    onClick={() => field.onChange(code)}
                  >
                    {label}
                  </Chip>
                ))}
              </div>
              <FormError message={fieldState.error?.message} />
            </div>
          )}
        />
        {reason === 'OTHER' && (
          <TextField
            label="사유"
            required
            multiline
            rows={3}
            maxLength={RENTAL_TEXT_MAX.CANCEL_REASON}
            placeholder="취소하는 이유를 적어 주세요"
            disabled={mutation.isPending}
            error={errors.reasonText?.message}
            {...register('reasonText', {
              // 기타가 아니면 입력란이 사라져도 값이 남으므로 사유로 다시 확인한다
              validate: (text, values) =>
                values.reason !== 'OTHER' || text.trim() !== '' || '사유를 입력해 주세요',
              maxLength: {
                value: RENTAL_TEXT_MAX.CANCEL_REASON,
                message: `${RENTAL_TEXT_MAX.CANCEL_REASON}자 이하로 입력해 주세요`,
              },
            })}
          />
        )}
        {fee > 0 && (
          <p className="text-caption text-accent-strong">
            대여료의 {CANCEL_FEE.RATE_PERCENT}%({formatPrice(fee)})가 수수료로 부과돼요
          </p>
        )}
        <FormError message={errors.root?.server?.message} />
      </form>
    </Modal>
  );
}
