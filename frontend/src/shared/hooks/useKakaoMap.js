import { useEffect, useState } from 'react';

import { getKakaoMapKey, loadKakaoMap } from '../lib/kakaoMapLoader';

/**
 * 카카오맵 SDK 로드 상태(REQ-030). 키가 비어 있으면 처음부터 실패로 본다.
 * @returns {{ status: 'loading' | 'ready' | 'error', kakao: object | null }}
 */
export function useKakaoMap() {
  const [state, setState] = useState(() => ({
    status: getKakaoMapKey() ? 'loading' : 'error',
    kakao: null,
  }));

  useEffect(() => {
    if (!getKakaoMapKey()) {
      return undefined;
    }
    let active = true;
    loadKakaoMap().then(
      (kakao) => active && setState({ status: 'ready', kakao }),
      () => active && setState({ status: 'error', kakao: null }),
    );
    return () => {
      active = false;
    };
  }, []);

  return state;
}
