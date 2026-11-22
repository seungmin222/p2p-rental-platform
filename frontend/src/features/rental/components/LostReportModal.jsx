import { useId } from 'react';
import { useForm } from 'react-hook-form';

import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { Button, Modal, TextField, toast } from '../../../shared/ui';
import { LOST_REPORT_AFTER_DAYS, PAYMENT_ENABLED, RENTAL_TEXT_MAX } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useReportLost } from '../queries';
import { FormError } from './FormParts';

const FIELD_MAP = { note: 'note' };

/**
 * 미반납 신고(제공자, REQ-018). 메모는 선택(return.note-max). 반납 사진 없이 반납 완료가 되고,
 * 동결된 보증금이 있으면 보류(이어서 D 등급 차감 제안), 없으면 바로 거래 완료다(open-issues #39).
 * 신고 뒤 물건이 돌아와도 상태 확인은 하지 않는다(open-issues #41).
 */
export default function LostReportModal({ rental, onClose }) {
  const formId = useId();
  const mutation = useReportLost(rental.rentalId);
  const {
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({ defaultValues: { note: '' }, shouldFocusError: true });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });

  function onSubmit(values) {
    const note = values.note.trim();
    mutation.mutate(note ? { note } : {}, {
      onSuccess: (result) => {
        onClose();
        toast.show({
          message:
            result.depositStatus === 'HELD'
              ? '미반납 신고를 했어요. 보증금 차감을 제안해 주세요'
              : '미반납 신고를 했어요. 거래가 완료됐어요',
        });
      },
      onError: handleError,
    });
  }

  return (
    <Modal
      open
      onClose={onClose}
      title="미반납 신고를 할까요?"
      dismissible={!mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            닫기
          </Button>
          <Button type="submit" form={formId} variant="danger" isLoading={mutation.isPending}>
            미반납 신고
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
        <div className="flex flex-col gap-1 text-caption">
          <p>
            반납 예정일로부터 {LOST_REPORT_AFTER_DAYS}일이 지나도 돌려받지 못한 물품을 신고해요.
            신고하면 반납 완료로 바뀌고 연체료는 오늘 기준으로 확정돼요.
          </p>
          {PAYMENT_ENABLED && (
            <p className="text-ink-muted">
              보증금은 보류되고, 이어서 보증금 차감(D 등급)을 제안할 수 있어요.
            </p>
          )}
          <p className="text-ink-muted">신고한 뒤에는 물품을 돌려받아도 상태 확인을 하지 않아요.</p>
        </div>
        <TextField
          label="메모"
          multiline
          rows={3}
          maxLength={RENTAL_TEXT_MAX.RETURN_NOTE}
          placeholder="연락 상황 등을 적어 주세요(선택)"
          disabled={mutation.isPending}
          error={errors.note?.message}
          {...register('note', {
            maxLength: {
              value: RENTAL_TEXT_MAX.RETURN_NOTE,
              message: `${RENTAL_TEXT_MAX.RETURN_NOTE}자 이하로 입력해 주세요`,
            },
          })}
        />
        <FormError message={errors.root?.server?.message} />
      </form>
    </Modal>
  );
}
