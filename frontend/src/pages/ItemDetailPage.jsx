import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import BackButton from '../app/layout/BackButton';
import BottomActionBar from '../app/layout/BottomActionBar';
import PageHeader from '../app/layout/PageHeader';
import DeleteItemModal from '../features/item/components/DeleteItemModal';
import ItemImageCarousel from '../features/item/components/ItemImageCarousel';
import {
  ItemDescription,
  ItemHeading,
  ItemOwnerCard,
  ItemTradePlace,
} from '../features/item/components/ItemInfo';
import { getDeletedMessage, getErrorMessage } from '../features/item/errorMessages';
import { parseItemId } from '../features/item/itemId';
import { useItemDetail } from '../features/item/queries';
import { RENTAL_DAY_STATUS } from '../features/rental/constants';
import { useCalendarDays } from '../features/rental/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { monthOf, todayInSeoul } from '../shared/lib/date';
import { isInitialLoadError } from '../shared/lib/queryState';
import { Button, ButtonLink, Calendar, ErrorState, Skeleton, toast } from '../shared/ui';

function DetailSkeleton() {
  return (
    <div
      aria-busy="true"
      aria-label="불러오는 중"
      className="flex flex-col gap-4 xl:grid xl:grid-cols-[480px_1fr] xl:gap-12 xl:pt-8"
    >
      <Skeleton className="-mx-4 aspect-[390/236] rounded-none xl:mx-0 xl:aspect-square xl:rounded-xl" />
      <div className="flex flex-col gap-4">
        <Skeleton className="h-10 w-1/2" />
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-24 w-full" />
      </div>
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

function HeartIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinejoin="round"
      className="size-6"
    >
      <path d="M12 20s-7-4.4-7-10a4 4 0 017-2.5A4 4 0 0119 10c0 5.6-7 10-7 10z" />
    </svg>
  );
}

// 관심(REQ-022)·채팅(REQ-026)은 2순위라 아직 없다. 시안 자리는 두고 누르면 안내만 한다
function showComingSoon() {
  toast.show({ message: '준비 중인 기능이에요' });
}

/** 내 물품의 수정·삭제. 삭제하면 내 물품 관리로 옮긴다(삭제 중 이 화면을 떠났으면 옮기지 않는다) */
function OwnerActions({ item }) {
  const navigate = useNavigate();
  const [isDeleting, setIsDeleting] = useState(false);

  function handleDeleted(result) {
    navigate('/me/items', { replace: true, flushSync: true });
    toast.show({ message: getDeletedMessage(result) });
  }

  return (
    <div className="flex w-full gap-2">
      <Button variant="danger" className="flex-1" onClick={() => setIsDeleting(true)}>
        삭제
      </Button>
      <ButtonLink to={`/items/${item.itemId}/edit`} variant="outline" className="flex-1">
        수정
      </ButtonLink>
      {isDeleting && (
        <DeleteItemModal
          item={item}
          onClose={() => setIsDeleting(false)}
          onDeleted={handleDeleted}
        />
      )}
    </div>
  );
}

/** 하단 바 버튼 줄: 관심(하트) · 채팅하기(외곽선) · 예약하기(채움, 더 넓게). 내 물품이면 수정·삭제 */
function DetailActions({ item }) {
  if (item.isOwner) {
    return <OwnerActions item={item} />;
  }
  return (
    <div className="flex w-full items-center gap-2">
      <button
        type="button"
        aria-label="관심 등록(준비 중)"
        onClick={showComingSoon}
        className="mr-2 flex size-11 shrink-0 items-center justify-center rounded-full text-ink-muted hover:bg-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <HeartIcon />
      </button>
      <Button variant="outline" className="flex-[0.72] px-0" onClick={showComingSoon}>
        채팅하기
      </Button>
      <ButtonLink to={`/items/${item.itemId}/reserve`} className="flex-1 px-0">
        예약하기
      </ButtonLink>
    </div>
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

/**
 * SCR-05 물품 상세(REQ-011). 토큰을 보내 isOwner가 로그인 사용자 기준이다.
 * 모바일: 사진 → 제목·가격·보증금 → 판매자 → 설명 → 캘린더 → 거래 장소 + 하단 바.
 * PC(xl): 왼쪽 사진 · 오른쪽 제목·가격·보증금·판매자, 그 아래 설명 → 캘린더 | 거래 장소 + 하단 바(Figma 시안).
 */
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
      <div className="xl:grid xl:grid-cols-[480px_1fr] xl:items-start xl:gap-12 xl:pt-8">
        <ItemImageCarousel images={item.images} name={item.name}>
          <BackButton className="absolute top-2 left-2 bg-white/90 text-ink hover:bg-white xl:hidden" />
        </ItemImageCarousel>
        <div className="flex flex-col gap-6 pt-6 xl:pt-2">
          <ItemHeading item={item} />
          <ItemOwnerCard owner={item.owner} />
        </div>
      </div>
      <div className="flex flex-col gap-7 pt-6 pb-8 xl:mt-10 xl:border-t xl:border-line xl:pt-8">
        <ItemDescription description={item.description} />
        <div className="flex flex-col gap-7 border-t border-line pt-7 xl:grid xl:grid-cols-2 xl:items-start xl:gap-12 xl:border-t-0 xl:pt-0">
          <ItemCalendar itemId={item.itemId} />
          <div className="border-t border-line pt-7 xl:border-t-0 xl:pt-0">
            <ItemTradePlace
              address={item.address}
              latitude={item.latitude}
              longitude={item.longitude}
            />
          </div>
        </div>
      </div>
      {/* 하단 고정 바(모바일·PC 같음, 확정 캡처) */}
      <BottomActionBar>
        <DetailActions item={item} />
      </BottomActionBar>
    </>
  );
}
