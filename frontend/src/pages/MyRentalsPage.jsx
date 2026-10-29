import PageHeader from '../app/layout/PageHeader';
import RentalHistoryCard from '../features/rental/components/RentalHistoryCard';
import { RENTAL_TABS } from '../features/rental/constants';
import { getErrorMessage } from '../features/rental/errorMessages';
import { useRentalHistory } from '../features/rental/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { useTabParam } from '../shared/hooks/useTabParam';
import { isInitialLoadError } from '../shared/lib/queryState';
import {
  ButtonLink,
  EmptyState,
  ErrorState,
  InfiniteListFooter,
  Skeleton,
  Tabs,
} from '../shared/ui';

const DEFAULT_TAB = 'ONGOING';
const TAB_ITEMS = Object.entries(RENTAL_TABS).map(([value, { label }]) => ({ value, label }));
const EMPTY_TITLES = {
  UPCOMING: '대여 예정인 물품이 없어요',
  ONGOING: '대여 중인 물품이 없어요',
  DONE: '완료된 대여가 없어요',
};

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중">
      {[0, 1, 2, 3].map((key) => (
        <li key={key} className="flex gap-3.5 border-b border-divider py-3.5">
          <Skeleton className="size-16 rounded-[10px]" />
          <div className="flex flex-1 flex-col gap-2 py-1">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3 w-full" />
            <Skeleton className="h-3 w-1/3" />
          </div>
        </li>
      ))}
    </ul>
  );
}

function HistoryList({ tab }) {
  const query = useRentalHistory(tab);
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
        title={EMPTY_TITLES[tab]}
        description="동네 이웃의 물건을 빌려 보세요"
        action={
          <ButtonLink to="/" variant="secondary">
            홈으로
          </ButtonLink>
        }
      />
    );
  }
  return (
    <>
      <ul>
        {query.data.map((rental) => (
          <li key={rental.rentalId} className="border-b border-divider">
            <RentalHistoryCard rental={rental} />
          </li>
        ))}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}

/** SCR-12 빌린 내역(REQ-019). 탭은 URL ?tab=(대여 상세에서 뒤로 오면 같은 탭) */
export default function MyRentalsPage() {
  const [tab, setTab] = useTabParam(RENTAL_TABS, DEFAULT_TAB);
  return (
    <>
      <PageHeader title="빌린 내역" />
      <Tabs
        label="빌린 내역 탭"
        items={TAB_ITEMS}
        value={tab}
        onChange={setTab}
        listClassName="sticky top-14 z-10 -mx-4 bg-white px-4"
      >
        {/* 탭마다 새 목록(스켈레톤·빈 상태·오류가 탭별로) */}
        <HistoryList key={tab} tab={tab} />
      </Tabs>
    </>
  );
}
