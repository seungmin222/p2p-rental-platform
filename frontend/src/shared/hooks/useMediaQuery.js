import { useCallback, useSyncExternalStore } from 'react';

/**
 * PC 배치 기준 = Tailwind lg(styles/index.css `--breakpoint-lg: 56rem`, 896px~. 1920 화면 반 분할 창이 들어오는 폭).
 * PC 전용 UI를 CSS로 숨기는 대신 아예 그리지 않을 때(데이터 조회가 따라오는 경우) 쓴다
 */
export const PC_MEDIA_QUERY = '(min-width: 56rem)';

function canMatchMedia() {
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function';
}

/** 미디어 쿼리 일치 여부. matchMedia가 없는 환경(테스트 등)에서는 false */
export function useMediaQuery(query) {
  const subscribe = useCallback(
    (onChange) => {
      if (!canMatchMedia()) {
        return () => {};
      }
      const list = window.matchMedia(query);
      list.addEventListener('change', onChange);
      return () => list.removeEventListener('change', onChange);
    },
    [query],
  );
  return useSyncExternalStore(subscribe, () =>
    canMatchMedia() ? window.matchMedia(query).matches : false,
  );
}
