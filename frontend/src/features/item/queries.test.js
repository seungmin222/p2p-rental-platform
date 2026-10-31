import { MutationObserver, QueryClient, QueryObserver } from '@tanstack/query-core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createItem, deleteItem, updateItem } from './api';
import {
  createItemMutationOptions,
  deleteItemMutationOptions,
  itemKeys,
  updateItemMutationOptions,
} from './queries';

vi.mock('./api', () => ({ createItem: vi.fn(), updateItem: vi.fn(), deleteItem: vi.fn() }));

const LATITUDE = 37.391234;

function invalidatedKeys(spy) {
  return spy.mock.calls.map(([filters]) => filters);
}

describe('물품 쓰기 뮤테이션', () => {
  let queryClient;
  let invalidate;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient();
    invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  });

  it('등록: 피드·검색·내 물품을 무효화한다', async () => {
    createItem.mockResolvedValue({ itemId: 1 });
    const observer = new MutationObserver(queryClient, createItemMutationOptions(queryClient));

    await observer.mutate({ request: {}, images: [] });

    expect(invalidatedKeys(invalidate)).toEqual([
      { queryKey: itemKeys.feed() },
      { queryKey: itemKeys.search() },
      { queryKey: itemKeys.mine() },
    ]);
  });

  it('등록·수정: 끝나면 variables의 좌표가 뮤테이션 캐시에 남지 않는다', async () => {
    vi.useFakeTimers();
    createItem.mockResolvedValue({ itemId: 1 });
    updateItem.mockResolvedValue({ itemId: 1 });
    const payload = { request: { latitude: LATITUDE }, images: [] };
    const options = [
      createItemMutationOptions(queryClient),
      updateItemMutationOptions(queryClient, 1),
    ];
    for (const option of options) {
      const observer = new MutationObserver(queryClient, option);
      await observer.mutate(payload);
      observer.reset();
    }
    await vi.runAllTimersAsync();
    vi.useRealTimers();

    expect(
      JSON.stringify(
        queryClient
          .getMutationCache()
          .getAll()
          .map((m) => m.state),
      ),
    ).not.toContain(String(LATITUDE));
  });

  it('수정: 상세가 마운트돼 있으면 이전 값을 다시 그리지 않고 새로 받는다', async () => {
    updateItem.mockResolvedValue({ itemId: 7 });
    const fetchDetail = vi.fn().mockResolvedValueOnce('이전 이름').mockResolvedValue('새 이름');
    const detail = new QueryObserver(queryClient, {
      queryKey: itemKeys.detail(7),
      queryFn: fetchDetail,
      staleTime: 10_000,
    });
    const seen = [];
    const unsubscribe = detail.subscribe((result) => seen.push(result.data));
    await vi.waitFor(() => expect(detail.getCurrentResult().data).toBe('이전 이름'));
    seen.length = 0;
    const observer = new MutationObserver(queryClient, updateItemMutationOptions(queryClient, 7));

    await observer.mutate({ request: {}, images: [] });

    await vi.waitFor(() => expect(detail.getCurrentResult().data).toBe('새 이름'));
    expect(fetchDetail).toHaveBeenCalledTimes(2);
    expect(seen).not.toContain('이전 이름');
    unsubscribe();
  });

  it('수정: 수정 키에만 응답을 넣고 상세만 비운 뒤 캘린더·목록을 무효화한다', async () => {
    const response = { itemId: 7, name: '새 이름' };
    updateItem.mockResolvedValue(response);
    queryClient.setQueryData(itemKeys.detail(7), { itemId: 7, name: '이전 이름' });
    queryClient.setQueryData(itemKeys.calendar(7), { days: [] });
    const observer = new MutationObserver(queryClient, updateItemMutationOptions(queryClient, 7));

    await observer.mutate({ request: {}, images: [] });

    expect(queryClient.getQueryData(itemKeys.edit(7))).toEqual(response);
    expect(queryClient.getQueryData(itemKeys.detail(7))).toBeUndefined();
    expect(queryClient.getQueryData(itemKeys.calendar(7))).toEqual({ days: [] });
    expect(invalidatedKeys(invalidate)).toEqual([
      { queryKey: itemKeys.calendar(7) },
      { queryKey: itemKeys.feed() },
      { queryKey: itemKeys.search() },
      { queryKey: itemKeys.mine() },
    ]);
  });

  it('삭제: 이동을 먼저 부른 뒤 물품 캐시를 지우고 목록·대여를 무효화한다', async () => {
    deleteItem.mockResolvedValue(null);
    queryClient.setQueryData(itemKeys.detail(7), { itemId: 7 });
    queryClient.setQueryData(itemKeys.calendar(7), { days: [] });
    let cachedWhenDeleted;
    const onDeleted = vi.fn(() => {
      cachedWhenDeleted = queryClient.getQueryData(itemKeys.detail(7));
    });
    const observer = new MutationObserver(
      queryClient,
      deleteItemMutationOptions(queryClient, { onDeleted }),
    );

    await observer.mutate(7);

    expect(onDeleted).toHaveBeenCalledWith(7);
    expect(cachedWhenDeleted).toEqual({ itemId: 7 });
    expect(queryClient.getQueryData(itemKeys.detail(7))).toBeUndefined();
    expect(queryClient.getQueryData(itemKeys.calendar(7))).toBeUndefined();
    expect(invalidatedKeys(invalidate)).toEqual([
      { queryKey: itemKeys.feed() },
      { queryKey: itemKeys.search() },
      { queryKey: itemKeys.mine() },
      { queryKey: ['rentals'] },
    ]);
  });

  it('삭제 실패: 진행 중인 대여면 캐시를 지우지 않는다', async () => {
    deleteItem.mockRejectedValue({ code: 'ITEM_HAS_ACTIVE_RENTAL' });
    queryClient.setQueryData(itemKeys.detail(7), { itemId: 7 });
    const onDeleted = vi.fn();
    const observer = new MutationObserver(
      queryClient,
      deleteItemMutationOptions(queryClient, { onDeleted }),
    );

    await expect(observer.mutate(7)).rejects.toMatchObject({ code: 'ITEM_HAS_ACTIVE_RENTAL' });

    expect(onDeleted).not.toHaveBeenCalled();
    expect(queryClient.getQueryData(itemKeys.detail(7))).toEqual({ itemId: 7 });
  });
});
