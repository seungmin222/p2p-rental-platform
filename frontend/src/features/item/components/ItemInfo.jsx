import { useKakaoMap } from '../../../shared/hooks/useKakaoMap';
import { formatElapsed } from '../../../shared/lib/format';
import { Skeleton } from '../../../shared/ui';
import { ITEM_CONDITION } from '../constants';
import { getDepositText, getPriceParts } from '../itemDisplay';
import KakaoPlaceMap from './KakaoPlaceMap';

function MemberAvatar({ url }) {
  if (url) {
    return (
      <img src={url} alt="" className="size-11 shrink-0 rounded-full bg-placeholder object-cover" />
    );
  }
  return <div aria-hidden="true" className="size-11 shrink-0 rounded-full bg-primary-soft" />;
}

/** 판매자 카드(테두리 상자, 닉네임 + 동네). 거래 횟수·신뢰 점수는 1순위 응답에 없어 그리지 않는다(신뢰 점수는 REQ-054, 3순위) */
export function ItemOwnerCard({ owner }) {
  return (
    <section
      aria-label="제공자"
      className="flex items-center gap-3 rounded-xl border border-line p-4"
    >
      <MemberAvatar url={owner.profileImageUrl} />
      <div className="flex min-w-0 items-baseline gap-2">
        <span className="truncate font-semibold">{owner.nickname}</span>
        {owner.regionName && (
          <span className="shrink-0 text-xs text-ink-muted">{owner.regionName}</span>
        )}
      </div>
    </section>
  );
}

/** 제목·"카테고리 · 상태 · 경과 시간"·일 대여료·보증금 */
export function ItemHeading({ item }) {
  const meta = [
    item.categoryName,
    ITEM_CONDITION[item.condition]?.label,
    formatElapsed(item.bumpedAt),
  ]
    .filter(Boolean)
    .join(' · ');
  const price = getPriceParts(item.pricePerDay);
  return (
    <section className="flex flex-col">
      <h1 className="text-title break-words">{item.name}</h1>
      <p className="mt-1 text-caption text-ink-muted">{meta}</p>
      <p className="mt-4 text-title tabular-nums">
        {price.amount}
        {price.unit && (
          <span className="ml-1 text-caption font-medium text-ink-muted">{price.unit}</span>
        )}
      </p>
      <p className="mt-1.5 text-caption font-semibold text-accent-strong tabular-nums">
        {getDepositText(item.deposit) ?? '보증금 없음'}
      </p>
    </section>
  );
}

/** 물품 설명(줄바꿈 유지) */
export function ItemDescription({ description }) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-price">물품 설명</h2>
      <p className="text-body break-words whitespace-pre-wrap text-ink-muted">{description}</p>
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

function hasCoords(latitude, longitude) {
  return Number.isFinite(latitude) && Number.isFinite(longitude);
}

/** 거래 장소(REQ-030): 지도를 불러오면 저장된 위치에 마커 + 주소, 못 불러오거나 좌표가 없으면 주소 텍스트만 */
export function ItemTradePlace({ address, latitude, longitude }) {
  const { status, kakao } = useKakaoMap();
  const showMap = status !== 'error' && hasCoords(latitude, longitude);
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-price">거래 장소</h2>
      {showMap &&
        (kakao ? (
          <KakaoPlaceMap
            kakao={kakao}
            point={{ lat: latitude, lng: longitude }}
            label={`거래 장소 지도: ${address}`}
          />
        ) : (
          <Skeleton className="h-48 rounded-xl" />
        ))}
      <div className="flex items-center gap-3 rounded-xl border border-line px-4 py-4">
        <PinIcon />
        <span className="text-body font-medium break-words">{address}</span>
      </div>
    </section>
  );
}
