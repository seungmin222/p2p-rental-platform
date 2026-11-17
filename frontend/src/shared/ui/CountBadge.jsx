import { formatBadgeCount } from '../lib/format';

/** 안 읽은 수 배지 표시 상한(UI 표기, 정책 값 아님). 넘으면 "99+" */
export const COUNT_BADGE_MAX = 99;

/** 빨간 숫자 배지(0이면 그리지 않음). 채팅·알림 안 읽은 수. 시안 배지는 11px이지만 토큰 규칙상 배지 글씨 text-xs */
export default function CountBadge({ count, className = '' }) {
  const label = formatBadgeCount(count, COUNT_BADGE_MAX);
  if (label === null) {
    return null;
  }
  return (
    <span
      className={`inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1.5 text-xs leading-none font-semibold text-white ${className}`}
    >
      {label}
    </span>
  );
}
