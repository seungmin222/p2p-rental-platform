import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import CategoryChips from '../features/item/components/CategoryChips';
import ItemListView from '../features/item/components/ItemListView';
import RegionGate from '../features/item/components/RegionGate';
import SearchFilterSheet from '../features/item/components/SearchFilterSheet';
import SearchForm from '../features/item/components/SearchForm';
import { useSearch } from '../features/item/queries';
import {
  countSheetFilters,
  hasSearchCondition,
  parseSearchParams,
  toSearchParams,
} from '../features/item/searchParams';
import { useFeedRegion } from '../features/item/useFeedRegion';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { EmptyState } from '../shared/ui';

function SearchResults({ regionId, filters }) {
  const search = useSearch(regionId, filters);
  useRefetchErrorToast([search]);

  if (!hasSearchCondition(filters)) {
    return (
      <EmptyState
        title="검색어를 입력하거나 필터를 골라 보세요"
        description="내 동네에서 빌릴 수 있는 물품을 찾아 드려요"
      />
    );
  }
  return (
    <>
      {search.data && (
        <p className="pt-2 text-caption text-ink-muted tabular-nums">
          검색 결과 {search.data.totalElements}개
        </p>
      )}
      <ItemListView
        query={search}
        items={search.data?.items}
        emptyTitle={
          filters.keyword ? `‘${filters.keyword}’에 맞는 물품이 없어요` : '맞는 물품이 없어요'
        }
        emptyDescription="검색어나 필터를 바꿔 보세요"
      />
    </>
  );
}

/**
 * SCR-04 검색(REQ-010). 키워드·카테고리·필터는 전부 URL 쿼리에 두어 뒤로 가기·새로고침에 유지하고, 같은 값이 queryKey가 된다.
 * 키워드는 제출(엔터·검색 버튼)할 때만 반영하고, 조회할 조건이 없으면 조회하지 않는다(searchParams.hasSearchCondition).
 */
export default function SearchPage() {
  const region = useFeedRegion();
  const [searchParams, setSearchParams] = useSearchParams();
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const filters = useMemo(() => parseSearchParams(searchParams), [searchParams]);

  // 조건을 바꿀 때는 기록을 쌓지 않는다(뒤로 가기가 조건을 거슬러 가지 않게)
  function updateFilters(patch) {
    setSearchParams(toSearchParams({ ...filters, ...patch }), { replace: true });
  }

  return (
    <>
      <PageHeader title="검색" />
      {region.status !== 'ready' ? (
        <RegionGate region={region} />
      ) : (
        <>
          <SearchForm
            key={filters.keyword}
            keyword={filters.keyword}
            filterCount={countSheetFilters(filters)}
            onSubmit={(keyword) => updateFilters({ keyword })}
            onOpenFilter={() => setIsFilterOpen(true)}
          />
          <CategoryChips
            categoryId={filters.categoryId}
            onChange={(categoryId) => updateFilters({ categoryId })}
          />
          <SearchResults regionId={region.regionId} filters={filters} />
          <SearchFilterSheet
            open={isFilterOpen}
            onClose={() => setIsFilterOpen(false)}
            filters={filters}
            onApply={updateFilters}
          />
        </>
      )}
    </>
  );
}
