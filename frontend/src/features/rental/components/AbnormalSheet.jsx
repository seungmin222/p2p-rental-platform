import { useId } from 'react';
import { useForm } from 'react-hook-form';

import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { BottomSheet, Button, TextField, toast } from '../../../shared/ui';
import { PAYMENT_ENABLED, RENTAL_TEXT_MAX } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useConfirmReturn } from '../queries';
import { FormError, InspectionImageField } from './FormParts';

const FIELD_MAP = { resultNote: 'resultNote' };

/** 상태 확인 '이상 있음'(제공자, REQ-018). 내용 필수 + 사진 1장 이상. 손상 등급·차감은 2순위(REQ-049). */
export default function AbnormalSheet({ rental, onClose }) {
  const formId = useId();
  const mutation = useConfirmReturn(rental.rentalId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({ defaultValues: { images: [], resultNote: '' }, shouldFocusError: true });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });

  function onSubmit(values) {
    mutation.mutate(
      {
        request: { result: 'ABNORMAL', resultNote: values.resultNote.trim() },
        images: values.images.map((image) => image.file),
      },
      {
        onSuccess: () => {
          onClose();
          toast.show({ message: '이상 내용을 기록했어요' });
        },
        onError: handleError,
      },
    );
  }

  return (
    <BottomSheet
      open
      onClose={onClose}
      title="이상 있음"
      dismissible={!mutation.isPending}
      footer={
        <Button
          type="submit"
          form={formId}
          variant="danger"
          fullWidth
          isLoading={mutation.isPending}
        >
          이상 있음으로 확인
        </Button>
      }
    >
      <form
        id={formId}
        noValidate
        onSubmit={handleSubmit(onSubmit)}
        className="flex flex-col gap-4"
      >
        <p className="text-caption text-ink-muted">
          {PAYMENT_ENABLED
            ? '반납된 물품의 문제를 기록해요.'
            : '반납된 물품의 문제를 기록해요. 확인하면 기록이 남고 거래가 완료돼요.'}
        </p>
        <TextField
          label="이상 내용"
          required
          multiline
          rows={4}
          maxLength={RENTAL_TEXT_MAX.ABNORMAL_NOTE}
          placeholder="어디에 어떤 문제가 있는지 적어 주세요"
          disabled={mutation.isPending}
          error={errors.resultNote?.message}
          {...register('resultNote', {
            validate: (note) => note.trim() !== '' || '이상 내용을 입력해 주세요',
            maxLength: {
              value: RENTAL_TEXT_MAX.ABNORMAL_NOTE,
              message: `${RENTAL_TEXT_MAX.ABNORMAL_NOTE}자 이하로 입력해 주세요`,
            },
          })}
        />
        <InspectionImageField control={control} label="이상 사진" disabled={mutation.isPending} />
        <FormError message={errors.root?.server?.message} />
      </form>
    </BottomSheet>
  );
}
