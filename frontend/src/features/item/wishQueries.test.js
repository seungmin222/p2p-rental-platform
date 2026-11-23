import { MutationObserver, QueryClient } from '@tanstack/query-core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { addWish, removeWish } from './api';
import { itemKeys, toggleWishMutationOptions } from './queries';

vi.mock('./api', () => ({ addWish: vi.fn(), removeWish: vi.fn() }));

const ITEM_ID = 101;
const FEED_KEY = itemKeys.feedList({ regionId: 1101, categoryId: null });

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('관심 토글 뮤테이션', () => {
  let queryClient;
  let observer;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient();
    queryClient.setQueryData(itemKeys.detail(ITEM_ID), {
      itemId: ITEM_ID,
      isWished: false,
      wishCount: 3,
    });
    queryClient.setQueryData(FEED_KEY, {
      pages: [{ items: [{ itemId: ITEM_ID, wishCount: 3 }], hasNext: false }],
      pageParams: [null],
    });
    queryClient.setQueryData(itemKeys.recent(), [{ itemId: ITEM_ID, wishCount: 3 }]);
    observer = new MutationObserver(queryClient, toggleWishMutationOptions(queryClient));
  });

  it('담기: 응답 전에 상세 하트를 바꾸고, 응답으로 상세·피드·최근 본의 관심 수를 맞춘다', async () => {
    const request = deferred();
    addWish.mockReturnValue(request.promise);

    const done = observer.mutate({ itemId: ITEM_ID, wished: true });
    await vi.waitFor(() =>
      expect(queryClient.getQueryData(itemKeys.detail(ITEM_ID)).isWished).toBe(true),
    );
    request.resolve({ itemId: ITEM_ID, isWished: true, wishCount: 4 });
    await done;

    expect(addWish).toHaveBeenCalledWith(ITEM_ID);
    expect(queryClient.getQueryData(itemKeys.detail(ITEM_ID))).toMatchObject({
      isWished: true,
      wishCount: 4,
    });
    expect(queryClient.getQueryData(FEED_KEY).pages[0].items[0].wishCount).toBe(4);
    expect(queryClient.getQueryData(itemKeys.recent())[0].wishCount).toBe(4);
  });

  it('해제: removeWish를 부른다', async () => {
    removeWish.mockResolvedValue({ itemId: ITEM_ID, isWished: false, wishCount: 2 });

    await observer.mutate({ itemId: ITEM_ID, wished: false });

    expect(removeWish).toHaveBeenCalledWith(ITEM_ID);
    expect(queryClient.getQueryData(itemKeys.detail(ITEM_ID)).wishCount).toBe(2);
  });

  it('실패하면 상세 하트를 되돌리고 피드는 그대로 둔다', async () => {
    addWish.mockRejectedValue({ code: 'CLIENT_NETWORK_ERROR' });
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

    await expect(observer.mutate({ itemId: ITEM_ID, wished: true })).rejects.toBeTruthy();

    expect(queryClient.getQueryData(itemKeys.detail(ITEM_ID)).isWished).toBe(false);
    expect(queryClient.getQueryData(FEED_KEY).pages[0].items[0].wishCount).toBe(3);
    expect(invalidate).not.toHaveBeenCalled();
  });

  it.each(['ITEM_NOT_FOUND', 'WISH_OWN_ITEM'])(
    '%s면 되돌린 뒤 상세를 다시 불러온다',
    async (code) => {
      addWish.mockRejectedValue({ code });
      const invalidate = vi.spyOn(queryClient, 'invalidateQueries');

      await expect(observer.mutate({ itemId: ITEM_ID, wished: true })).rejects.toBeTruthy();

      expect(queryClient.getQueryData(itemKeys.detail(ITEM_ID)).isWished).toBe(false);
      expect(invalidate).toHaveBeenCalledWith({
        queryKey: itemKeys.detail(ITEM_ID),
        exact: true,
      });
    },
  );
});
