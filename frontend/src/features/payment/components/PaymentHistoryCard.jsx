import { Link } from 'react-router';

import { Badge, Thumbnail } from '../../../shared/ui';
import { formatDateRange } from '../../../shared/lib/date';
import { getPaymentHistoryView } from '../paymentHistory';
import AmountRows from './AmountRows';

/** 결제·보증금 내역 한 줄(PaymentHistoryResponse). 카드 전체가 대여 상세 링크(payment.md "클릭 → 대여 상세") */
export default function PaymentHistoryCard({ payment }) {
  const view = getPaymentHistoryView(payment);
  return (
    <Link
      to={`/rentals/${payment.rentalId}`}
      className="flex gap-3.5 py-3.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Thumbnail url={payment.item?.thumbnailUrl} className="size-16" />
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
          {view.kindLabel} · {view.paidAt}
        </span>
        <span className="text-caption text-ink-muted">
          {formatDateRange(payment.startDate, payment.endDate)}
        </span>
        <div className="mt-1 flex items-center justify-between gap-2">
          {view.deposit ? <Badge tone={view.deposit.tone}>{view.deposit.label}</Badge> : <span />}
          <span className="shrink-0 text-caption whitespace-nowrap tabular-nums">
            <span className="text-ink-muted">결제 금액</span>{' '}
            <b className="font-semibold">{view.amount}</b>
          </span>
        </div>
        <AmountRows rows={view.rows} />
        {view.notes.map((note) => (
          <p key={note} className="text-xs text-ink-muted">
            {note}
          </p>
        ))}
      </div>
    </Link>
  );
}
