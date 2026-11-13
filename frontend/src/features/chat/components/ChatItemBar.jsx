import { Link } from 'react-router';

import { Badge, ButtonLink, Thumbnail } from '../../../shared/ui';
import { RENTAL_STATUS } from '../../rental/constants';
import { formatShortPeriod } from '../messageView';

/**
 * SCR-10 상단 물품 요약(chat.md "채팅방 상단 대여 상태"): 썸네일 · 물품 이름 · 최근 대여 건 상태 배지와 기간 · 버튼.
 * - 최근 대여 건이 있으면 "대여 상세"(/rentals/:id). 상태 라벨은 RENTAL_STATUS(state-machines.md Enum)
 * - 없으면 대여자에게만 "예약하기"(삭제된 물품이면 없음). 소유자는 버튼 없음
 * 시안의 일 대여료는 방 정보 응답에 없어 그리지 않는다.
 */
export default function ChatItemBar({ room, isRenter }) {
  const { item, latestRental } = room;
  const status = latestRental ? RENTAL_STATUS[latestRental.status] : null;

  let action = null;
  if (latestRental) {
    action = (
      <ButtonLink to={`/rentals/${latestRental.rentalId}`} variant="outline" size="sm">
        대여 상세
      </ButtonLink>
    );
  } else if (isRenter && !item.isDeleted) {
    action = (
      <ButtonLink to={`/items/${item.itemId}/reserve`} size="sm">
        예약하기
      </ButtonLink>
    );
  }

  const name = item.isDeleted ? (
    <span className="truncate text-ink-muted">삭제된 물품</span>
  ) : (
    <Link
      to={`/items/${item.itemId}`}
      className="truncate hover:underline focus-visible:outline-2 focus-visible:outline-primary"
    >
      {item.name}
    </Link>
  );

  return (
    <div className="flex items-center gap-3 border-b border-line bg-white px-4 py-3">
      <Thumbnail url={item.isDeleted ? null : item.thumbnailUrl} className="size-12" />
      <div className="min-w-0 flex-1">
        <p className="flex min-w-0 items-center gap-2 text-body font-semibold">
          {name}
          {status && (
            <Badge tone={status.tone} className="shrink-0">
              {status.label}
            </Badge>
          )}
        </p>
        {latestRental && (
          <p className="mt-0.5 text-caption text-ink-muted">
            {formatShortPeriod(latestRental.startDate, latestRental.endDate)}
          </p>
        )}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}
