import { useId, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { Link, useNavigate } from 'react-router';

import BottomActionBar from '../../../app/layout/BottomActionBar';
import { useSubmitErrorHandler } from '../../../shared/hooks/useSubmitErrorHandler';
import { countCodePoints } from '../../../shared/lib/validators';
import { Button, Chip, TextField, toast } from '../../../shared/ui';
import { FormError } from '../../rental/components/FormParts';
import { PAYMENT_ENABLED } from '../../rental/constants';
import { INQUIRY_CONTENT_MAX, INQUIRY_TYPE } from '../constants';
import { getErrorMessage, isDisputeAlreadyOpenError } from '../errorMessages';
import {
  DISPUTE_BLOCK_TEXT,
  getDisputeBlockReason,
  toInquiryRequest,
  validateInquiryContent,
} from '../inquiryForm';
import { useCreateInquiry } from '../queries';

// 서버 details[].field → 폼 필드(rentalId는 사용자가 고치는 칸이 아니라 root 문구로)
const FIELD_MAP = { content: 'content', type: 'type' };

/**
 * REQ-063 문의·분쟁 접수 폼. rentalId가 있으면 그 대여를 연결해 보낸다(일반 문의도 연결 가능).
 * rental은 연결한 대여의 상세(없거나 불러오는 중이면 undefined) — 결제 대기면 분쟁 칩을 막는다.
 * 이미 열린 분쟁(409)은 응답에 미리 알 수 있는 필드가 없어 제출 뒤 폼 안에 안내 + 내 문의 링크로 보인다.
 */
export default function InquiryForm({ rentalId, rental, initialType }) {
  const formId = useId();
  const typeLabelId = useId();
  const navigate = useNavigate();
  const mutation = useCreateInquiry();
  const [isDisputeOpen, setDisputeOpen] = useState(false);
  const disputeBlock = getDisputeBlockReason(rentalId, rental);
  const {
    control,
    register,
    handleSubmit,
    setError,
    setFocus,
    formState: { errors },
  } = useForm({
    defaultValues: {
      type: initialType === 'DISPUTE' && !disputeBlock ? 'DISPUTE' : 'GENERAL',
      content: '',
    },
    shouldFocusError: true,
  });
  const handleError = useSubmitErrorHandler({ setError, setFocus }, mutation.isPending, {
    fieldMap: FIELD_MAP,
    getMessage: getErrorMessage,
  });
  const type = useWatch({ control, name: 'type' });
  const contentLength = countCodePoints(useWatch({ control, name: 'content' }));
  const isPending = mutation.isPending;
  // 상세를 다시 받아 결제 대기로 바뀌면 고른 분쟁을 보내지 않는다
  const isDispute = type === 'DISPUTE' && !disputeBlock;

  function onSubmit(values) {
    setDisputeOpen(false);
    const body = toInquiryRequest({ ...values, type: isDispute ? 'DISPUTE' : 'GENERAL' }, rentalId);
    mutation.mutate(body, {
      onSuccess: () => {
        toast.show({ message: isDispute ? '분쟁을 접수했어요' : '문의를 접수했어요' });
        navigate('/me/inquiries', { replace: true });
      },
      onError: (error) => {
        setDisputeOpen(isDisputeAlreadyOpenError(error));
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
          name="type"
          control={control}
          render={({ field }) => (
            <div className="flex flex-col gap-2">
              <span id={typeLabelId} className="text-caption font-semibold">
                종류
              </span>
              <div
                role="group"
                aria-labelledby={typeLabelId}
                className="flex flex-wrap gap-x-2 gap-y-2.5"
              >
                {Object.entries(INQUIRY_TYPE).map(([code, { label }], index) => (
                  <Chip
                    key={code}
                    ref={index === 0 ? field.ref : undefined}
                    selected={code === 'DISPUTE' ? isDispute : !isDispute}
                    disabled={isPending || (code === 'DISPUTE' && Boolean(disputeBlock))}
                    onClick={() => field.onChange(code)}
                  >
                    {label}
                  </Chip>
                ))}
              </div>
              {disputeBlock && (
                <p className="text-xs text-ink-muted">{DISPUTE_BLOCK_TEXT[disputeBlock]}</p>
              )}
              {isDispute && (
                <p className="rounded-xl bg-accent-soft px-4 py-3 text-caption">
                  거래 중 다툼이 생겨 플랫폼의 확인이 필요할 때 접수해요. 관리자가 확인하고 처리
                  상태를 바꿔요.
                  {PAYMENT_ENABLED &&
                    ' 접수하면 이 대여의 정산 지급이 분쟁이 끝날 때까지 보류돼요(이미 지급된 정산은 그대로예요).'}
                </p>
              )}
            </div>
          )}
        />

        <TextField
          label="내용"
          required
          multiline
          rows={8}
          placeholder={
            isDispute
              ? '어떤 문제가 있었는지 자세히 적어 주세요'
              : '궁금한 점이나 불편한 점을 적어 주세요'
          }
          hint={`${contentLength.toLocaleString('ko-KR')}/${INQUIRY_CONTENT_MAX.toLocaleString('ko-KR')}`}
          disabled={isPending}
          error={errors.content?.message}
          {...register('content', { validate: validateInquiryContent })}
        />

        <div className="flex flex-col gap-1">
          <FormError message={errors.root?.server?.message ?? errors.type?.message} />
          {isDisputeOpen && (
            <Link
              to="/me/inquiries"
              className="inline-flex min-h-11 items-center self-start text-caption font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
            >
              내 문의 보기
            </Link>
          )}
        </div>
      </form>
      <BottomActionBar>
        <Button type="submit" form={formId} fullWidth isLoading={isPending}>
          {isDispute ? '분쟁 접수하기' : '문의 보내기'}
        </Button>
      </BottomActionBar>
    </>
  );
}
