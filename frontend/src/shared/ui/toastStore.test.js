import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { toast, useToastStore } from './toastStore';

beforeEach(() => {
  vi.useFakeTimers();
  useToastStore.setState({ toasts: [] });
});

afterEach(() => {
  vi.useRealTimers();
});

describe('toast', () => {
  it('3초 뒤 사라진다', () => {
    toast.show({ message: '저장했어요' });
    expect(useToastStore.getState().toasts).toHaveLength(1);
    vi.advanceTimersByTime(2999);
    expect(useToastStore.getState().toasts).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(useToastStore.getState().toasts).toHaveLength(0);
  });

  it('최대 3개, 넘치면 오래된 것부터 버린다', () => {
    ['1', '2', '3', '4'].forEach((message) => toast.show({ message }));
    expect(useToastStore.getState().toasts.map((t) => t.message)).toEqual(['2', '3', '4']);
  });

  it('tone 기본값은 default', () => {
    toast.show({ message: 'a' });
    toast.show({ message: 'b', tone: 'danger' });
    expect(useToastStore.getState().toasts.map((t) => t.tone)).toEqual(['default', 'danger']);
  });
});
