import { useId } from 'react';
import { useForm } from 'react-hook-form';

import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { BottomSheet, Button, TextField, toast } from '../../../shared/ui';
import { RENTAL_TEXT_MAX } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useHandover } from '../queries';
import { FormError, InspectionImageField } from './FormParts';

const FIELD_MAP = { memo: 'memo' };

/** 인계 확인(제공자, REQ-017). 사진 1장 이상 필수, 메모 선택. 인계 후 사진은 고칠 수 없다. */
export default function HandoverSheet({ rental, onClose }) {
  const formId = useId();
  const mutation = useHandover(rental.rentalId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({ defaultValues: { images: [], memo: '' }, shouldFocusError: true });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });

  function onSubmit(values) {
    const memo = values.memo.trim();
    mutation.mutate(
      { request: memo ? { memo } : {}, images: values.images.map((image) => image.file) },
      {
        onSuccess: () => {
          onClose();
          toast.show({ message: '인계를 확인했어요' });
        },
        onError: handleError,
      },
    );
  }

  return (
    <BottomSheet
      open
      onClose={onClose}
      title="인계 확인"
      dismissible={!mutation.isPending}
      footer={
        <Button type="submit" form={formId} fullWidth isLoading={mutation.isPending}>
          인계 확인
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
          건네기 전 물품 상태를 사진으로 남겨 주세요. 인계 후에는 사진을 수정할 수 없어요.
        </p>
        <InspectionImageField control={control} label="인계 사진" disabled={mutation.isPending} />
        <TextField
          label="메모"
          multiline
          rows={3}
          maxLength={RENTAL_TEXT_MAX.HANDOVER_MEMO}
          placeholder="구성품, 기존 흠집 등을 적어 두면 좋아요"
          disabled={mutation.isPending}
          error={errors.memo?.message}
          {...register('memo', {
            maxLength: {
              value: RENTAL_TEXT_MAX.HANDOVER_MEMO,
              message: `${RENTAL_TEXT_MAX.HANDOVER_MEMO}자 이하로 입력해 주세요`,
            },
          })}
        />
        <FormError message={errors.root?.server?.message} />
      </form>
    </BottomSheet>
  );
}
