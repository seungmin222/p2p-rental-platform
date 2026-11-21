import { useQueryClient } from '@tanstack/react-query';
import { useId, useState } from 'react';

import { formatMonthDay, weekdayOf } from '../../../shared/lib/date';
import { formatPrice } from '../../../shared/lib/format';
import { BottomSheet, Button, Chip, Skeleton, toast } from '../../../shared/ui';
import { startPayment } from '../../payment/startPayment';
import { EXTENSION_POLICY, PAYMENT_ENABLED } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useCalendarDays, useRequestExtension } from '../queries';
import {
  estimateExtensionFee,
  getExtensionMonths,
  getExtensionOptions,
  readMaxEndDate,
} from '../rentalExtension';
import { FormError } from './FormParts';

function dayLabel(date) {
  return `${formatMonthDay(date)}(${weekdayOf(date)})`;
}

/** 서버가 알려 준 최대 날짜 안내. null이면 하루도 안 됨 */
function maxEndDateMessage(maxEndDate) {
  return maxEndDate
    ? `${dayLabel(maxEndDate)}까지 연장할 수 있어요`
    : '지금은 연장할 수 있는 날이 없어요';
}

/**
 * 대여 연장(REQ-038). 새 반납일을 종료일 다음 날부터 extension.max-days-per일 안에서 칩으로 고른다.
 * 고를 수 있는 날은 물품 캘린더로 미리 거르고(이어진 AVAILABLE까지), 서버 거절의 maxEndDate로 다시 좁힌다.
 * 결제 대기가 되면 시트를 닫고 바로 연장료 결제창을 연다. 바로 확정(0원·결제 꺼짐)이면 반납일이 바뀌었다고 알린다.
 */
export default function ExtensionSheet({ rental, onClose }) {
  const groupLabelId = useId();
  const queryClient = useQueryClient();
  const mutation = useRequestExtension(rental);
  const [selectedDate, setSelectedDate] = useState(null);
  // undefined = 서버가 알려 준 적 없음, null = 하루도 안 됨
  const [maxEndDate, setMaxEndDate] = useState(undefined);
  const [errorMessage, setErrorMessage] = useState(null);

  const months = getExtensionMonths(rental.endDate, EXTENSION_POLICY.MAX_DAYS);
  const calendar = useCalendarDays(rental.item.itemId, months);
  const isCalendarLoading = calendar.results.some((result) => result.isPending);
  // 캘린더를 못 불러오면 서버 검사에 맡긴다(모든 날을 고를 수 있게)
  const isCalendarFailed = calendar.results.some((result) => result.isError);
  const options = getExtensionOptions(
    rental.endDate,
    isCalendarFailed ? null : calendar.days,
    EXTENSION_POLICY.MAX_DAYS,
    maxEndDate,
  );
  const hasSelectable = options.some((option) => option.isSelectable);
  const selected = options.find((option) => option.date === selectedDate && option.isSelectable);

  function handleSubmit() {
    if (!selected) {
      return;
    }
    setErrorMessage(null);
    mutation.mutate(
      { newEndDate: selected.date },
      {
        onSuccess: (result) => {
          onClose();
          if (result.status === 'PENDING_PAYMENT') {
            startPayment(queryClient, rental.rentalId, { extensionId: result.extensionId });
            return;
          }
          toast.show({ message: `반납일이 ${formatMonthDay(result.newEndDate)}로 바뀌었어요` });
        },
        onError: (error) => {
          const serverMaxEndDate = readMaxEndDate(error);
          if (serverMaxEndDate !== undefined) {
            setMaxEndDate(serverMaxEndDate);
            setSelectedDate(null);
            setErrorMessage(`${getErrorMessage(error)}. ${maxEndDateMessage(serverMaxEndDate)}`);
            return;
          }
          setErrorMessage(getErrorMessage(error));
        },
      },
    );
  }

  return (
    <BottomSheet
      open
      onClose={onClose}
      title="대여 연장"
      dismissible={!mutation.isPending}
      footer={
        <Button
          fullWidth
          onClick={handleSubmit}
          disabled={!selected}
          isLoading={mutation.isPending}
        >
          {PAYMENT_ENABLED &&
          selected &&
          estimateExtensionFee(rental.pricePerDay, selected.days) > 0
            ? '연장료 결제하기'
            : '연장하기'}
        </Button>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="text-caption text-ink-muted">
          지금 반납일은 <b className="text-ink">{dayLabel(rental.endDate)}</b>이에요. 한 번에 최대{' '}
          {EXTENSION_POLICY.MAX_DAYS}일, 대여당 {EXTENSION_POLICY.MAX_COUNT}번까지 늘릴 수 있어요
          {rental.extensionCount > 0 ? `(지금까지 ${rental.extensionCount}번)` : ''}.
        </p>

        <div className="flex flex-col gap-2">
          <span id={groupLabelId} className="text-caption font-semibold">
            새 반납일
          </span>
          {isCalendarLoading ? (
            <div aria-busy="true" className="flex flex-wrap gap-x-2 gap-y-2.5">
              {Array.from({ length: EXTENSION_POLICY.MAX_DAYS }, (_, index) => (
                <Skeleton key={index} className="h-8.5 w-24 rounded-full" />
              ))}
            </div>
          ) : (
            <div
              role="group"
              aria-labelledby={groupLabelId}
              className="flex flex-wrap gap-x-2 gap-y-2.5"
            >
              {options.map((option) => (
                <Chip
                  key={option.date}
                  selected={option.date === selected?.date}
                  disabled={!option.isSelectable || mutation.isPending}
                  className="disabled:cursor-not-allowed disabled:text-ink-muted disabled:line-through"
                  onClick={() => {
                    setSelectedDate(option.date);
                    setErrorMessage(null);
                  }}
                >
                  {dayLabel(option.date)} · {option.days}일
                </Chip>
              ))}
            </div>
          )}
          {!isCalendarLoading && !hasSelectable && !errorMessage && (
            <p className="text-caption text-ink-muted">
              {maxEndDate === undefined
                ? '반납일 다음 날에 다른 예약이 있거나 대여 가능 기간이 끝나 지금은 연장할 수 없어요'
                : maxEndDateMessage(maxEndDate)}
            </p>
          )}
        </div>

        {selected && (
          <section
            aria-label="연장 내용"
            className="flex flex-col gap-1 rounded-xl bg-surface p-3.5 text-caption tabular-nums"
          >
            <div className="flex justify-between gap-3">
              <span className="text-ink-muted">새 반납일</span>
              <span>
                {dayLabel(selected.date)} · {selected.days}일 연장
              </span>
            </div>
            {PAYMENT_ENABLED && (
              <div className="flex justify-between gap-3">
                <span className="text-ink-muted">
                  연장료 {formatPrice(rental.pricePerDay)} × {selected.days}일
                </span>
                <b className="font-semibold">
                  {formatPrice(estimateExtensionFee(rental.pricePerDay, selected.days))}
                </b>
              </div>
            )}
            {PAYMENT_ENABLED && (
              <p className="mt-1 text-ink-muted">
                보증금은 그대로예요. 결제 시간 안에 결제하지 않으면 연장이 취소돼요.
              </p>
            )}
          </section>
        )}

        <FormError message={errorMessage} />
      </div>
    </BottomSheet>
  );
}
