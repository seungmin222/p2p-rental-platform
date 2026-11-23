import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';

import { toWishItem } from '../api';
import WishItemRow from './WishItemRow';

function render(raw, isWished = true) {
  return renderToStaticMarkup(
    createElement(
      QueryClientProvider,
      { client: new QueryClient() },
      createElement(
        MemoryRouter,
        null,
        createElement(WishItemRow, { item: toWishItem(raw), isWished, onChange: () => {} }),
      ),
    ),
  );
}

const AVAILABLE = {
  itemId: 101,
  status: 'AVAILABLE',
  name: '캠핑 의자',
  thumbnailUrl: null,
  regionName: '역삼1동',
  pricePerDay: 5000,
  deposit: 20000,
  isRenting: false,
  wishCount: 3,
  chatCount: 1,
};

describe('관심 목록 한 줄', () => {
  it('볼 수 있는 물품은 상세로 가는 카드와 담긴 하트를 그린다', () => {
    const html = render(AVAILABLE);

    expect(html).toContain('href="/items/101"');
    expect(html).toContain('캠핑 의자');
    expect(html).toMatch(/aria-label="캠핑 의자 관심"[^>]*aria-pressed="true"/);
  });

  it('이 화면에서 해제한 물품은 빈 하트로 남는다', () => {
    expect(render(AVAILABLE, false)).toMatch(
      /aria-label="캠핑 의자 관심"[^>]*aria-pressed="false"/,
    );
  });

  it.each([
    ['DELETED', '삭제된 물품'],
    ['HIDDEN', '볼 수 없는 물품'],
  ])('%s 물품은 링크·하트 없이 안내와 삭제 버튼만 그린다', (status, label) => {
    const html = render({ itemId: 210, status, wishedAt: '2026-11-20T10:00:00+09:00' });

    expect(html).toContain(label);
    expect(html).toContain(`aria-label="${label} 관심 해제"`);
    expect(html).not.toContain('<a ');
    expect(html).not.toContain('aria-pressed');
  });
});
