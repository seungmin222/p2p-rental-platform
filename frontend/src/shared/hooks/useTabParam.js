import { useSearchParams } from 'react-router';

import { normalizeTab } from '../lib/listParams';

/**
 * 탭 상태를 URL ?tab=에 둔다(상세에 갔다가 뒤로 오면 같은 탭). 모르는 값이면 fallback.
 * 바꿀 때는 기록을 쌓지 않고 replace한다(뒤로 가기가 탭을 거슬러 가지 않게).
 */
export function useTabParam(tabs, fallback) {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = normalizeTab(searchParams.get('tab'), tabs, fallback);

  function setTab(next) {
    setSearchParams(
      (params) => {
        const nextParams = new URLSearchParams(params);
        nextParams.set('tab', next);
        return nextParams;
      },
      { replace: true },
    );
  }

  return [tab, setTab];
}
