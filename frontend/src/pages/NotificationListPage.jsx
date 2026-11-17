import { useNavigate } from 'react-router';

import NotificationListItem from '../features/notification/components/NotificationListItem';
import { getErrorMessage } from '../features/notification/errorMessages';
import { getNotificationPath } from '../features/notification/notificationTarget';
import {
  useMarkAllNotificationsRead,
  useMarkNotificationRead,
  useNotifications,
  useNotificationUnreadCount,
} from '../features/notification/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { flattenPages } from '../shared/lib/listParams';
import { isInitialLoadError } from '../shared/lib/queryState';
import { Button, EmptyState, ErrorState, InfiniteListFooter, Skeleton, toast } from '../shared/ui';

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중" className="divide-y divide-divider">
      {Array.from({ length: 5 }, (_, index) => (
        <li key={index} className="flex items-start gap-3 py-4">
          <Skeleton className="size-10 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-3/4" />
            <Skeleton className="h-3 w-1/4" />
          </div>
        </li>
      ))}
    </ul>
  );
}

/**
 * SCR-11 알림 목록(REQ-032, 연체 알림 031의 확인 경로). 최신순 커서 무한 스크롤.
 * 들어오기만 해서는 읽음 처리하지 않는다. 누르면 읽음 + 관련 화면으로 이동(갈 곳이 없으면 읽음만), "모두 읽음"은 위에.
 * 실시간 알림은 /user/queue/notifications로 맨 앞에 붙는다(features/notification/queries.js useNotificationUpdates).
 */
export default function NotificationListPage() {
  const navigate = useNavigate();
  const query = useNotifications();
  const unreadQuery = useNotificationUnreadCount({ enabled: true });
  const markRead = useMarkNotificationRead();
  const markAllRead = useMarkAllNotificationsRead();
  useRefetchErrorToast([query]);

  const notifications = flattenPages(query.data);
  const unreadCount = unreadQuery.data?.unreadCount ?? 0;

  function handleSelect(notification) {
    if (!notification.isRead) {
      markRead.mutate(notification);
    }
    const path = getNotificationPath(notification);
    if (path) {
      navigate(path);
    }
  }

  function handleMarkAllRead() {
    markAllRead.mutate(undefined, {
      onError: (error) => toast.show({ message: getErrorMessage(error), tone: 'danger' }),
    });
  }

  let content;
  if (isInitialLoadError(query)) {
    content = <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  } else if (!notifications) {
    content = <ListSkeleton />;
  } else if (notifications.length === 0) {
    content = (
      <EmptyState
        title="아직 알림이 없어요"
        description="예약·인계·반납 소식이 생기면 여기에 알려 드릴게요"
      />
    );
  } else {
    content = (
      <>
        <ul className="divide-y divide-divider">
          {notifications.map((notification) => (
            <li key={notification.notificationId}>
              <NotificationListItem notification={notification} onSelect={handleSelect} />
            </li>
          ))}
        </ul>
        <InfiniteListFooter query={query} />
      </>
    );
  }

  return (
    <div className="mx-auto w-full max-w-2xl">
      <div className="flex h-14 items-center justify-between">
        <h1 className="text-lg font-bold">알림</h1>
        <Button
          variant="secondary"
          size="sm"
          onClick={handleMarkAllRead}
          disabled={unreadCount <= 0}
          isLoading={markAllRead.isPending}
        >
          모두 읽음
        </Button>
      </div>
      {content}
    </div>
  );
}
