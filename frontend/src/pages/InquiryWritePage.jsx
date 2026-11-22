import { useSearchParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import InquiryForm from '../features/inquiry/components/InquiryForm';
import { getInquiryWritePath, readInquiryParams } from '../features/inquiry/inquiryForm';
import { RENTAL_STATUS } from '../features/rental/constants';
import { getErrorMessage as getRentalErrorMessage } from '../features/rental/errorMessages';
import { useRental } from '../features/rental/queries';
import { isRentalUnavailable } from '../features/review/reviewData';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { formatDateRange } from '../shared/lib/date';
import { isInitialLoadError } from '../shared/lib/queryState';
import { Badge, ButtonLink, ErrorState, Skeleton, Thumbnail } from '../shared/ui';

const TITLE = '문의·분쟁 접수';

/** 연결한 대여 요약(물품·상태·기간). 접수 대상을 확인하게 한다 */
function RentalSummary({ rental }) {
  const status = RENTAL_STATUS[rental.status];
  const itemName = rental.item.isDeleted ? '삭제된 물품' : rental.item.name;
  return (
    <section
      aria-label="연결한 대여"
      className="mt-3.5 flex items-center gap-3.5 rounded-xl bg-surface px-4 py-3"
    >
      <Thumbnail url={rental.item.thumbnailUrl} className="size-12" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="text-xs text-ink-muted">연결한 대여</span>
        <span className="truncate font-semibold">{itemName}</span>
        <span className="text-caption text-ink-muted">
          {formatDateRange(rental.startDate, rental.endDate)}
        </span>
      </div>
      {status && (
        <Badge tone={status.tone} className="shrink-0">
          {status.label}
        </Badge>
      )}
    </section>
  );
}

function FormSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-4 pt-3.5">
      <Skeleton className="h-18 w-full rounded-xl" />
      <Skeleton className="h-9 w-48 rounded-full" />
      <Skeleton className="h-40 w-full rounded-lg" />
    </div>
  );
}

/** 대여를 연결한 접수: 대여 상세를 불러와 요약을 보이고, 결제 대기면 분쟁을 막는다 */
function RentalInquiry({ rentalId, initialType }) {
  const rentalQuery = useRental(rentalId);
  const rental = rentalQuery.data;
  const isUnavailable = isRentalUnavailable(rentalQuery);
  useRefetchErrorToast(isUnavailable ? [] : [rentalQuery]);

  if (isUnavailable) {
    return (
      <>
        <ErrorState
          title="대여 정보를 볼 수 없어요"
          message="없는 대여이거나 내가 참여한 대여가 아니에요. 대여와 상관없는 문의는 따로 보낼 수 있어요."
        />
        <div className="flex justify-center">
          <ButtonLink to={getInquiryWritePath()} variant="secondary" replace>
            일반 문의하기
          </ButtonLink>
        </div>
      </>
    );
  }
  if (isInitialLoadError(rentalQuery)) {
    return (
      <ErrorState
        message={getRentalErrorMessage(rentalQuery.error)}
        onRetry={rentalQuery.refetch}
      />
    );
  }
  if (!rental) {
    return <FormSkeleton />;
  }
  return (
    <>
      <RentalSummary rental={rental} />
      <InquiryForm rentalId={rentalId} rental={rental} initialType={initialType} />
    </>
  );
}

/**
 * SCR-15 문의·분쟁 접수(`/me/inquiries/new?rentalId=&type=`, REQ-063).
 * 대여 상세에서 들어오면 그 대여를 연결하고(일반 문의·분쟁), `/me`·문의 내역에서 들어오면 대여 없이 일반 문의만.
 */
export default function InquiryWritePage() {
  const [searchParams] = useSearchParams();
  const { rentalId, type } = readInquiryParams(searchParams);
  return (
    <>
      <PageHeader title={TITLE} />
      {rentalId == null ? (
        <InquiryForm rentalId={null} initialType="GENERAL" />
      ) : (
        <RentalInquiry key={rentalId} rentalId={rentalId} initialType={type} />
      )}
    </>
  );
}
