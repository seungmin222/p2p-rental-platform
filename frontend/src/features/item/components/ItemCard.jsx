import { Link } from 'react-router';

import { Badge, Thumbnail } from '../../../shared/ui';
import { getCardMeta, getDepositText, getPriceParts } from '../itemDisplay';

/**
 * 피드·검색 카드 한 줄(시안 Home.html: 썸네일 104px·모서리 10px, 이름 15/600, "동네 · 경과 시간", 일 대여료, 보증금 accent-strong).
 * 전체가 상세 링크. "대여 중" 배지는 카드에만(isRenting). 관심·채팅 수는 2순위라 그리지 않는다.
 */
export default function ItemCard({ item }) {
  const price = getPriceParts(item.pricePerDay);
  const depositText = getDepositText(item.deposit);
  return (
    <Link
      to={`/items/${item.itemId}`}
      className="flex gap-3.5 py-3.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <Thumbnail url={item.thumbnailUrl} className="size-26" />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex items-start justify-between gap-2">
          <span className="truncate font-semibold">{item.name}</span>
          {item.isRenting && (
            <Badge tone="green" className="shrink-0">
              대여 중
            </Badge>
          )}
        </div>
        <span className="truncate text-caption text-ink-muted">
          {getCardMeta(item.regionName, item.bumpedAt)}
        </span>
        <span className="text-price tabular-nums">
          {price.amount}
          {price.unit && (
            <span className="text-body font-normal text-ink-muted"> {price.unit}</span>
          )}
        </span>
        {depositText && (
          <span className="text-caption font-semibold text-accent-strong tabular-nums">
            {depositText}
          </span>
        )}
      </div>
    </Link>
  );
}
