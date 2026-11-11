import { Link } from 'react-router';

import { Badge, Thumbnail } from '../../../shared/ui';
import { getCardMeta, getDepositText, getPriceParts } from '../itemDisplay';

const LINK_FOCUS =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';

function PriceLine({ pricePerDay }) {
  const price = getPriceParts(pricePerDay);
  return (
    <span className="text-price tabular-nums">
      {price.amount}
      {price.unit && (
        <span className="ml-0.5 text-caption font-medium text-ink-muted">{price.unit}</span>
      )}
    </span>
  );
}

function HeartIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinejoin="round"
      className="size-3.5"
    >
      <path d="M12 20s-7-4.4-7-10a4 4 0 017-2.5A4 4 0 0119 10c0 5.6-7 10-7 10z" />
    </svg>
  );
}

function ChatIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinejoin="round"
      className="size-3.5"
    >
      <path d="M4 5h16v11H9l-5 4z" />
    </svg>
  );
}

/** 관심·채팅 수(표시만. 관심 등록 REQ-022·채팅 REQ-026은 2순위라 누를 수 없다). 채팅 0은 시안처럼 그리지 않는다 */
function Counts({ wishCount, chatCount }) {
  const wish = wishCount ?? 0;
  const chat = chatCount ?? 0;
  return (
    <span className="flex shrink-0 items-center gap-2 text-xs text-ink-subtle tabular-nums">
      <span className="flex items-center gap-0.5" aria-label={`관심 ${wish}`}>
        <HeartIcon />
        <span aria-hidden="true">{wish}</span>
      </span>
      {chat > 0 && (
        <span className="flex items-center gap-0.5" aria-label={`채팅 ${chat}`}>
          <ChatIcon />
          <span aria-hidden="true">{chat}</span>
        </span>
      )}
    </span>
  );
}

/**
 * 홈 그리드 카드(Figma 시안, chore/fe-design-refactor): 정사각형 사진 → 제목 → 가격 → "동네 · 경과 시간"
 * → 보증금 회색 태그 + 관심·채팅 수. 테두리·그림자 없음. "대여 중" 배지는 사진 왼쪽 위.
 */
function GridItemCard({ item }) {
  const depositText = getDepositText(item.deposit);
  return (
    <Link
      to={`/items/${item.itemId}`}
      className={`flex min-w-0 flex-col rounded-[10px] ${LINK_FOCUS}`}
    >
      <div className="relative">
        <Thumbnail url={item.thumbnailUrl} className="aspect-square w-full" />
        {item.isRenting && (
          <Badge tone="green" className="absolute top-2 left-2">
            대여 중
          </Badge>
        )}
      </div>
      <span className="mt-2.5 line-clamp-2 text-body break-words">{item.name}</span>
      <span className="mt-1">
        <PriceLine pricePerDay={item.pricePerDay} />
      </span>
      <span className="mt-1 truncate text-xs text-ink-subtle xl:text-caption">
        {getCardMeta(item.regionName, item.bumpedAt)}
      </span>
      <div className="mt-1.5 flex items-center justify-between gap-2">
        <span className="min-w-0 truncate">
          {depositText && (
            <Badge tone="gray" className="tabular-nums">
              {depositText}
            </Badge>
          )}
        </span>
        <Counts wishCount={item.wishCount} chatCount={item.chatCount} />
      </div>
    </Link>
  );
}

/**
 * 검색 결과 카드 한 줄(시안 Home.html: 썸네일 104px·모서리 10px, 이름 15/600, "동네 · 경과 시간", 일 대여료, 보증금 accent-strong).
 * 전체가 상세 링크. "대여 중" 배지는 카드에만(isRenting).
 */
function ListItemCard({ item }) {
  const depositText = getDepositText(item.deposit);
  return (
    <Link to={`/items/${item.itemId}`} className={`flex gap-3.5 py-3.5 ${LINK_FOCUS}`}>
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
        <PriceLine pricePerDay={item.pricePerDay} />
        {depositText && (
          <span className="text-caption font-semibold text-accent-strong tabular-nums">
            {depositText}
          </span>
        )}
      </div>
    </Link>
  );
}

/** layout: 'list'(검색, 기본) · 'grid'(홈) */
export default function ItemCard({ item, layout = 'list' }) {
  return layout === 'grid' ? <GridItemCard item={item} /> : <ListItemCard item={item} />;
}
