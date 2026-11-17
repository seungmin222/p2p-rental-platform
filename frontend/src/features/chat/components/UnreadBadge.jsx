import { useAuthStore } from '../../../shared/api';
import { CountBadge } from '../../../shared/ui';
import { useChatUnreadCount } from '../queries';

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
