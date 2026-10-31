import { useState } from 'react';
import { Link } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import DeleteItemModal from '../features/item/components/DeleteItemModal';
import { MY_ITEM_TABS } from '../features/item/constants';
import { useMyItems } from '../features/item/queries';
import ItemRentalsSheet from '../features/rental/components/ItemRentalsSheet';
import { getErrorMessage } from '../features/rental/errorMessages';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { useTabParam } from '../shared/hooks/useTabParam';
import { formatDailyPrice } from '../shared/lib/format';
import { isInitialLoadError } from '../shared/lib/queryState';
import {
  Button,
  ButtonLink,
  EmptyState,
  ErrorState,
  InfiniteListFooter,
  Skeleton,
  Tabs,
  Thumbnail,
  toast,
} from '../shared/ui';

const DEFAULT_TAB = 'AVAILABLE';
const TAB_ITEMS = Object.entries(MY_ITEM_TABS).map(([value, { label }]) => ({ value, label }));
const EMPTY_TITLES = {
  AVAILABLE: '대여 가능한 물품이 없어요',
  RENTING: '대여 중인 물품이 없어요',
};

/** 카드 메뉴는 1순위에 수정·삭제(REQ-008)까지(숨기기·끌어올리기는 3순위) */
function MyItemCard({ item, onOpenRentals, onDelete }) {
  return (
    <div className="flex flex-col gap-2.5 py-3.5">
      <Link
        to={`/items/${item.itemId}`}
        className="flex gap-3.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <Thumbnail url={item.thumbnailUrl} className="size-16" />
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="truncate font-semibold">{item.name}</span>
          <span className="text-price">{formatDailyPrice(item.pricePerDay)}</span>
          <span className="text-caption text-ink-muted tabular-nums">
            조회 {item.viewCount} · 관심 {item.wishCount} · 채팅 {item.chatCount}
          </span>
          {item.upcomingRentalCount > 0 && (
            <span className="text-caption font-semibold text-info">
              다가오는 예약 {item.upcomingRentalCount}건
            </span>
          )}
        </div>
      </Link>
      <div className="flex justify-end gap-2">
        <Button
          variant="secondary"
          size="sm"
          aria-label={`${item.name} 예약 현황`}
          onClick={() => onOpenRentals(item)}
        >
          예약 현황
        </Button>
        <ButtonLink
          to={`/items/${item.itemId}/edit`}
          variant="secondary"
          size="sm"
          aria-label={`${item.name} 수정`}
        >
          수정
        </ButtonLink>
        <Button
          variant="danger"
          size="sm"
          aria-label={`${item.name} 삭제`}
          onClick={() => onDelete(item)}
        >
          삭제
        </Button>
      </div>
    </div>
  );
}

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중">
      {[0, 1, 2].map((key) => (
        <li key={key} className="flex flex-col gap-2.5 border-b border-divider py-3.5">
          <div className="flex gap-3.5">
            <Skeleton className="size-16 rounded-[10px]" />
            <div className="flex flex-1 flex-col gap-2 py-1">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-3 w-1/2" />
            </div>
          </div>
          <Skeleton className="ml-auto h-11 w-44 rounded-xl" />
        </li>
      ))}
    </ul>
  );
}

function MyItemList({ tab, onOpenRentals, onDelete }) {
  const query = useMyItems(tab);
  useRefetchErrorToast([query]);

  if (isInitialLoadError(query)) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  }
  if (!query.data) {
    return <ListSkeleton />;
  }
  if (query.data.length === 0) {
    return (
      <EmptyState
        title={EMPTY_TITLES[tab]}
        description={tab === 'AVAILABLE' ? '안 쓰는 물건을 이웃에게 빌려줘 보세요' : undefined}
        action={
          tab === 'AVAILABLE' && (
            <ButtonLink to="/items/new" variant="secondary">
              물품 등록하기
            </ButtonLink>
          )
        }
      />
    );
  }
  return (
    <>
      <ul>
        {query.data.map((item) => (
          <li key={item.itemId} className="border-b border-divider">
            <MyItemCard item={item} onOpenRentals={onOpenRentals} onDelete={onDelete} />
          </li>
        ))}
      </ul>
      <InfiniteListFooter query={query} />
    </>
  );
}

/** SCR-12 내 물품 관리(REQ-020). 탭은 URL ?tab= */
export default function MyItemsPage() {
  const [tab, setTab] = useTabParam(MY_ITEM_TABS, DEFAULT_TAB);
  const [sheetItem, setSheetItem] = useState(null);
  const [deletingItem, setDeletingItem] = useState(null);

  function handleDeleted() {
    setDeletingItem(null);
    toast.show({ message: '물품을 삭제했어요' });
  }

  return (
    <>
      <PageHeader title="내 물품 관리" />
      <Tabs
        label="내 물품 탭"
        items={TAB_ITEMS}
        value={tab}
        onChange={setTab}
        listClassName="sticky top-14 z-10 -mx-4 bg-white px-4"
      >
        <MyItemList key={tab} tab={tab} onOpenRentals={setSheetItem} onDelete={setDeletingItem} />
      </Tabs>
      {/* 열린 동안만 렌더해 그때 조회한다 */}
      {sheetItem && <ItemRentalsSheet item={sheetItem} onClose={() => setSheetItem(null)} />}
      {deletingItem && (
        <DeleteItemModal
          item={deletingItem}
          onClose={() => setDeletingItem(null)}
          onDeleted={handleDeleted}
        />
      )}
    </>
  );
}
