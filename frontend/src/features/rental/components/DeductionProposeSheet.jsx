import { useEffect, useId } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';

import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { formatPrice } from '../../../shared/lib/format';
import { BottomSheet, Button, Chip, TextField, toast } from '../../../shared/ui';
import { DAMAGE_GRADE } from '../../payment/constants';
import { DEDUCTION_POLICY } from '../constants';
import { getDeductionGrades, parseDeductionAmount, validateDeductionImages } from '../deposit';
import { getErrorMessage, readMaxDeductionAmount } from '../errorMessages';
import { useDeductionPolicy, useDepositAction } from '../queries';
import { AiCompareSummary } from './AiCompareCard';
import { FormError, InspectionImageField } from './FormParts';

// 서버 details[].field → 폼 필드
const FIELD_MAP = { grade: 'grade', amount: 'amount', reason: 'reason' };
// 필드에 바로 붙일 409·400 코드. 상한 초과(DEDUCTION_EXCEEDS_CAP)는 details maxAmount로 따로 붙인다
const CODE_FIELDS = {
  DEDUCTION_GRADE_NOT_ALLOWED: 'grade',
  DEDUCTION_EXCEEDS_DEPOSIT: 'amount',
  DEDUCTION_EVIDENCE_REQUIRED: 'evidenceImages',
};
// 상한이 곧 제안 금액인 방식(정액·전액)은 상한을 미리 채운다. 실비(ACTUAL)는 직접 넣는다
const PREFILL_TYPES = ['FIXED', 'FULL'];

/** 고른 등급의 상한(GET deposit/policy). 못 불러오면 상한 검사는 서버에 맡긴다 */
function PolicySummary({ query }) {
  if (query.isPending) {
    return <p className="text-caption text-ink-muted">차감 상한을 불러오고 있어요</p>;
  }
  if (query.isError) {
    return (
      <div className="flex items-center justify-between gap-2 text-caption">
        <span className="text-danger">{getErrorMessage(query.error)}</span>
        <Button size="sm" variant="secondary" onClick={() => query.refetch()}>
          다시 시도
        </Button>
      </div>
    );
  }
  const policy = query.data;
  return (
    <dl className="flex flex-col gap-0.5 rounded-lg bg-surface px-3 py-2 text-caption">
      <div className="flex justify-between gap-2">
        <dt className="text-ink-muted">보증금</dt>
        <dd className="tabular-nums">{formatPrice(policy.deposit)}</dd>
      </div>
      {policy.overdueFee > 0 && (
        <div className="flex justify-between gap-2">
          <dt className="text-ink-muted">연체료(먼저 빠짐)</dt>
          <dd className="text-danger tabular-nums">−{formatPrice(policy.overdueFee)}</dd>
        </div>
      )}
      <div className="flex justify-between gap-2">
        <dt className="text-ink-muted">남은 보증금</dt>
        <dd className="tabular-nums">{formatPrice(policy.remainingDeposit)}</dd>
      </div>
      <div className="flex justify-between gap-2 font-semibold">
        <dt>제안할 수 있는 최대 금액</dt>
        <dd className="tabular-nums">{formatPrice(policy.maxAmount)}</dd>
      </div>
    </dl>
  );
}

/**
 * 보증금 차감 제안(제공자, REQ-049). 등급(B·C·D, 미반납 신고 건은 D만) → 상한 확인 → 금액·사유·사진.
 * 사진 규칙(open-issues #38·#40): 장수·용량은 inspection.images, 손상 사진 1장 이상(미반납 신고 건은 0장 가능),
 * 정책이 실비(receiptRequired)면 견적서·영수증 1장 이상.
 */
export default function DeductionProposeSheet({ rental, isLostReport, onClose }) {
  const formId = useId();
  const gradeLabelId = useId();
  const grades = getDeductionGrades(isLostReport);
  const mutation = useDepositAction(rental.rentalId);
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    setValue,
    formState: { errors, dirtyFields },
  } = useForm({
    defaultValues: {
      grade: grades.length === 1 ? grades[0] : null,
      amount: '',
      reason: '',
      evidenceImages: [],
      receiptImages: [],
    },
    shouldFocusError: true,
  });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    codeFields: CODE_FIELDS,
    getMessage: getErrorMessage,
  });
  const grade = useWatch({ control, name: 'grade' });
  const policyQuery = useDeductionPolicy(rental.rentalId, grade);
  const policy = policyQuery.data;
  const maxAmount = policy?.maxAmount;
  const receiptRequired = policy?.receiptRequired === true;

  // 정액·전액은 상한을 미리 채운다(직접 고친 금액은 두고, 등급을 바꾸면 그 등급 상한으로)
  const isAmountEdited = dirtyFields.amount === true;
  useEffect(() => {
    if (policy && PREFILL_TYPES.includes(policy.deductionType) && !isAmountEdited) {
      setValue('amount', String(policy.maxAmount));
    }
  }, [policy, isAmountEdited, setValue]);

  function validateImages(field) {
    return (_, values) => {
      const imageErrors = validateDeductionImages({
        evidenceCount: values.evidenceImages.length,
        receiptCount: values.receiptImages.length,
        isLostReport,
        receiptRequired,
      });
      return imageErrors?.[field] ?? true;
    };
  }

  function onSubmit(values) {
    const amount = parseDeductionAmount(values.amount, maxAmount).value;
    mutation.mutate(
      {
        request: {
          action: 'PROPOSE',
          grade: values.grade,
          amount,
          reason: values.reason.trim(),
        },
        evidenceImages: values.evidenceImages.map((image) => image.file),
        receiptImages: values.receiptImages.map((image) => image.file),
      },
      {
        onSuccess: () => {
          onClose();
          toast.show({ message: '차감을 제안했어요. 대여자의 응답을 기다려요' });
        },
        onError: (error) => {
          const max = readMaxDeductionAmount(error);
          if (max != null) {
            setError('amount', {
              type: 'server',
              message: `${formatPrice(max)}까지 제안할 수 있어요`,
            });
            setFocus('amount');
            return;
          }
          handleError(error);
        },
      },
    );
  }

  return (
    <BottomSheet
      open
      onClose={onClose}
      title={isLostReport ? '보증금 차감 제안(미반납)' : '보증금 차감 제안'}
      dismissible={!mutation.isPending}
      footer={
        <Button type="submit" form={formId} fullWidth isLoading={mutation.isPending}>
          차감 제안하기
        </Button>
      }
    >
      <form
        id={formId}
        noValidate
        onSubmit={handleSubmit(onSubmit)}
        className="flex flex-col gap-4"
      >
        {!isLostReport && <AiCompareSummary rentalId={rental.rentalId} />}
        <p className="text-caption text-ink-muted">
          대여자가 {DEDUCTION_POLICY.RESPONSE_HOURS}시간 안에 응답하지 않으면 자동으로 수락돼요.
          {isLostReport && ' 미반납 신고 건은 D 등급으로만 제안할 수 있어요.'}
        </p>
        <Controller
          name="grade"
          control={control}
          rules={{ required: '손상 등급을 골라 주세요' }}
          render={({ field, fieldState }) => (
            <div className="flex flex-col gap-2">
              <span id={gradeLabelId} className="text-caption font-semibold">
                손상 등급
              </span>
              <div
                role="group"
                aria-labelledby={gradeLabelId}
                className="flex flex-wrap gap-x-2 gap-y-2.5"
              >
                {grades.map((code, index) => (
                  <Chip
                    key={code}
                    ref={index === 0 ? field.ref : undefined}
                    selected={field.value === code}
                    disabled={mutation.isPending}
                    onClick={() => field.onChange(code)}
                  >
                    {DAMAGE_GRADE[code].label}
                  </Chip>
                ))}
              </div>
              {field.value && (
                <p className="text-xs text-ink-muted">{DAMAGE_GRADE[field.value].description}</p>
              )}
              <FormError message={fieldState.error?.message} />
            </div>
          )}
        />
        {grade && <PolicySummary query={policyQuery} />}
        <TextField
          label="차감 금액(원)"
          required
          inputMode="numeric"
          placeholder="0"
          hint={maxAmount != null ? `0 ~ ${formatPrice(maxAmount)}` : undefined}
          disabled={mutation.isPending}
          error={errors.amount?.message}
          {...register('amount', {
            validate: (text) => parseDeductionAmount(text, maxAmount).error ?? true,
          })}
        />
        <TextField
          label="차감 사유"
          required
          multiline
          rows={4}
          maxLength={DEDUCTION_POLICY.REASON_MAX}
          placeholder={
            isLostReport
              ? '돌려받지 못한 상황을 적어 주세요'
              : '어디가 어떻게 손상됐는지 적어 주세요'
          }
          disabled={mutation.isPending}
          error={errors.reason?.message}
          {...register('reason', {
            validate: (text) => text.trim() !== '' || '차감 사유를 입력해 주세요',
            maxLength: {
              value: DEDUCTION_POLICY.REASON_MAX,
              message: `${DEDUCTION_POLICY.REASON_MAX}자 이하로 입력해 주세요`,
            },
          })}
        />
        <InspectionImageField
          control={control}
          name="evidenceImages"
          label="손상 사진"
          hint={isLostReport ? '물품이 없으면 올리지 않아도 돼요' : undefined}
          disabled={mutation.isPending}
          validate={validateImages('evidenceImages')}
        />
        <InspectionImageField
          control={control}
          name="receiptImages"
          label="견적서·영수증"
          hint={receiptRequired ? '이 등급은 견적서·영수증이 꼭 필요해요' : '있으면 올려 주세요'}
          disabled={mutation.isPending}
          validate={validateImages('receiptImages')}
        />
        <FormError message={errors.root?.server?.message} />
      </form>
    </BottomSheet>
  );
}
