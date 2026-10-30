import { useState } from 'react';
import { useParams } from 'react-router';

import BackButton from '../app/layout/BackButton';
import BottomActionBar from '../app/layout/BottomActionBar';
import PageHeader from '../app/layout/PageHeader';
import ItemImageCarousel from '../features/item/components/ItemImageCarousel';
import { ItemOwnerRow, ItemSummary, ItemTradePlace } from '../features/item/components/ItemInfo';
import { getErrorMessage } from '../features/item/errorMessages';
import { getPriceParts, getDepositText } from '../features/item/itemDisplay';
import { parseItemId } from '../features/item/itemId';
import { useItemDetail } from '../features/item/queries';
import { RENTAL_DAY_STATUS } from '../features/rental/constants';
import { useCalendarDays } from '../features/rental/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { monthOf, todayInSeoul } from '../shared/lib/date';
import { isInitialLoadError } from '../shared/lib/queryState';
import { ButtonLink, Calendar, ErrorState, Skeleton } from '../shared/ui';

function DetailSkeleton() {
  return (
    <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-4">
      <Skeleton className="-mx-4 aspect-[390/236] rounded-none" />
      <Skeleton className="h-10 w-1/2" />
      <Skeleton className="h-8 w-2/3" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

/** 읽기 전용 대여 캘린더(이번 달부터, 예약된 날은 취소선). 월을 넘길 때마다 그 달만 부른다 */
function ItemCalendar({ itemId }) {
  const thisMonth = monthOf(todayInSeoul());
  const [month, setMonth] = useState(thisMonth);
  const calendar = useCalendarDays(itemId, [month]);
  const result = calendar.results[0];
  useRefetchErrorToast([result]);

  return (
    <section>
      {isInitialLoadError(result) ? (
        <ErrorState
          title="캘린더를 불러오지 못했어요"
          message={getErrorMessage(result.error)}
          onRetry={result.refetch}
        />
      ) : (
        <Calendar
          readOnly
          title={<h2 className="text-price">대여 캘린더</h2>}
          month={month}
          days={calendar.days}
          value={null}
          onChange={() => {}}
          onMonthChange={setMonth}
          statusLabels={RENTAL_DAY_STATUS}
          minMonth={thisMonth}
          isLoading={result?.isPending ?? true}
        />
      )}
    </section>
  );
}

/** 하단 바: 일 대여료·보증금 + 예약하기. 내 물품이면 예약 대신 "내 물품 관리"(수정·삭제는 feat/fe-item-form에서) */
function DetailBar({ item }) {
  const price = getPriceParts(item.pricePerDay);
  const depositText = getDepositText(item.deposit) ?? '보증금 없음';
  return (
    <BottomActionBar className="flex items-center justify-between gap-3">
      <div className="flex min-w-0 flex-col">
        <span className="text-price tabular-nums">
          {price.amount}
          {price.unit && (
            <span className="text-body font-normal text-ink-muted"> {price.unit}</span>
          )}
        </span>
        <span className="text-caption font-semibold text-accent-strong tabular-nums">
          {depositText}
        </span>
      </div>
      {item.isOwner ? (
        <ButtonLink to="/me/items" variant="outline" className="shrink-0">
          내 물품 관리
        </ButtonLink>
      ) : (
        <ButtonLink to={`/items/${item.itemId}/reserve`} className="shrink-0">
          예약하기
        </ButtonLink>
      )}
    </BottomActionBar>
  );
}

function NotFound() {
  return (
    <>
      <PageHeader title="물품 상세" />
      <ErrorState title="물품을 찾을 수 없어요" message="삭제되었거나 숨겨진 물품이에요." />
      <div className="flex justify-center">
        <ButtonLink to="/" variant="secondary">
          홈으로
        </ButtonLink>
      </div>
    </>
  );
}

/** SCR-05 물품 상세(REQ-011). 토큰을 보내 isOwner가 로그인 사용자 기준이다 */
export default function ItemDetailPage() {
  const { id } = useParams();
  // 올바른 id가 아니면 요청하지 않고 바로 "찾을 수 없어요"
  const itemId = parseItemId(id);
  const query = useItemDetail(itemId);
  useRefetchErrorToast([query]);

  if (itemId == null || (isInitialLoadError(query) && query.error.code === 'ITEM_NOT_FOUND')) {
    return <NotFound />;
  }
  if (isInitialLoadError(query)) {
    return (
      <>
        <PageHeader title="물품 상세" />
        <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />
      </>
    );
  }
  const item = query.data;
  if (!item) {
    return <DetailSkeleton />;
  }
  return (
    <>
      <ItemImageCarousel images={item.images} name={item.name}>
        <BackButton className="absolute top-2 left-2 bg-white/90 text-ink hover:bg-white" />
      </ItemImageCarousel>
      <ItemOwnerRow owner={item.owner} />
      <div className="flex flex-col gap-6 pb-8">
        <ItemSummary item={item} />
        <ItemCalendar itemId={item.itemId} />
        <ItemTradePlace address={item.address} />
      </div>
      <DetailBar item={item} />
    </>
  );
}
