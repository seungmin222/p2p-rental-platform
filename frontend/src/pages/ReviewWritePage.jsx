import { useParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import { getErrorMessage as getRentalErrorMessage } from '../features/rental/errorMessages';
import { useRental } from '../features/rental/queries';
import ReviewForm from '../features/review/components/ReviewForm';
import { REVIEW_WRITE_DEADLINE_DAYS } from '../features/review/constants';
import { getReviewBlockReason } from '../features/review/reviewData';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { isInitialLoadError } from '../shared/lib/queryState';
import { ButtonLink, EmptyState, ErrorState, Skeleton } from '../shared/ui';

const TITLE = '후기 쓰기';
const UNAVAILABLE_CODES = ['RENTAL_NOT_FOUND', 'RENTAL_NOT_PARTICIPANT'];

// 쓸 수 없을 때의 안내(getReviewBlockReason)
const BLOCKED = {
  WRITTEN: { title: '이미 후기를 보냈어요', description: '보낸 후기는 고칠 수 없어요.' },
  DEADLINE_PASSED: {
    title: '후기를 쓸 수 없는 대여예요',
    description: `작성 기간(거래 완료 후 ${REVIEW_WRITE_DEADLINE_DAYS}일)이 지났어요.`,
  },
  NOT_COMPLETED: {
    title: '후기를 쓸 수 없는 대여예요',
    description: '거래가 완료된 뒤에 쓸 수 있어요.',
  },
};

function FormSkeleton() {
  return (
    <div aria-busy="true" className="flex flex-col gap-4 pt-3.5">
      <Skeleton className="h-7 w-3/4" />
      <Skeleton className="h-4 w-1/2" />
      <Skeleton className="h-9 w-full rounded-full" />
      <Skeleton className="h-28 w-full rounded-lg" />
    </div>
  );
}

/**
 * SCR-14 후기 작성(`/rentals/:id/review`). 대여 상세(canWriteReview·reviewWritten)로 쓸 수 있는지 정하고,
 * 쓸 수 없으면 폼 대신 안내 + 대여 상세 버튼을 보인다(자동으로 옮기지 않는다 — 뒤로 가기 루프 방지).
 * 대여자·제공자 모두 이 화면을 쓰고, 받는 사람은 상세의 counterpart다.
 */
export default function ReviewWritePage() {
  const { id } = useParams();
  const rentalId = Number(id);
  const isValidId = Number.isInteger(rentalId) && rentalId > 0;
  const rentalQuery = useRental(isValidId ? rentalId : null);
  const rental = rentalQuery.data;
  useRefetchErrorToast([rentalQuery]);
  const isLoadFailed = isInitialLoadError(rentalQuery);

  if (!isValidId || (isLoadFailed && UNAVAILABLE_CODES.includes(rentalQuery.error.code))) {
    return (
      <>
        <PageHeader title={TITLE} />
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
        <PageHeader title={TITLE} />
        <ErrorState
          message={getRentalErrorMessage(rentalQuery.error)}
          onRetry={rentalQuery.refetch}
        />
      </>
    );
  }

  if (!rental) {
    return (
      <>
        <PageHeader title={TITLE} />
        <FormSkeleton />
      </>
    );
  }

  const blockReason = getReviewBlockReason(rental);
  if (blockReason) {
    const { title, description } = BLOCKED[blockReason];
    return (
      <>
        <PageHeader title={TITLE} />
        <EmptyState
          title={title}
          description={description}
          action={
            <ButtonLink to={`/rentals/${rental.rentalId}`} variant="secondary" replace>
              대여 상세로
            </ButtonLink>
          }
        />
      </>
    );
  }

  const itemName = rental.item.isDeleted ? '삭제된 물품' : rental.item.name;
  return (
    <>
      <PageHeader title={TITLE} />
      <div className="flex flex-col gap-1 pt-3.5">
        <h2 className="text-heading">{rental.counterpart.nickname}님과의 거래는 어땠나요?</h2>
        <p className="text-caption text-ink-muted">
          {itemName} · {rental.myRole === 'RENTER' ? '빌려준 분에게' : '빌린 분에게'}
        </p>
      </div>
      <ReviewForm rental={rental} />
    </>
  );
}
