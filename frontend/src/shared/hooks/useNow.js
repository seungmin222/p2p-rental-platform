import { useEffect, useState } from 'react';

/**
 * 지금 시각(밀리초). intervalMs마다 다시 그린다. 렌더 중에 Date.now()를 부르지 않으려고 쓴다
 * (예: 대여 상세의 차감 제안 응답 기한 지남 판단).
 */
export function useNow(intervalMs) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(timer);
  }, [intervalMs]);
  return now;
}
