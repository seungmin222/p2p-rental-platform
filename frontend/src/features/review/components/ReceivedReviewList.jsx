import { useRefetchErrorToast } from '../../../shared/hooks/useRefetchErrorToast';
import { isInitialLoadError } from '../../../shared/lib/queryState';
import { EmptyState, ErrorState, InfiniteListFooter, Skeleton } from '../../../shared/ui';
import { getErrorMessage } from '../errorMessages';
import { useReceivedReviews } from '../queries';
import ReceivedReviewItem from './ReceivedReviewItem';

function ListSkeleton() {
  return (
    <ul aria-busy="true">
      {['a', 'b', 'c'].map((key) => (
        <li key={key} className="flex gap-3 border-b border-divider py-3.5">
          <Skeleton className="size-10 rounded-full" />
          <div className="flex flex-1 flex-col gap-2 py-1">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-4 w-full" />
          </div>
        </li>
      ))}
    </ul>
  );
}

/**
 * 회원이 받은 후기 목록(REQ-034, 최신순·무한 스크롤). 화면 연결은 사용자 프로필(SCR-13, REQ-021) 브랜치에서 한다.
 * 지금은 /dev/ui 미리보기에서만 쓴다.
 */
export default function ReceivedReviewList({ memberId }) {
  const query = useReceivedReviews(memberId);
  useRefetchErrorToast([query]);

  if (isInitialLoadError(query)) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  }
  if (!query.data) {
    return <ListSkeleton />;
  }
  if (query.data.length === 0) {
    return <EmptyState title="아직 받은 후기가 없어요" />;
  }
  return (
    <>
      <ul>
        {query.data.map((review) => (
          <li key={review.reviewId} className="border-b border-divider">
            <ReceivedReviewItem review={review} />
          </li>
        ))}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}
