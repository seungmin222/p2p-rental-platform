import { useEffect, useEffectEvent } from 'react';

import { subscribe } from './stompClient';

/**
 * 화면이 떠 있는 동안 destination을 구독한다(destination이 null이면 하지 않음).
 * 핸들러는 최신 렌더의 것을 부르므로 매번 다시 구독하지 않는다.
 */
export function useStompSubscription(destination, handler, { onReady, onError } = {}) {
  const handleMessage = useEffectEvent((body) => handler(body));
  const handleReady = useEffectEvent(() => onReady?.());
  const handleError = useEffectEvent((code) => onError?.(code));

  useEffect(() => {
    if (!destination) {
      return undefined;
    }
    return subscribe(destination, handleMessage, { onReady: handleReady, onError: handleError });
  }, [destination]);
}
