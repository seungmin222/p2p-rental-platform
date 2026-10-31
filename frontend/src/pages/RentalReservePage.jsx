import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import BottomActionBar from '../app/layout/BottomActionBar';
import PageHeader from '../app/layout/PageHeader';
import { parseItemId } from '../features/item/itemId';
import { useItemSummary } from '../features/item/queries';
import { handleStartPaymentError, startPayment } from '../features/payment/startPayment';
import AmountSummary from '../features/rental/components/AmountSummary';
import {
  CANCEL_FEE,
  PAYMENT_ENABLED,
  PAYMENT_EXPIRE_MINUTES,
  RENTAL_DAY_STATUS,
  RENTAL_PERIOD,
} from '../features/rental/constants';
import { getErrorMessage } from '../features/rental/errorMessages';
import { rentalKeys, useCalendarDays, useCreateRental } from '../features/rental/queries';
import { calcTotalAmount } from '../features/rental/rentalData';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { countDaysInclusive, monthOf, todayInSeoul } from '../shared/lib/date';
import { formatPrice } from '../shared/lib/format';
import { isInitialLoadError } from '../shared/lib/queryState';
import {
  Button,
  ButtonLink,
  Calendar,
  EmptyState,
  ErrorState,
  Skeleton,
  toast,
} from '../shared/ui';

// 기간을 다시 골라야 하는 예약 실패: 캘린더를 다시 불러오고 선택을 지운다
const RESELECT_CODES = [
  'RENTAL_PERIOD_CONFLICT',
  'RENTAL_INVALID_PERIOD',
  'RENTAL_OUT_OF_AVAILABLE_PERIOD',
];

const CANCEL_POLICY = PAYMENT_ENABLED
  ? `시작일 ${CANCEL_FEE.FREE_UNTIL_DAYS_BEFORE}일 전까지 무료 취소, ${CANCEL_FEE.FREE_UNTIL_DAYS_BEFORE - 1}일 전부터 전날까지는 대여료의 ${CANCEL_FEE.RATE_PERCENT}%가 수수료로 부과돼요.`
  : // 결제가 없는 1순위에는 수수료를 받지 않으므로 취소 방법만 안내한다
    '시작일 전날까지는 바로 취소할 수 있고, 시작일부터는 상대의 동의가 있어야 취소돼요.';

function ItemSummaryRow({ item }) {
  return (
    <div className="flex items-center gap-3">
      <div className="size-13 shrink-0 overflow-hidden rounded-[10px] bg-placeholder">
        {item.thumbnailUrl && (
          <img src={item.thumbnailUrl} alt="" className="size-full object-cover" />
        )}
      </div>
      <div className="flex min-w-0 flex-col gap-0.5">
        <span className="truncate text-body font-semibold">{item.name}</span>
        <span className="truncate text-caption text-ink-muted">
          {[item.ownerNickname, item.regionName].filter(Boolean).join(' · ')}
        </span>
      </div>
    </div>
  );
}

function ItemSummarySkeleton() {
  return (
    <div className="flex items-center gap-3">
      <Skeleton className="size-13 rounded-[10px]" />
      <div className="flex flex-col gap-1.5">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-3.5 w-24" />
      </div>
    </div>
  );
}

/** SCR-07 대여 예약(시안 Booking.html). 결제 대기가 되면 대여 상세로 옮긴 뒤 결제창을 연다(뒤로 가도 예약 화면으로 오지 않게). */
export default function RentalReservePage() {
  const { id } = useParams();
  const itemId = parseItemId(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const thisMonth = monthOf(todayInSeoul());
  const [month, setMonth] = useState(thisMonth);
  // 한 번 연 달은 계속 불러 둔다(달을 넘는 기간의 범위 검사)
  const [openedMonths, setOpenedMonths] = useState([thisMonth]);
  const [value, setValue] = useState(null);
  // 예약 응답으로 알게 된 막힘: 'OWN_ITEM' | 'NOT_FOUND' | 'REGION'
  const [blocker, setBlocker] = useState(null);

  const itemQuery = useItemSummary(itemId);
  const calendar = useCalendarDays(itemId, openedMonths);
  const createMutation = useCreateRental();
  // 이미 보던 물품·캘린더는 재조회가 실패해도 그대로 둔다(고른 기간 유지)
  useRefetchErrorToast([itemQuery, ...calendar.results]);

  const monthResult = calendar.results[openedMonths.indexOf(month)];
  const item = itemQuery.data;
  const rentalDays = value?.endDate ? countDaysInclusive(value.startDate, value.endDate) : null;
  const rentalFee = item && rentalDays ? item.pricePerDay * rentalDays : null;
  const totalAmount =
    rentalFee != null ? calcTotalAmount(item.pricePerDay, rentalDays, item.deposit) : null;

  function handleMonthChange(next) {
    setMonth(next);
    setOpenedMonths((months) => (months.includes(next) ? months : [...months, next]));
  }

  function handleSubmit() {
    createMutation.mutate(
      { itemId, startDate: value.startDate, endDate: value.endDate },
      {
        onSuccess: (rental) => {
          toast.show({ message: '예약했어요' });
          navigate(`/rentals/${rental.rentalId}`, { replace: true });
          // 무료 대여는 바로 RESERVED라 결제가 없다
          if (PAYMENT_ENABLED && rental.status === 'PENDING_PAYMENT') {
            startPayment(rental.rentalId).catch((error) =>
              handleStartPaymentError(queryClient, rental.rentalId, error),
            );
          }
        },
        onError: (error) => {
          if (RESELECT_CODES.includes(error.code)) {
            toast.show({ message: getErrorMessage(error), tone: 'danger' });
            queryClient.invalidateQueries({ queryKey: rentalKeys.calendar(itemId) });
            setValue(null);
          } else if (error.code === 'REGION_NOT_VERIFIED') {
            toast.show({ message: getErrorMessage(error), tone: 'danger' });
            setBlocker('REGION');
          } else if (error.code === 'RENTAL_OWN_ITEM') {
            setBlocker('OWN_ITEM');
          } else if (error.code === 'ITEM_NOT_FOUND') {
            setBlocker('NOT_FOUND');
          } else {
            toast.show({ message: getErrorMessage(error), tone: 'danger' });
          }
        },
      },
    );
  }

  const isNotFound =
    !Number.isInteger(itemId) ||
    blocker === 'NOT_FOUND' ||
    (isInitialLoadError(itemQuery) && itemQuery.error.code === 'ITEM_NOT_FOUND') ||
    (isInitialLoadError(monthResult) && monthResult.error.code === 'ITEM_NOT_FOUND');

  if (isNotFound) {
    return (
      <>
        <PageHeader title="대여 예약" />
        <ErrorState title="물품을 찾을 수 없어요" message="삭제되었거나 숨겨진 물품이에요." />
        <div className="flex justify-center">
          <ButtonLink to="/" variant="secondary">
            홈으로
          </ButtonLink>
        </div>
      </>
    );
  }

  if (item?.isOwner || blocker === 'OWN_ITEM') {
    return (
      <>
        <PageHeader title="대여 예약" />
        <EmptyState
          title="내 물품은 예약할 수 없어요"
          description="물품 정보는 물품 상세에서 수정할 수 있어요."
          action={
            <ButtonLink to={`/items/${itemId}`} variant="outline">
              물품 상세로
            </ButtonLink>
          }
        />
      </>
    );
  }

  if (isInitialLoadError(itemQuery)) {
    return (
      <>
        <PageHeader title="대여 예약" />
        <ErrorState message={getErrorMessage(itemQuery.error)} onRetry={itemQuery.refetch} />
      </>
    );
  }

  const canSubmit = Boolean(item && value?.endDate);

  return (
    <>
      <PageHeader title="대여 예약" />
      <div className="flex flex-col gap-4 pt-3.5 pb-6">
        {item ? <ItemSummaryRow item={item} /> : <ItemSummarySkeleton />}

        {blocker === 'REGION' && (
          <div className="flex flex-col items-start gap-2 rounded-xl bg-accent-soft px-3.5 py-3">
            <p className="text-caption">
              <b className="text-accent-strong">동네 인증이 필요해요.</b> 대표 동네를 인증하면
              예약할 수 있어요.
            </p>
            <ButtonLink to="/regions" variant="outline" size="sm">
              동네 인증하기
            </ButtonLink>
          </div>
        )}

        <section>
          {isInitialLoadError(monthResult) ? (
            <ErrorState
              title="캘린더를 불러오지 못했어요"
              message={getErrorMessage(monthResult.error)}
              onRetry={monthResult.refetch}
            />
          ) : (
            <Calendar
              title={<h2 className="text-price">기간 선택</h2>}
              month={month}
              days={calendar.days}
              value={value}
              onChange={setValue}
              onMonthChange={handleMonthChange}
              minDays={RENTAL_PERIOD.MIN_DAYS}
              maxDays={RENTAL_PERIOD.MAX_DAYS}
              statusLabels={RENTAL_DAY_STATUS}
              minMonth={thisMonth}
              isLoading={monthResult?.isPending ?? true}
            />
          )}
        </section>

        {item && (
          <AmountSummary
            pricePerDay={item.pricePerDay}
            rentalDays={rentalDays}
            rentalFee={rentalFee}
            deposit={item.deposit}
            totalAmount={totalAmount}
          />
        )}

        {PAYMENT_ENABLED && item?.deposit > 0 && (
          <div className="flex flex-col gap-0.5 rounded-xl bg-accent-soft px-3.5 py-3 text-caption">
            <b className="text-accent-strong">보증금은 플랫폼이 보관해요</b>
            <span className="text-ink-muted">반납 후 제공자가 상태를 확인하면 돌려드려요.</span>
          </div>
        )}

        <p className="text-caption text-ink-muted">
          <b className="text-ink">취소 정책</b> · {CANCEL_POLICY}
        </p>
      </div>

      {PAYMENT_ENABLED ? (
        <BottomActionBar className="flex flex-col gap-2">
          <p className="text-center text-xs text-ink-muted">
            예약 후 <b className="text-danger">{PAYMENT_EXPIRE_MINUTES}분 안에</b> 결제하지 않으면
            자동 취소돼요
          </p>
          <Button
            fullWidth
            disabled={!canSubmit}
            isLoading={createMutation.isPending}
            onClick={handleSubmit}
          >
            {totalAmount != null ? `${formatPrice(totalAmount)} 결제하기` : '결제하기'}
          </Button>
        </BottomActionBar>
      ) : (
        <BottomActionBar>
          <Button
            fullWidth
            disabled={!canSubmit}
            isLoading={createMutation.isPending}
            onClick={handleSubmit}
          >
            예약하기
          </Button>
        </BottomActionBar>
      )}
    </>
  );
}
