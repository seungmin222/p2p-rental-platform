import { formatElapsed } from '../../../shared/lib/format';
import { ITEM_CONDITION } from '../constants';

function MemberAvatar({ url }) {
  if (url) {
    return (
      <img src={url} alt="" className="size-10 shrink-0 rounded-full bg-placeholder object-cover" />
    );
  }
  return <div aria-hidden="true" className="size-10 shrink-0 rounded-full bg-primary-soft" />;
}

/** 제공자 줄. 거래 횟수·신뢰 점수는 1순위 응답에 없어 그리지 않는다(신뢰 점수는 REQ-054, 3순위) */
export function ItemOwnerRow({ owner }) {
  return (
    <section aria-label="제공자" className="flex items-center gap-3 border-b border-divider py-4">
      <MemberAvatar url={owner.profileImageUrl} />
      <div className="flex min-w-0 flex-col">
        <span className="truncate font-semibold">{owner.nickname}</span>
        {owner.regionName && (
          <span className="truncate text-caption text-ink-muted">{owner.regionName}</span>
        )}
      </div>
    </section>
  );
}

/** 제목·"카테고리 · 상태 · 경과 시간"·설명(줄바꿈 유지) */
export function ItemSummary({ item }) {
  const meta = [
    item.categoryName,
    ITEM_CONDITION[item.condition]?.label,
    formatElapsed(item.bumpedAt),
  ]
    .filter(Boolean)
    .join(' · ');
  return (
    <section className="flex flex-col gap-1 pt-5">
      <h1 className="text-title">{item.name}</h1>
      <p className="text-caption text-ink-muted">{meta}</p>
      <p className="mt-3 text-body break-words whitespace-pre-wrap">{item.description}</p>
    </section>
  );
}

function PinIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-6 shrink-0 text-primary"
    >
      <path d="M12 21s7-6.2 7-11a7 7 0 10-14 0c0 4.8 7 11 7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  );
}

/** 거래 장소(주소 텍스트. 지도는 REQ-030, 2순위) */
export function ItemTradePlace({ address }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-price">거래 장소</h2>
      <div className="flex items-center gap-3 rounded-xl bg-primary-soft px-4 py-5">
        <PinIcon />
        <span className="text-body break-words">{address}</span>
      </div>
    </section>
  );
}
