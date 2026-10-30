import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { REGION_SEARCH_DEBOUNCE_MS } from '../../features/region/constants';
import { scheduleDebounce } from './useDebouncedValue';

// node 테스트 환경이라 훅을 렌더하지 않고, 훅의 effect가 하는 일(값이 바뀌면 이전 예약 취소 → 새로 예약)을 그대로 흉내 낸다.
// 그래서 useState·effect 배선 자체는 이 테스트가 아니라 헤드리스(입력마다 요청하지 않음)로 확인한다
const DELAY = REGION_SEARCH_DEBOUNCE_MS;
function createEffectRunner(delayMs) {
  const settled = [];
  let cleanup = null;
  return {
    settled,
    change(value) {
      cleanup?.();
      cleanup = scheduleDebounce(() => settled.push(value), delayMs);
    },
    unmount() {
      cleanup?.();
    },
  };
}

beforeEach(() => {
  vi.useFakeTimers();
});
afterEach(() => {
  vi.useRealTimers();
});

describe('scheduleDebounce (useDebouncedValue)', () => {
  it('지연 시간 전에는 반영하지 않고 지연 시간이 되면 반영한다', () => {
    const runner = createEffectRunner(DELAY);
    runner.change('송');
    vi.advanceTimersByTime(DELAY - 1);
    expect(runner.settled).toEqual([]);
    vi.advanceTimersByTime(1);
    expect(runner.settled).toEqual(['송']);
  });

  it('지연 시간 안에 계속 바뀌면 마지막 값 한 번만', () => {
    const runner = createEffectRunner(DELAY);
    runner.change('송');
    vi.advanceTimersByTime(DELAY - 100);
    runner.change('송도');
    vi.advanceTimersByTime(DELAY - 100);
    runner.change('송도1');
    vi.advanceTimersByTime(DELAY - 1);
    expect(runner.settled).toEqual([]);
    vi.advanceTimersByTime(1);
    expect(runner.settled).toEqual(['송도1']);
  });

  it('화면이 내려가면(cleanup) 반영하지 않는다', () => {
    const runner = createEffectRunner(DELAY);
    runner.change('송');
    runner.unmount();
    vi.advanceTimersByTime(DELAY * 3);
    expect(runner.settled).toEqual([]);
  });
});
