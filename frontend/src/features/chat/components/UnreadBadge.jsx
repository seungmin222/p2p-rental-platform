import { useAuthStore } from '../../../shared/api';
import { UNREAD_BADGE_MAX } from '../constants';
import { formatUnreadCount } from '../messageView';
import { useChatUnreadCount } from '../queries';

/** 빨간 숫자 배지(0이면 그리지 않음). 시안 채팅 배지는 11px이지만 토큰 규칙상 배지 글씨 text-xs */
export function CountBadge({ count, className = '' }) {
  const label = formatUnreadCount(count, UNREAD_BADGE_MAX);
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

/** 하단 탭·PC 헤더 "채팅"의 전체 안 읽은 수. 로그인했을 때만 부른다. 화면 낭독기용 문구를 함께 둔다 */
export default function ChatUnreadBadge({ className = '' }) {
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);
  const { data } = useChatUnreadCount({ enabled: isLoggedIn });
  const count = isLoggedIn ? (data?.unreadCount ?? 0) : 0;
  if (count <= 0) {
    return null;
  }
  return (
    <>
      <CountBadge count={count} className={className} />
      <span className="sr-only">, 안 읽은 메시지 {count}개</span>
    </>
  );
}
