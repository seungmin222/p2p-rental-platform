import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { flattenPages, getNextCursorParam, getNextPageParam } from '../../shared/lib/listParams';
import {
  createItem,
  deleteItem,
  fetchCategories,
  fetchDepositGuide,
  fetchFeed,
  fetchItem,
  fetchItemForEdit,
  fetchMyItems,
  searchItems,
  toItemCard,
  toItemDetail,
  toItemSummary,
  toMyItem,
  updateItem,
} from './api';
import { CATEGORY_STALE_TIME, ITEM_DETAIL_STALE_TIME } from './constants';
import { hasSearchCondition, resolveCategory, toApiParams } from './searchParams';

export const itemKeys = {
  // 동네 피드 전체. 필터 키 ['items', 'feed', { regionId, categoryId }]의 앞부분이라 대표 동네가 바뀌면 이 키로 무효화한다
  feed: () => ['items', 'feed'],
  feedList: ({ regionId, categoryId }) => ['items', 'feed', { regionId, categoryId }],
  // 검색 전체(등록·수정·삭제 뒤 무효화). 조건은 URL 쿼리와 같은 값을 그대로 키에 넣는다
  search: () => ['items', 'search'],
  searchList: (regionId, filters) => ['items', 'search', { regionId, ...filters }],
  detail: (itemId) => ['items', itemId],
  // 수정 폼. detail의 하위라 detail을 무효화할 때는 exact로 부른다(방금 넣은 수정 응답을 다시 받지 않게)
  edit: (itemId) => ['items', itemId, 'edit'],
  // rentalKeys.calendar와 같은 키(rental/queries가 이 파일을 가져오므로 반대로 가져오지 않는다)
  calendar: (itemId) => ['items', itemId, 'calendar'],
  depositGuide: ({ categoryId, itemPrice }) => [
    'items',
    'deposit-guide',
    { categoryId, itemPrice },
  ],
  mine: () => ['items', 'me'],
  mineTab: (tab) => ['items', 'me', { tab }],
  categories: () => ['categories'],
};

/** 활성 카테고리(sortOrder 순). 홈·검색의 칩이 쓴다 */
export function useCategories() {
  return useQuery({
    queryKey: itemKeys.categories(),
    queryFn: fetchCategories,
    staleTime: CATEGORY_STALE_TIME,
  });
}

/** URL의 카테고리 → 실제 조건(searchParams.js resolveCategory). 없는 카테고리면 전체 */
export function useCategoryFilter(categoryId) {
  const { data, isPending } = useCategories();
  return resolveCategory(categoryId, data, isPending);
}

// 모듈 함수라 참조가 바뀌지 않아, 데이터가 그대로면 이전 결과를 돌려준다
function selectFeed(data) {
  return flattenPages(data, toItemCard);
}

/**
 * REQ-009 동네 피드(커서). data는 지금까지 받은 페이지를 합친 카드 배열.
 * 카테고리·동네가 바뀌면 키가 달라져 처음 페이지부터 다시 받는다. 마지막 페이지(hasNext false)면 getNextCursorParam이 undefined라 더 부르지 않는다.
 */
export function useFeed({ regionId, categoryId, enabled = true }) {
  return useInfiniteQuery({
    queryKey: itemKeys.feedList({ regionId, categoryId }),
    queryFn: ({ pageParam }) => fetchFeed({ regionId, categoryId, cursor: pageParam ?? undefined }),
    initialPageParam: null,
    getNextPageParam: getNextCursorParam,
    select: selectFeed,
    enabled: enabled && Number.isInteger(regionId),
  });
}

function selectSearch(data) {
  return {
    items: flattenPages(data, toItemCard),
    totalElements: data.pages[0]?.totalElements ?? 0,
  };
}

/** REQ-010 검색(page/hasNext). 동네가 있고 조건이 하나라도 있을 때만 부른다. data는 { items, totalElements } */
export function useSearch(regionId, filters, { enabled = true } = {}) {
  return useInfiniteQuery({
    queryKey: itemKeys.searchList(regionId, filters),
    queryFn: ({ pageParam }) => searchItems({ ...toApiParams(filters, regionId), page: pageParam }),
    initialPageParam: 0,
    getNextPageParam,
    select: selectSearch,
    enabled: enabled && Number.isInteger(regionId) && hasSearchCondition(filters),
  });
}

/** REQ-011 물품 상세. 토큰이 있으면 isOwner가 로그인 사용자 기준이다 */
export function useItemDetail(itemId) {
  return useQuery({
    queryKey: itemKeys.detail(itemId),
    queryFn: () => fetchItem(itemId),
    select: toItemDetail,
    staleTime: ITEM_DETAIL_STALE_TIME,
    // 경로의 id가 숫자가 아니면 부르지 않는다(화면이 바로 "찾을 수 없어요")
    enabled: Number.isInteger(itemId),
  });
}

/** 물품 상세를 요약 모양으로(features/item/api.js toItemSummary) */
export function useItemSummary(itemId) {
  return useQuery({
    queryKey: itemKeys.detail(itemId),
    queryFn: () => fetchItem(itemId),
    select: toItemSummary,
    staleTime: ITEM_DETAIL_STALE_TIME,
    // 경로의 id가 숫자가 아니면 부르지 않는다(화면이 바로 "찾을 수 없어요")
    enabled: Number.isInteger(itemId),
  });
}

/** REQ-007 수정 폼을 채울 조회(소유자 전용). 남의 물품 403, 없음·삭제 404 */
export function useItemForEdit(itemId) {
  return useQuery({
    queryKey: itemKeys.edit(itemId),
    queryFn: () => fetchItemForEdit(itemId),
    enabled: Number.isInteger(itemId),
    // 수정 화면에 들어올 때마다 지금 값으로 채운다
    staleTime: 0,
    gcTime: 0,
  });
}

/** 권장 보증금(REQ-006). 카테고리와 유효한 물품 가격이 있을 때만 부른다. 안내일 뿐이라 실패해도 폼을 막지 않는다 */
export function useDepositGuide({ categoryId, itemPrice }) {
  return useQuery({
    queryKey: itemKeys.depositGuide({ categoryId, itemPrice }),
    queryFn: () => fetchDepositGuide({ categoryId, itemPrice }),
    enabled: Number.isInteger(categoryId) && Number.isInteger(itemPrice),
    staleTime: CATEGORY_STALE_TIME,
  });
}

function invalidateItemLists(queryClient) {
  queryClient.invalidateQueries({ queryKey: itemKeys.feed() });
  queryClient.invalidateQueries({ queryKey: itemKeys.search() });
  queryClient.invalidateQueries({ queryKey: itemKeys.mine() });
}

/**
 * REQ-006 등록. variables에 거래 장소 좌표가 들어가 끝나면 바로 지운다(gcTime 0).
 * 훅 밖에서 테스트하려고 옵션을 나눈다.
 */
export function createItemMutationOptions(queryClient) {
  return {
    mutationFn: createItem,
    gcTime: 0,
    onSuccess: () => invalidateItemLists(queryClient),
  };
}

export function useCreateItem() {
  const queryClient = useQueryClient();
  return useMutation(createItemMutationOptions(queryClient));
}

/**
 * REQ-007 수정. 응답이 ItemEditResponse라 상세 모양이 아니다: 수정 키만 setQueryData, 캘린더(기간)·목록은 무효화.
 * 상세(exact)는 reset한다: 무효화만 하면 이전 내용이 먼저 그려져 새 대표 사진이 앞에 낄 때 캐러셀 스냅이 이전 사진을 따라가고,
 * remove는 마운트된 관찰자(저장 중 뒤로 간 상세)를 갱신하지 않아 이전 내용에 멈춘다.
 * 화면을 벗어나도 빠지지 않도록 mutate() 콜백이 아니라 훅 옵션에 둔다. 대여 화면의 물품 정보를 위해 대여도 무효화한다.
 */
export function updateItemMutationOptions(queryClient, itemId) {
  return {
    mutationFn: (payload) => updateItem(itemId, payload),
    gcTime: 0,
    onSuccess: (data) => {
      queryClient.setQueryData(itemKeys.edit(itemId), data);
      queryClient.resetQueries({ queryKey: itemKeys.detail(itemId), exact: true });
      queryClient.invalidateQueries({ queryKey: itemKeys.calendar(itemId) });
      invalidateItemLists(queryClient);
      queryClient.invalidateQueries({ queryKey: ['rentals'] });
    },
  };
}

export function useUpdateItem(itemId) {
  const queryClient = useQueryClient();
  return useMutation(updateItemMutationOptions(queryClient, itemId));
}

/**
 * REQ-008 삭제. variables는 itemId, 결과는 { alreadyDeleted }.
 * 이미 지워진 물품(404)도 결과는 같으므로 성공으로 돌린다(문구만 호출부가 alreadyDeleted로 바꾼다).
 * 캐시 정리는 훅 옵션(화면을 벗어나도 실행), 이동은 mutate() 콜백(화면을 벗어났으면 부르지 않음)에 둔다.
 * 과거 대여의 "삭제된 물품" 표시를 위해 대여도 무효화한다.
 */
export function deleteItemMutationOptions(queryClient) {
  return {
    mutationFn: async (itemId) => {
      try {
        await deleteItem(itemId);
        return { alreadyDeleted: false };
      } catch (error) {
        if (error?.code === 'ITEM_NOT_FOUND') {
          return { alreadyDeleted: true };
        }
        throw error;
      }
    },
    onSuccess: (_, itemId) => {
      queryClient.removeQueries({ queryKey: itemKeys.detail(itemId) });
      invalidateItemLists(queryClient);
      queryClient.invalidateQueries({ queryKey: ['rentals'] });
    },
  };
}

export function useDeleteItem() {
  const queryClient = useQueryClient();
  return useMutation(deleteItemMutationOptions(queryClient));
}

// 모듈 함수라 참조가 바뀌지 않아, 데이터가 그대로면 이전 결과를 돌려준다
function selectMyItems(data) {
  return flattenPages(data, toMyItem);
}

/** REQ-020 내 물품(탭별, page/hasNext). data는 지금까지 받은 페이지를 합친 카드 배열 */
export function useMyItems(tab) {
  return useInfiniteQuery({
    queryKey: itemKeys.mineTab(tab),
    queryFn: ({ pageParam }) => fetchMyItems({ tab, page: pageParam }),
    initialPageParam: 0,
    getNextPageParam,
    select: selectMyItems,
  });
}
