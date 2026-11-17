import { useId } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useNavigate } from 'react-router';

import BottomActionBar from '../../../app/layout/BottomActionBar';
import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { countCodePoints } from '../../../shared/lib/validators';
import { Button, Chip, TextField, toast } from '../../../shared/ui';
import { FormError } from '../../rental/components/FormParts';
import { COMPLIMENT, REVIEW_CONTENT_MAX, SATISFACTION } from '../constants';
import { getErrorMessage, isReviewBlockedError } from '../errorMessages';
import { useWriteReview } from '../queries';
import { toReviewRequest, validateReviewContent } from '../reviewData';

// 서버 details[].field → 폼 필드
const FIELD_MAP = {
  satisfaction: 'satisfaction',
  compliments: 'compliments',
  content: 'content',
};
const COMPLIMENT_ORDER = Object.keys(COMPLIMENT);

/** 칩 묶음 하나(라벨 + 칩들 + 오류). 칩 사이 세로 간격은 Chip 터치 영역 규칙대로 gap-y-2.5 */
function ChipGroup({ label, hint, error, children }) {
  const labelId = useId();
  return (
    <div className="flex flex-col gap-2">
      <span id={labelId} className="text-caption font-semibold">
        {label}
        {hint && <span className="font-normal text-ink-muted"> {hint}</span>}
      </span>
      <div role="group" aria-labelledby={labelId} className="flex flex-wrap gap-x-2 gap-y-2.5">
        {children}
      </div>
      <FormError message={error} />
    </div>
  );
}

/**
 * SCR-14 후기 작성 폼. 만족도 필수(하나), 칭찬 여러 개, 내용 선택.
 * 쓸 수 없는 대여로 거절되면(이미 씀·기한 지남 등) 토스트만 띄운다 — 상세를 다시 받아 페이지가 안내 화면으로 바뀐다.
 */
export default function ReviewForm({ rental }) {
  const formId = useId();
  const navigate = useNavigate();
  const mutation = useWriteReview(rental.rentalId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({
    defaultValues: { satisfaction: null, compliments: [], content: '' },
    shouldFocusError: true,
  });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });
  const contentLength = countCodePoints(useWatch({ control, name: 'content' }));
  const isPending = mutation.isPending;

  function onSubmit(values) {
    mutation.mutate(toReviewRequest(values, COMPLIMENT_ORDER), {
      onSuccess: () => {
        toast.show({ message: '후기를 보냈어요' });
        navigate(`/rentals/${rental.rentalId}`, { replace: true });
      },
      onError: (error) => {
        if (isReviewBlockedError(error)) {
          toast.show({ message: getErrorMessage(error), tone: 'danger' });
          return;
        }
        handleError(error);
      },
    });
  }

  return (
    <>
      <form
        id={formId}
        noValidate
        onSubmit={handleSubmit(onSubmit)}
        className="flex flex-col gap-6 pt-2 pb-6"
      >
        <Controller
          name="satisfaction"
          control={control}
          rules={{ required: '만족도를 골라 주세요' }}
          render={({ field, fieldState }) => (
            <ChipGroup label="거래는 어땠나요?" error={fieldState.error?.message}>
              {Object.entries(SATISFACTION).map(([code, { label }], index) => (
                <Chip
                  key={code}
                  ref={index === 0 ? field.ref : undefined}
                  selected={field.value === code}
                  disabled={isPending}
                  onClick={() => field.onChange(code)}
                >
                  {label}
                </Chip>
              ))}
            </ChipGroup>
          )}
        />

        <Controller
          name="compliments"
          control={control}
          render={({ field, fieldState }) => (
            <ChipGroup
              label="좋았던 점"
              hint="(여러 개 고를 수 있어요)"
              error={fieldState.error?.message}
            >
              {Object.entries(COMPLIMENT).map(([code, { label }], index) => {
                const selected = field.value.includes(code);
                return (
                  <Chip
                    key={code}
                    ref={index === 0 ? field.ref : undefined}
                    selected={selected}
                    disabled={isPending}
                    onClick={() =>
                      field.onChange(
                        selected ? field.value.filter((c) => c !== code) : [...field.value, code],
                      )
                    }
                  >
                    {label}
                  </Chip>
                );
              })}
            </ChipGroup>
          )}
        />

        <TextField
          label="후기 내용 (선택)"
          multiline
          rows={5}
          placeholder="거래하면서 느낀 점을 남겨 주세요"
          hint={`${contentLength}/${REVIEW_CONTENT_MAX}`}
          disabled={isPending}
          error={errors.content?.message}
          {...register('content', { validate: validateReviewContent })}
        />

        <FormError message={errors.root?.server?.message} />
      </form>
      <BottomActionBar>
        <Button type="submit" form={formId} fullWidth isLoading={isPending}>
          후기 보내기
        </Button>
      </BottomActionBar>
    </>
  );
}
