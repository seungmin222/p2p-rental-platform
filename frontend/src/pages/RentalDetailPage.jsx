import { useParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import AmountSummary from '../features/rental/components/AmountSummary';
import InspectionRecords from '../features/rental/components/InspectionRecords';
import RentalActionBar from '../features/rental/components/RentalActionBar';
import RentalStepBar from '../features/rental/components/RentalStepBar';
import {
  CANCEL_REASON,
  CANCEL_TYPE,
  PAYMENT_ENABLED,
  RENTAL_STATUS,
} from '../features/rental/constants';
import { getErrorMessage } from '../features/rental/errorMessages';
import { useRental, useRentalInspections } from '../features/rental/queries';
import {
  findInspection,
  formatDateTime,
  formatShortRange,
  getDepositNotice,
} from '../features/rental/rentalData';
import { getRentalSteps } from '../features/rental/rentalSteps';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { todayInSeoul } from '../shared/lib/date';
import { formatPrice } from '../shared/lib/format';
import { isInitialLoadError } from '../shared/lib/queryState';
import { Badge, ButtonLink, ErrorState, Skeleton } from '../shared/ui';

// 인계 이후 상태만 인계·반납 기록이 있다
const STATUSES_WITH_RECORDS = ['RENTING', 'OVERDUE', 'RETURNED', 'COMPLETED'];
const UNAVAILABLE_CODES = ['RENTAL_NOT_FOUND', 'RENTAL_NOT_PARTICIPANT'];

function reasonText(reason, text) {
  const label = CANCEL_REASON[reason]?.label ?? reason;
  return text ? `${label} · ${text}` : label;
}

function InfoRow({ label, children }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="shrink-0 text-ink-muted">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}

/** CANCELED: 스텝바 대신 취소 정보. 수수료는 결제가 켜져 있을 때만(1순위엔 받지 않음) */
function CancelInfoCard({ rental }) {
  return (
    <section aria-label="취소 정보" className="rounded-xl bg-surface p-3.5">
      <p className="text-body font-semibold">취소된 예약이에요</p>
      <dl className="mt-2 flex flex-col gap-1 text-caption">
        <InfoRow label="취소 종류">
          {CANCEL_TYPE[rental.cancelType]?.label ?? rental.cancelType}
        </InfoRow>
        {rental.cancelReason && (
          <InfoRow label="사유">{reasonText(rental.cancelReason, rental.cancelReasonText)}</InfoRow>
        )}
        {rental.canceledAt && (
          <InfoRow label="취소 시각">{formatDateTime(rental.canceledAt)}</InfoRow>
        )}
        {PAYMENT_ENABLED && rental.cancelFee > 0 && (
          <InfoRow label="취소 수수료">{formatPrice(rental.cancelFee)}</InfoRow>
        )}
      </dl>
    </section>
  );
}

/** 보증금 처리 결과. 결제가 켜져 있을 때만(rentalData.getDepositNotice의 TODO 참고) */
function DepositNoticeCard({ notice }) {
  const text = {
    REFUNDED: `보증금 ${formatPrice(notice.amount)} 반환을 완료했어요`,
    REFUNDING: '보증금 반환을 처리하고 있어요',
    WAITING: '보증금 처리를 기다리고 있어요',
  }[notice.kind];
  return (
    <section aria-label="보증금" className="rounded-xl bg-accent-soft px-3.5 py-3 text-caption">
      <p className="font-semibold text-accent-strong">{text}</p>
    </section>
  );
}

/** 진행 중인 양측 동의 취소 요청(RESERVED일 때만 응답에 있다) */
function CancelAgreementCard({ agreement, counterpartNickname }) {
  return (
    <section aria-label="취소 요청" className="rounded-xl bg-accent-soft px-3.5 py-3 text-caption">
      <p className="font-semibold text-accent-strong">
        {agreement.requestedByMe
          ? '취소를 요청했어요'
          : `${counterpartNickname}님이 취소를 요청했어요`}
      </p>
      <p className="mt-1">사유 · {reasonText(agreement.reason, agreement.reasonText)}</p>
      <p className="mt-0.5 text-ink-muted">
        {formatDateTime(agreement.expiresAt)}까지 응답이 없으면 요청이 만료돼요
      </p>
    </section>
  );
}

function DetailSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-3.5 pt-3.5">
      <Skeleton className="h-8 w-full" />
      <Skeleton className="h-4 w-3/4" />
      <Skeleton className="h-27 w-full rounded-xl" />
      <Skeleton className="h-28 w-full rounded-xl" />
    </div>
  );
}

/** SCR-08 대여 상세(시안 RentalDetail.html). 버튼은 getRentalActions 표로만 정한다. */
export default function RentalDetailPage() {
  const { id } = useParams();
  const rentalId = Number(id);
  const isValidId = Number.isInteger(rentalId) && rentalId > 0;
  const rentalQuery = useRental(isValidId ? rentalId : null);
  const rental = rentalQuery.data;
  const hasRecords = rental != null && STATUSES_WITH_RECORDS.includes(rental.status);
  const inspectionsQuery = useRentalInspections(rentalId, { enabled: hasRecords });
  const today = todayInSeoul();
  // 이미 보던 화면은 재조회가 실패해도 그대로 둔다(열린 모달·시트 유지)
  useRefetchErrorToast([rentalQuery, inspectionsQuery]);
  const isLoadFailed = isInitialLoadError(rentalQuery);

  if (!isValidId || (isLoadFailed && UNAVAILABLE_CODES.includes(rentalQuery.error.code))) {
    return (
      <>
        <PageHeader title="대여 상세" />
        <ErrorState
          title="대여 정보를 볼 수 없어요"
          message="없는 대여이거나 내가 참여한 대여가 아니에요."
        />
        <div className="flex justify-center">
          <ButtonLink to="/" variant="secondary">
            홈으로
          </ButtonLink>
        </div>
      </>
    );
  }

  if (isLoadFailed) {
    return (
      <>
        <PageHeader title="대여 상세" />
        <ErrorState message={getErrorMessage(rentalQuery.error)} onRetry={rentalQuery.refetch} />
      </>
    );
  }

  if (!rental) {
    return (
      <>
        <PageHeader title="대여 상세" />
        <DetailSkeleton />
      </>
    );
  }

  const status = RENTAL_STATUS[rental.status];
  const steps = getRentalSteps(rental.status);
  // undefined = 아직 못 불러옴(getRentalActions가 버튼을 그리지 않는다)
  const returnInspection = hasRecords ? findInspection(inspectionsQuery.data, 'RETURN') : null;
  const itemName = rental.item.isDeleted ? '삭제된 물품' : rental.item.name;
  const depositNotice = PAYMENT_ENABLED ? getDepositNotice(rental, returnInspection) : null;

  return (
    <>
      <PageHeader
        title="대여 상세"
        action={<Badge tone={status?.tone}>{status?.label ?? rental.status}</Badge>}
      />
      <div className="flex flex-col gap-3.5 pt-3.5 pb-6">
        {steps ? <RentalStepBar steps={steps} /> : <CancelInfoCard rental={rental} />}

        <p className="text-caption text-ink-muted">
          {itemName} · {formatShortRange(rental.startDate, rental.endDate)} ·{' '}
          {rental.myRole === 'RENTER' ? '제공자' : '대여자'}{' '}
          <b className="text-ink">{rental.counterpart.nickname}</b>
        </p>

        {rental.activeCancelAgreement && (
          <CancelAgreementCard
            agreement={rental.activeCancelAgreement}
            counterpartNickname={rental.counterpart.nickname}
          />
        )}

        {depositNotice && <DepositNoticeCard notice={depositNotice} />}

        {hasRecords && <InspectionRecords query={inspectionsQuery} />}

        <AmountSummary
          pricePerDay={rental.pricePerDay}
          rentalDays={rental.rentalDays}
          rentalFee={rental.rentalFee}
          deposit={rental.deposit}
          totalAmount={rental.totalAmount}
        />
      </div>
      <RentalActionBar rental={rental} returnInspection={returnInspection} today={today} />
    </>
  );
}
