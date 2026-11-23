import {
  MutationObserver,
  QueryClient,
  QueryClientProvider,
  useQuery,
} from '@tanstack/react-query';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { addWish } from '../api';
import { itemKeys, toggleWishMutationOptions } from '../queries';
import DetailWishButton from './DetailWishButton';

vi.mock('../api', async (importOriginal) => ({ ...(await importOriginal()), addWish: vi.fn() }));

const ITEM_ID = 101;

/** 상세 화면처럼 상세 캐시의 물품으로 하트를 그린다 */
function render(queryClient) {
  function Harness() {
    const { data } = useQuery({ queryKey: itemKeys.detail(ITEM_ID), enabled: false });
    return createElement(DetailWishButton, { item: data });
  }
  return renderToStaticMarkup(
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(MemoryRouter, null, createElement(Harness)),
    ),
  );
}

function pressed(html) {
  return html.match(/aria-pressed="(true|false)"/)[1];
}

describe('상세 하트', () => {
  it('누르면 바로 채워지고, 실패하면 빈 하트로 되돌린다', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(itemKeys.detail(ITEM_ID), {
      itemId: ITEM_ID,
      isWished: false,
      wishCount: 3,
    });
    let reject;
    addWish.mockReturnValue(new Promise((_, rej) => (reject = rej)));
    const observer = new MutationObserver(queryClient, toggleWishMutationOptions(queryClient));
    expect(pressed(render(queryClient))).toBe('false');

    const done = observer.mutate({ itemId: ITEM_ID, wished: true });
    await vi.waitFor(() => expect(pressed(render(queryClient))).toBe('true'));
    reject({ code: 'CLIENT_NETWORK_ERROR' });
    await expect(done).rejects.toBeTruthy();

    expect(pressed(render(queryClient))).toBe('false');
  });
});
