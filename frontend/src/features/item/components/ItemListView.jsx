import { EmptyState, ErrorState, InfiniteListFooter, Skeleton } from '../../../shared/ui';
import { isInitialLoadError } from '../../../shared/lib/queryState';
import { getErrorMessage } from '../errorMessages';
import ItemCard from './ItemCard';

// 홈 그리드: 모바일 2열, PC(xl) 4열(Figma 시안 간격 12·24 / 16·32)
const GRID = 'grid grid-cols-2 gap-x-3 gap-y-6 pt-2 xl:grid-cols-4 xl:gap-x-4 xl:gap-y-8';

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

function GridSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중" className={GRID}>
      {[0, 1, 2, 3].map((key) => (
        <li key={key} className="flex flex-col gap-2">
          <Skeleton className="aspect-square w-full rounded-[10px]" />
          <Skeleton className="h-4 w-2/3" />
          <Skeleton className="h-4 w-1/2" />
          <Skeleton className="h-3 w-2/5" />
        </li>
      ))}
    </ul>
  );
}

/**
 * 홈 피드·검색 결과가 같이 쓰는 목록. query는 useInfiniteQuery 결과이고 items는 카드 배열(없으면 아직 못 받음).
 * 처음 불러오기 실패만 전체 오류로 바꾸고, 이미 목록이 있으면 그대로 둔다(재조회 실패는 화면이 토스트로 알린다).
 * layout: 'list'(검색, 기본 — 구분선 있는 한 줄 카드) · 'grid'(홈 — 사진 그리드, 테두리·구분선 없음)
 */
export default function ItemListView({
  query,
  items,
  emptyTitle,
  emptyDescription,
  layout = 'list',
}) {
  const isGrid = layout === 'grid';
  if (isInitialLoadError(query)) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  }
  if (!items) {
    return isGrid ? <GridSkeleton /> : <ListSkeleton />;
  }
  if (items.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }
  return (
    <>
      <ul className={isGrid ? GRID : undefined}>
        {items.map((item) => (
          <li key={item.itemId} className={isGrid ? 'min-w-0' : 'border-b border-divider'}>
            <ItemCard item={item} layout={layout} />
          </li>
        ))}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}
