import { describe, expect, it, vi } from 'vitest';

import { handleDetailWishClick } from './wishClick';

const ITEM = { itemId: 101, isWished: false };

function click(overrides) {
  const navigate = vi.fn();
  const mutate = vi.fn();
  handleDetailWishClick({
    isLoggedIn: true,
    isPending: false,
    from: '/items/101',
    item: ITEM,
    navigate,
    mutate,
    ...overrides,
  });
  return { navigate, mutate };
}

describe('상세 하트 누름', () => {
  it('비회원이면 요청하지 않고 지금 상세로 돌아오는 로그인 화면으로 보낸다', () => {
    const { navigate, mutate } = click({ isLoggedIn: false, from: '/items/101?tab=1' });

    expect(navigate).toHaveBeenCalledWith('/login?redirect=%2Fitems%2F101%3Ftab%3D1');
    expect(mutate).not.toHaveBeenCalled();
  });

  it('요청 중에 다시 누르면 무시한다', () => {
    const { navigate, mutate } = click({ isPending: true });

    expect(mutate).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each([
    [false, true],
    [true, false],
  ])('isWished %s → wished %s로 요청한다', (isWished, wished) => {
    const { mutate } = click({ item: { ...ITEM, isWished } });

    expect(mutate).toHaveBeenCalledWith({ itemId: 101, wished });
  });
});
