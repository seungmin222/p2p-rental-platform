import { useId } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';

import BottomActionBar from '../../../app/layout/BottomActionBar';
import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { countCodePoints } from '../../../shared/lib/validators';
import { Button, Chip, ErrorState, ImageUploader, Skeleton, TextField } from '../../../shared/ui';
import {
  DEPOSIT_GUIDE_DEBOUNCE_MS,
  ITEM_CONDITION,
  ITEM_IMAGES,
  ITEM_TEXT_MAX,
} from '../constants';
import { getErrorMessage, getErrorReasons } from '../errorMessages';
import {
  parseAmount,
  validateDeposit,
  validateDescription,
  validateImages,
  validateItemPrice,
  validateName,
  validatePricePerDay,
} from '../itemForm';
import { useCategories, useDepositGuide } from '../queries';
import AvailablePeriodsField from './AvailablePeriodsField';
import TradePlaceField from './TradePlaceField';

// 서버 details의 field → 폼 필드(좌표 오류는 위치 칸 하나에)
const FIELD_MAP = {
  name: 'name',
  description: 'description',
  categoryId: 'categoryId',
  condition: 'condition',
  pricePerDay: 'pricePerDay',
  itemPrice: 'itemPrice',
  deposit: 'deposit',
  address: 'address',
  latitude: 'latitude',
  longitude: 'latitude',
  availablePeriods: 'availablePeriods',
  images: 'images',
  imageOrder: 'images',
};
const CODE_FIELDS = {
  IMAGE_COUNT_INVALID: 'images',
  IMAGE_INVALID_TYPE: 'images',
  IMAGE_SIZE_EXCEEDED: 'images',
  ITEM_PERIOD_CONFLICT: 'availablePeriods',
};

function FieldError({ message }) {
  return message ? (
    <p role="alert" className="text-xs text-danger">
      {message}
    </p>
  ) : null;
}

/** 칩 하나 고르기(카테고리·상태). 오류 시 이 영역으로 포커스가 오도록 ref를 단다 */
function ChipGroupField({ control, name, label, options, validateMessage, disabled }) {
  return (
    <Controller
      name={name}
      control={control}
      rules={{ validate: (value) => value != null || validateMessage }}
      render={({ field, fieldState }) => (
        <fieldset
          ref={field.ref}
          tabIndex={-1}
          disabled={disabled}
          className="flex flex-col gap-1.5 outline-none"
        >
          <legend className="pb-1.5 text-caption font-semibold">
            {label}
            <span aria-hidden="true" className="text-danger">
              {' '}
              *
            </span>
          </legend>
          <div className="flex flex-wrap gap-x-2 gap-y-2.5">
            {options.map((option) => (
              <Chip
                key={option.value}
                selected={field.value === option.value}
                onClick={() => field.onChange(option.value)}
              >
                {option.label}
              </Chip>
            ))}
          </div>
          <FieldError message={fieldState.error?.message} />
        </fieldset>
      )}
    />
  );
}

const CONDITION_OPTIONS = Object.entries(ITEM_CONDITION).map(([value, { label }]) => ({
  value,
  label,
}));

function CategoryField({ control, disabled }) {
  const query = useCategories();
  if (query.isPending) {
    return <Skeleton className="h-20 w-full" />;
  }
  if (query.isError) {
    return (
      <ErrorState
        title="카테고리를 불러오지 못했어요"
        message={getErrorMessage(query.error)}
        onRetry={query.refetch}
      />
    );
  }
  return (
    <ChipGroupField
      control={control}
      name="categoryId"
      label="카테고리"
      validateMessage="카테고리를 선택해 주세요"
      disabled={disabled}
      options={query.data.map((category) => ({
        value: category.categoryId,
        label: category.name,
      }))}
    />
  );
}

/** 권장 보증금 안내 문구. 카테고리·유효한 물품 가격이 있을 때만, 범위 밖이어도 등록할 수 있다 */
function useDepositHint(control) {
  const categoryId = useWatch({ control, name: 'categoryId' });
  const itemPriceText = useWatch({ control, name: 'itemPrice' });
  const debouncedText = useDebouncedValue(itemPriceText, DEPOSIT_GUIDE_DEBOUNCE_MS);
  const itemPrice = validateItemPrice(debouncedText) === true ? parseAmount(debouncedText) : null;
  const { data } = useDepositGuide({ categoryId, itemPrice });
  if (!data || itemPrice == null) {
    return '0원(보증금 없음) 또는 1,000원 이상';
  }
  const range =
    data.minDeposit === data.maxDeposit
      ? `${data.minDeposit.toLocaleString()}원`
      : `${data.minDeposit.toLocaleString()}원 ~ ${data.maxDeposit.toLocaleString()}원`;
  return `권장 보증금 ${range}. 다른 금액을 입력해도 돼요`;
}

/**
 * SCR-06 등록·수정 공통 폼(REQ-006·007). 제출 변환·요청은 호출부가 onSave(values, onError)로 맡고,
 * 필드 오류 표시는 여기서 한다.
 *
 * @param {object} props
 * @param {object} props.initialValues itemForm.js createItemFormValues 또는 api.js toItemEditForm 모양
 * @param {boolean} [props.isEdit]
 * @param {string} [props.today] 등록만. 가능 기간 시작일 ≥ 오늘 검사
 * @param {boolean} props.isPending
 * @param {string} props.submitLabel
 * @param {(values: object, onError: (error: object) => void) => void} props.onSave
 * @param {(error: object) => boolean} [props.onServerError] true를 돌려주면 처리한 것(REGION_NOT_VERIFIED)
 */
export default function ItemForm({
  initialValues,
  isEdit = false,
  today,
  isPending,
  submitLabel,
  onSave,
  onServerError,
}) {
  const formId = useId();
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    setValue,
    formState: { errors },
  } = useForm({ defaultValues: initialValues, shouldFocusError: true });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, isPending, {
    fieldMap: FIELD_MAP,
    codeFields: CODE_FIELDS,
    getMessage: getErrorMessage,
  });
  const nameLength = countCodePoints(useWatch({ control, name: 'name' }));
  const descriptionLength = countCodePoints(useWatch({ control, name: 'description' }));
  const depositHint = useDepositHint(control);

  function onError(error) {
    if (onServerError?.(error)) {
      return;
    }
    handleError(error);
    // 충돌 기간은 details에 한 건씩 온다. 포커스는 위 처리에 맡기고 문구만 기간까지 넣는다
    if (error.code === 'ITEM_PERIOD_CONFLICT') {
      const reasons = getErrorReasons(error);
      if (reasons.length > 0) {
        setError('availablePeriods', {
          type: 'server',
          message: `${getErrorMessage(error)}: ${reasons.join(', ')}`,
        });
      }
    }
  }

  return (
    <>
      <form
        id={formId}
        noValidate
        onSubmit={handleSubmit((values) => onSave(values, onError))}
        className="flex flex-col gap-6 pt-2 pb-8"
      >
        <Controller
          name="images"
          control={control}
          rules={{
            validate: (images) =>
              validateImages(images, isEdit ? { initialImages: initialValues.images } : undefined),
          }}
          render={({ field, fieldState }) => (
            <div ref={field.ref} tabIndex={-1} className="flex flex-col gap-1 outline-none">
              <fieldset disabled={isPending} className="contents">
                <ImageUploader
                  value={field.value}
                  onChange={field.onChange}
                  maxCount={ITEM_IMAGES.MAX_COUNT}
                  maxFileSize={ITEM_IMAGES.MAX_FILE_SIZE}
                  accept={ITEM_IMAGES.ACCEPT}
                  label="사진"
                  hint="첫 장이 대표 사진이에요. jpg·png, 장당 10MB"
                />
              </fieldset>
              <FieldError message={fieldState.error?.message} />
            </div>
          )}
        />
        <TextField
          label="물품 이름"
          required
          placeholder="예: 4인용 거실형 텐트"
          hint={`${nameLength}/${ITEM_TEXT_MAX.NAME}`}
          disabled={isPending}
          error={errors.name?.message}
          {...register('name', { validate: validateName })}
        />
        <CategoryField control={control} disabled={isPending} />
        <ChipGroupField
          control={control}
          name="condition"
          label="상태"
          options={CONDITION_OPTIONS}
          validateMessage="상태를 선택해 주세요"
          disabled={isPending}
        />
        <TextField
          label="설명"
          required
          multiline
          rows={5}
          placeholder="구성품, 사용 방법, 주의할 점을 적어 주세요"
          hint={`${descriptionLength}/${ITEM_TEXT_MAX.DESCRIPTION}`}
          disabled={isPending}
          error={errors.description?.message}
          {...register('description', { validate: validateDescription })}
        />
        <TextField
          label="일 대여료(원)"
          required
          inputMode="numeric"
          placeholder="0"
          hint="0원(무료) 또는 1,000원 이상"
          disabled={isPending}
          error={errors.pricePerDay?.message}
          {...register('pricePerDay', { validate: validatePricePerDay })}
        />
        <TextField
          label="물품 가격(원)"
          required
          inputMode="numeric"
          placeholder="0"
          hint="대여자에게 보이지 않아요. 권장 보증금 계산에 써요"
          disabled={isPending}
          error={errors.itemPrice?.message}
          {...register('itemPrice', { validate: validateItemPrice })}
        />
        <TextField
          label="보증금(원)"
          required
          inputMode="numeric"
          placeholder="0"
          hint={depositHint}
          disabled={isPending}
          error={errors.deposit?.message}
          {...register('deposit', { validate: validateDeposit })}
        />
        <AvailablePeriodsField control={control} today={today} disabled={isPending} />
        <TradePlaceField
          control={control}
          register={register}
          setValue={setValue}
          error={errors.address?.message}
          isEdit={isEdit}
          disabled={isPending}
        />
        <FieldError message={errors.root?.server?.message} />
      </form>
      <BottomActionBar>
        <Button type="submit" form={formId} fullWidth isLoading={isPending}>
          {submitLabel}
        </Button>
      </BottomActionBar>
    </>
  );
}
