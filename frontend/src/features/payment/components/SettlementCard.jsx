import { Link } from 'react-router';

import { Badge, Thumbnail } from '../../../shared/ui';
import { formatDateRange } from '../../../shared/lib/date';
import { getSettlementView } from '../paymentHistory';
import AmountRows from './AmountRows';

/** 정산 내역 한 줄(SettlementResponse). 카드 전체가 대여 상세 링크 */
export default function SettlementCard({ settlement }) {
  const view = getSettlementView(settlement);
  return (
    <Link
      to={`/rentals/${settlement.rentalId}`}
      className="flex gap-3.5 py-3.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Thumbnail url={settlement.item?.thumbnailUrl} className="size-16" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex items-start justify-between gap-2">
          <span className={`truncate font-semibold ${view.isItemDeleted ? 'text-ink-muted' : ''}`}>
            {view.itemName}
          </span>
          <Badge tone={view.status.tone} className="shrink-0">
            {view.status.label}
          </Badge>
        </div>
        <span className="text-caption text-ink-muted">
          {view.sourceLabel} · {formatDateRange(settlement.startDate, settlement.endDate)}
        </span>
        <AmountRows rows={view.rows} />
        <div className="mt-1 flex items-center justify-between gap-2">
          <span className="min-w-0 text-xs text-ink-muted">{view.dateText}</span>
          <span className="shrink-0 text-caption whitespace-nowrap tabular-nums">
            <span className="text-ink-muted">지급액</span>{' '}
            <b className="font-semibold">{view.payout}</b>
          </span>
        </div>
      </div>
    </Link>
  );
}
