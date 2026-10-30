import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { flattenPages, getNextCursorParam, getNextPageParam } from '../../shared/lib/listParams';
import {
  fetchCategories,
  fetchFeed,
  fetchItem,
  fetchMyItems,
  searchItems,
  toItemCard,
  toItemDetail,
  toItemSummary,
  toMyItem,
} from './api';
import { CATEGORY_STALE_TIME } from './constants';
import { hasSearchCondition, toApiParams } from './searchParams';

export const itemKeys = {
  // 동네 피드 전체. 필터 키 ['items', 'feed', { regionId, categoryId }]의 앞부분이라 대표 동네가 바뀌면 이 키로 무효화한다
  feed: () => ['items', 'feed'],
  feedList: ({ regionId, categoryId }) => ['items', 'feed', { regionId, categoryId }],
  // 검색 전체(등록·수정·삭제 뒤 무효화). 조건은 URL 쿼리와 같은 값을 그대로 키에 넣는다
  search: () => ['items', 'search'],
  searchList: (regionId, filters) => ['items', 'search', { regionId, ...filters }],
  detail: (itemId) => ['items', itemId],
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

// 모듈 함수라 참조가 바뀌지 않아, 데이터가 그대로면 이전 결과를 돌려준다
function selectFeed(data) {
  return flattenPages(data, toItemCard);
}

/**
 * REQ-009 동네 피드(커서). data는 지금까지 받은 페이지를 합친 카드 배열.
 * 카테고리·동네가 바뀌면 키가 달라져 처음 페이지부터 다시 받는다. 마지막 페이지(hasNext false)면 getNextCursorParam이 undefined라 더 부르지 않는다.
 */
export function useFeed({ regionId, categoryId }) {
  return useInfiniteQuery({
    queryKey: itemKeys.feedList({ regionId, categoryId }),
    queryFn: ({ pageParam }) => fetchFeed({ regionId, categoryId, cursor: pageParam ?? undefined }),
    initialPageParam: null,
    getNextPageParam: getNextCursorParam,
    select: selectFeed,
    enabled: Number.isInteger(regionId),
  });
}

function selectSearch(data) {
  return {
    items: flattenPages(data, toItemCard),
    totalElements: data.pages[0]?.totalElements ?? 0,
  };
}

/** REQ-010 검색(page/hasNext). 동네가 있고 조건이 하나라도 있을 때만 부른다. data는 { items, totalElements } */
export function useSearch(regionId, filters) {
  return useInfiniteQuery({
    queryKey: itemKeys.searchList(regionId, filters),
    queryFn: ({ pageParam }) => searchItems({ ...toApiParams(filters, regionId), page: pageParam }),
    initialPageParam: 0,
    getNextPageParam,
    select: selectSearch,
    enabled: Number.isInteger(regionId) && hasSearchCondition(filters),
  });
}

/** REQ-011 물품 상세. 토큰이 있으면 isOwner가 로그인 사용자 기준이다 */
export function useItemDetail(itemId) {
  return useQuery({
    queryKey: itemKeys.detail(itemId),
    queryFn: () => fetchItem(itemId),
    select: toItemDetail,
    staleTime: 10_000,
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
    staleTime: 10_000,
    // 경로의 id가 숫자가 아니면 부르지 않는다(화면이 바로 "찾을 수 없어요")
    enabled: Number.isInteger(itemId),
  });
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
