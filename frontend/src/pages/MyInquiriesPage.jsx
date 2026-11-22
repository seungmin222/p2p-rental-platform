import PageHeader from '../app/layout/PageHeader';
import InquiryCard from '../features/inquiry/components/InquiryCard';
import { getErrorMessage } from '../features/inquiry/errorMessages';
import { getInquiryWritePath } from '../features/inquiry/inquiryForm';
import { useMyInquiries } from '../features/inquiry/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { isInitialLoadError } from '../shared/lib/queryState';
import { ButtonLink, EmptyState, ErrorState, InfiniteListFooter, Skeleton } from '../shared/ui';

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중">
      {[0, 1, 2, 3].map((key) => (
        <li key={key} className="flex flex-col gap-2 border-b border-divider py-3.5">
          <Skeleton className="h-5 w-24 rounded-full" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-3 w-1/3" />
        </li>
      ))}
    </ul>
  );
}

function InquiryList() {
  const query = useMyInquiries();
  // 이미 보던 목록은 재조회가 실패해도 그대로 둔다
  useRefetchErrorToast([query]);

  if (isInitialLoadError(query)) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  }
  if (!query.data) {
    return <ListSkeleton />;
  }
  if (query.data.length === 0) {
    return (
      <EmptyState
        title="접수한 문의가 없어요"
        description="거래 중 문제가 있으면 대여 상세에서 분쟁을 접수할 수 있어요"
      />
    );
  }
  return (
    <>
      <ul>
        {query.data.map((inquiry) => (
          <li key={inquiry.inquiryId} className="border-b border-divider">
            <InquiryCard inquiry={inquiry} />
          </li>
        ))}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}

/**
 * SCR-15 내 문의(`/me/inquiries`, REQ-063). 최신 접수 순, 처리 상태는 관리자가 바꾼다(REQ-057).
 * 대여와 상관없는 일반 문의는 오른쪽 위 "문의하기"로, 분쟁은 대여 상세에서 접수한다.
 */
export default function MyInquiriesPage() {
  return (
    <>
      <PageHeader
        title="문의 내역"
        action={
          <ButtonLink to={getInquiryWritePath()} variant="secondary" size="sm">
            문의하기
          </ButtonLink>
        }
      />
      <InquiryList />
    </>
  );
}
