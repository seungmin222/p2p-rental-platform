import { EmptyState, ErrorState, InfiniteListFooter, Skeleton } from '../../../shared/ui';
import { isInitialLoadError } from '../../../shared/lib/queryState';
import { getErrorMessage } from '../errorMessages';
import ItemCard from './ItemCard';

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중">
      {[0, 1, 2, 3].map((key) => (
        <li key={key} className="flex gap-3.5 border-b border-divider py-3.5">
          <Skeleton className="size-26 shrink-0 rounded-[10px]" />
          <div className="flex flex-1 flex-col gap-2 py-1">
            <Skeleton className="h-4 w-2/3" />
            <Skeleton className="h-3 w-1/3" />
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-3 w-2/5" />
          </div>
        </li>
      ))}
    </ul>
  );
}

/**
 * 홈 피드·검색 결과가 같이 쓰는 목록. query는 useInfiniteQuery 결과이고 items는 카드 배열(없으면 아직 못 받음).
 * 처음 불러오기 실패만 전체 오류로 바꾸고, 이미 목록이 있으면 그대로 둔다(재조회 실패는 화면이 토스트로 알린다).
 */
export default function ItemListView({ query, items, emptyTitle, emptyDescription }) {
  if (isInitialLoadError(query)) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  }
  if (!items) {
    return <ListSkeleton />;
  }
  if (items.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }
  return (
    <>
      <ul>
        {items.map((item) => (
          <li key={item.itemId} className="border-b border-divider">
            <ItemCard item={item} />
          </li>
        ))}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}
