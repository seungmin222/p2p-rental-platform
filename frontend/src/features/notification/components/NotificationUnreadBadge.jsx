import { useAuthStore } from '../../../shared/api';
import { CountBadge } from '../../../shared/ui';
import { useNotificationUnreadCount } from '../queries';

/** 하단 탭·PC 헤더 "알림"의 안 읽은 수. 로그인했을 때만 부른다. 화면 낭독기용 문구를 함께 둔다 */
export default function NotificationUnreadBadge({ className = '' }) {
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);
  const { data } = useNotificationUnreadCount({ enabled: isLoggedIn });
  const count = isLoggedIn ? (data?.count ?? 0) : 0;
  if (count <= 0) {
    return null;
  }
  return (
    <>
      <CountBadge count={count} className={className} />
      <span className="sr-only">, 안 읽은 알림 {count}개</span>
    </>
  );
}
