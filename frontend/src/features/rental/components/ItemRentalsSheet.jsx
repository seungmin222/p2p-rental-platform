import { Link } from 'react-router';

import { formatDateRange } from '../../../shared/lib/date';
import { isInitialLoadError } from '../../../shared/lib/queryState';
import { useRefetchErrorToast } from '../../../shared/hooks/useRefetchErrorToast';
import {
  Badge,
  BottomSheet,
  EmptyState,
  ErrorState,
  InfiniteListFooter,
  Skeleton,
} from '../../../shared/ui';
import { RENTAL_STATUS } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { useItemRentals } from '../queries';

function SheetBody({ query }) {
  if (isInitialLoadError(query)) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  }
  if (!query.data) {
    return (
      <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-2 py-2">
        {[0, 1, 2].map((key) => (
          <Skeleton key={key} className="h-12 w-full" />
        ))}
      </div>
    );
  }
  if (query.data.length === 0) {
    return <EmptyState title="아직 예약이 없어요" />;
  }
  return (
    <>
      <ul className="-mx-5">
        {query.data.map((rental) => {
          const status = RENTAL_STATUS[rental.status];
          return (
            <li key={rental.rentalId} className="border-b border-divider last:border-b-0">
              <Link
                to={`/rentals/${rental.rentalId}`}
                className="flex min-h-14 items-center justify-between gap-3 px-5 py-2.5 hover:bg-surface focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
              >
                <span className="flex min-w-0 flex-col">
                  <span className="text-body tabular-nums">
                    {formatDateRange(rental.startDate, rental.endDate)}
                  </span>
                  <span className="truncate text-caption text-ink-muted">
                    대여자 {rental.renterNickname}
                  </span>
                </span>
                <Badge tone={status?.tone} className="shrink-0">
                  {status?.label ?? rental.status}
                </Badge>
              </Link>
            </li>
          );
        })}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}

/**
 * 내 물품의 예약 현황(REQ-020, 기간순). 열린 동안만 렌더하고 그때 부른다. 줄마다 대여 상세(제공자 뷰)로.
 */
export default function ItemRentalsSheet({ item, onClose }) {
  const query = useItemRentals(item.itemId, { enabled: true });
  useRefetchErrorToast([query]);
  return (
    <BottomSheet open onClose={onClose} title={`${item.name} 예약 현황`}>
      <SheetBody query={query} />
    </BottomSheet>
  );
}
