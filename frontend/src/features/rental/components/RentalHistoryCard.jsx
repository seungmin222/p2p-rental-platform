import { Link } from 'react-router';

import { formatDateRange } from '../../../shared/lib/date';
import { formatPrice } from '../../../shared/lib/format';
import { Badge, Thumbnail } from '../../../shared/ui';
import { PAYMENT_ENABLED, RENTAL_STATUS } from '../constants';

/** 빌린 내역 한 줄(RentalHistoryResponse). 전체가 대여 상세 링크 */
export default function RentalHistoryCard({ rental }) {
  const status = RENTAL_STATUS[rental.status];
  const itemName = rental.item.isDeleted ? '삭제된 물품' : rental.item.name;
  return (
    <Link
      to={`/rentals/${rental.rentalId}`}
      className="flex gap-3.5 py-3.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Thumbnail url={rental.item.thumbnailUrl} className="size-16" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex items-start justify-between gap-2">
          <span
            className={`truncate font-semibold ${rental.item.isDeleted ? 'text-ink-muted' : ''}`}
          >
            {itemName}
          </span>
          <Badge tone={status?.tone} className="shrink-0">
            {status?.label ?? rental.status}
          </Badge>
        </div>
        <span className="truncate text-caption text-ink-muted">제공자 {rental.owner.nickname}</span>
        <span className="text-caption text-ink-muted">
          {formatDateRange(rental.startDate, rental.endDate)}
        </span>
        <span className="text-caption tabular-nums">
          {/* 결제가 꺼진 1순위엔 실제 결제가 없어 "합계"(대여 상세 금액 카드와 같은 규칙) */}
          <span className="text-ink-muted">{PAYMENT_ENABLED ? '결제 금액' : '합계'}</span>{' '}
          <b className="font-semibold">{formatPrice(rental.totalAmount)}</b>
        </span>
      </div>
    </Link>
  );
}
