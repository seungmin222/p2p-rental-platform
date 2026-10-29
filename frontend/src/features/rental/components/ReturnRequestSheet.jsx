import { useId } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';

import { BottomSheet, Button, Chip, TextField, toast } from '../../../shared/ui';
import { RENTAL_TEXT_MAX, RETURN_CHECKLIST_ITEM } from '../constants';
import { applyFieldErrors, getErrorMessage } from '../errorMessages';
import { useRequestReturn } from '../queries';
import { toChecklist } from '../rentalData';
import { FormError, InspectionImageField } from './formParts';

const CHECKLIST_KEYS = Object.keys(RETURN_CHECKLIST_ITEM);
const FIRST_CHECKLIST_FIELD = `checklist.${CHECKLIST_KEYS[0]}`;

function hasNo(values) {
  return CHECKLIST_KEYS.some((key) => values.checklist[key] === false);
}

/** 항목 하나의 예/아니오. 기본 선택 없음. */
function ChecklistRow({ control, itemKey, question, disabled }) {
  const labelId = useId();
  return (
    <Controller
      name={`checklist.${itemKey}`}
      control={control}
      rules={{ validate: (answer) => answer != null || '예 또는 아니오를 골라 주세요' }}
      render={({ field, fieldState }) => (
        <li className="flex flex-col gap-1">
          <div className="flex items-center justify-between gap-3">
            <span id={labelId} className="text-caption">
              {question}
            </span>
            <div role="group" aria-labelledby={labelId} className="flex shrink-0 gap-1.5">
              <Chip
                ref={field.ref}
                selected={field.value === true}
                disabled={disabled}
                onClick={() => field.onChange(true)}
              >
                예
              </Chip>
              <Chip
                selected={field.value === false}
                disabled={disabled}
                onClick={() => field.onChange(false)}
              >
                아니오
              </Chip>
            </div>
          </div>
          <FormError message={fieldState.error?.message} />
        </li>
      )}
    />
  );
}

/**
 * 반납 요청(대여자) / 반납 대리 접수(제공자, REQ-018). 같은 API·같은 폼이고 서버가 writerRole로 구분한다.
 * 사진 1장 이상 + 체크리스트 5개를 모두 고르고, '아니오'가 하나라도 있으면 메모 필수.
 */
export default function ReturnRequestSheet({ rental, onClose }) {
  const formId = useId();
  const isOwner = rental.myRole === 'OWNER';
  const mutation = useRequestReturn(rental.rentalId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm({
    defaultValues: {
      images: [],
      checklist: Object.fromEntries(CHECKLIST_KEYS.map((key) => [key, null])),
      note: '',
    },
    shouldFocusError: true,
  });
  const checklist = useWatch({ control, name: 'checklist' });
  const noteRequired = hasNo({ checklist });

  function onSubmit(values) {
    const note = values.note.trim();
    const request = { checklist: toChecklist(values.checklist) };
    if (note) {
      request.note = note;
    }
    mutation.mutate(
      { request, images: values.images.map((image) => image.file) },
      {
        onSuccess: () => {
          onClose();
          toast.show({ message: isOwner ? '반납을 대신 접수했어요' : '반납을 요청했어요' });
        },
        onError: (error) => {
          const mapped = applyFieldErrors(error, setError, {
            checklist: FIRST_CHECKLIST_FIELD,
            note: 'note',
          });
          if (!mapped) {
            setError('root.server', { message: getErrorMessage(error) });
          }
        },
      },
    );
  }

  const title = isOwner ? '반납 대리 접수' : '반납 요청';
  return (
    <BottomSheet
      open
      onClose={onClose}
      title={title}
      dismissible={!mutation.isPending}
      footer={
        <Button type="submit" form={formId} fullWidth isLoading={mutation.isPending}>
          {title}
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
          {isOwner
            ? '대여자가 요청하지 않고 물품을 돌려준 경우 대신 접수해요. 기록에는 제공자가 작성한 것으로 남아요.'
            : '돌려주는 물품의 상태를 사진으로 남겨 주세요. 접수 후에는 수정할 수 없어요.'}
        </p>
        <InspectionImageField control={control} label="반납 사진" disabled={mutation.isPending} />
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 text-caption font-semibold">체크리스트</legend>
          <ul className="flex flex-col gap-3">
            {Object.entries(RETURN_CHECKLIST_ITEM).map(([key, { question }]) => (
              <ChecklistRow
                key={key}
                control={control}
                itemKey={key}
                question={question}
                disabled={mutation.isPending}
              />
            ))}
          </ul>
        </fieldset>
        <TextField
          label="메모"
          required={noteRequired}
          hint={noteRequired ? "'아니오' 항목이 있으면 메모를 적어 주세요" : undefined}
          multiline
          rows={3}
          maxLength={RENTAL_TEXT_MAX.RETURN_NOTE}
          placeholder="물품 상태에 대해 남길 말이 있으면 적어 주세요"
          disabled={mutation.isPending}
          error={errors.note?.message}
          {...register('note', {
            validate: (note, values) =>
              !hasNo(values) || note.trim() !== '' || "'아니오' 항목의 내용을 적어 주세요",
            maxLength: {
              value: RENTAL_TEXT_MAX.RETURN_NOTE,
              message: `${RENTAL_TEXT_MAX.RETURN_NOTE}자 이하로 입력해 주세요`,
            },
          })}
        />
        <FormError message={errors.root?.server?.message} />
      </form>
    </BottomSheet>
  );
}
