import { useEffect, useState } from 'react';

/**
 * delayMs 뒤에 callback을 부르고, 그 전에 취소하는 함수를 돌려준다.
 * useDebouncedValue의 effect가 이 함수를 그대로 쓰고 cleanup으로 취소하므로, 값이 바뀔 때마다 이전 예약이 취소된다(테스트 대상)
 */
export function scheduleDebounce(callback, delayMs) {
  const timer = setTimeout(callback, delayMs);
  return () => clearTimeout(timer);
}

/** value가 delayMs 동안 바뀌지 않으면 그 값을 돌려준다(검색 입력마다 요청하지 않게) */
export function useDebouncedValue(value, delayMs) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => scheduleDebounce(() => setDebounced(value), delayMs), [value, delayMs]);
  return debounced;
}
