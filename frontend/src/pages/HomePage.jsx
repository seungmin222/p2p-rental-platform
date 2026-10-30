import { Link, useSearchParams } from 'react-router';

import FloatingActionButton from '../app/layout/FloatingActionButton';
import CategoryChips from '../features/item/components/CategoryChips';
import ItemListView from '../features/item/components/ItemListView';
import RegionGate from '../features/item/components/RegionGate';
import { useCategoryFilter, useFeed } from '../features/item/queries';
import { readCategoryParam, SEARCH_PARAM } from '../features/item/searchParams';
import { useFeedRegion } from '../features/item/useFeedRegion';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { Skeleton } from '../shared/ui';

function SearchIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      className="size-6"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-4-4" />
    </svg>
  );
}

function ChevronDownIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-5"
    >
      <path d="M6 9l6 6 6-6" />
    </svg>
  );
}

/** 내 동네 이름(시안 "연수동 ▾") → /regions, 우측 검색 아이콘 → /search. 알림 아이콘은 알림 화면(2순위)이 없어 두지 않는다 */
function HomeHeader({ region }) {
  const { regionName } = region;
  return (
    <header className="sticky top-0 z-10 -mx-4 flex h-14 items-center justify-between border-b border-divider bg-white px-4">
      <Link
        to="/regions"
        aria-label={regionName ? `동네 설정, 현재 ${regionName}` : '동네 설정'}
        className="-ml-1 flex min-h-11 items-center gap-0.5 rounded-lg px-1 text-title focus-visible:outline-2 focus-visible:outline-primary"
      >
        {region.status === 'loading' ? (
          // 내 정보를 받는 동안 "동네 설정"이 잠깐 보이지 않게
          <Skeleton className="h-6 w-20" />
        ) : (
          <span className="truncate">{regionName ?? '동네 설정'}</span>
        )}
        <ChevronDownIcon />
      </Link>
      <Link
        to="/search"
        aria-label="검색"
        className="-mr-2 flex size-11 items-center justify-center rounded-full text-ink hover:bg-surface focus-visible:outline-2 focus-visible:outline-primary"
      >
        <SearchIcon />
      </Link>
    </header>
  );
}

function Feed({ regionId }) {
  const [searchParams, setSearchParams] = useSearchParams();
  // 선택은 URL ?category=에 둔다(상세에 갔다가 뒤로 와도 그대로). 바꿀 때는 기록을 쌓지 않는다
  const { categoryId, isResolving } = useCategoryFilter(readCategoryParam(searchParams));
  const feed = useFeed({ regionId, categoryId, enabled: !isResolving });
  useRefetchErrorToast([feed]);

  function handleCategoryChange(nextCategoryId) {
    setSearchParams(
      (params) => {
        const next = new URLSearchParams(params);
        if (nextCategoryId == null) {
          next.delete(SEARCH_PARAM.CATEGORY);
        } else {
          next.set(SEARCH_PARAM.CATEGORY, String(nextCategoryId));
        }
        return next;
      },
      { replace: true },
    );
  }

  return (
    <>
      <div className="sticky top-14 z-10 -mx-4 bg-white px-4">
        <CategoryChips categoryId={categoryId} onChange={handleCategoryChange} />
      </div>
      <ItemListView
        // 카테고리가 바뀌면 목록 상태(스크롤 관찰자 등)를 새로 시작한다
        key={categoryId ?? 'all'}
        query={feed}
        items={feed.data}
        emptyTitle={
          categoryId == null
            ? '아직 이 동네에 등록된 물품이 없어요'
            : '이 카테고리에는 물품이 없어요'
        }
        emptyDescription="다른 카테고리를 골라 보거나 검색해 보세요"
      />
    </>
  );
}

/** SCR-03 홈(동네 피드, REQ-009). 동네 기준은 features/item/homeRegion.js */
export default function HomePage() {
  const region = useFeedRegion();
  return (
    <>
      <HomeHeader region={region} />
      {region.status === 'ready' ? (
        <Feed regionId={region.regionId} />
      ) : (
        <RegionGate region={region} />
      )}
      {/* 등록 화면은 로그인이 필요해 RequireAuth가 비회원을 로그인으로 보낸다(로그인 뒤 돌아온다) */}
      <FloatingActionButton to="/items/new" label="물품 등록" />
    </>
  );
}
