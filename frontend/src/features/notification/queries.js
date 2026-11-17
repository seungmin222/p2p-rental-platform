import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getNextCursorParam } from '../../shared/lib/listParams';
import { useStompSubscription } from '../../shared/ws';
import { rentalKeys } from '../rental/queries';
import {
  fetchNotificationUnreadCount,
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from './api';
import { NOTIFICATION_DESTINATION } from './constants';
import { markAllReadInList, markReadInList, prependNotification } from './notificationCache';

export const notificationKeys = {
  all: ['notifications'],
  list: () => ['notifications', 'list'],
  unreadCount: () => ['notifications', 'unread-count'],
};

// 다른 기기에서 읽은 것은 이벤트가 없어 화면에 들어올 때마다 다시 받는다(채팅 목록과 같은 이유)
export function useNotifications() {
  return useInfiniteQuery({
    queryKey: notificationKeys.list(),
    queryFn: ({ pageParam }) => fetchNotifications(pageParam),
    initialPageParam: null,
    getNextPageParam: getNextCursorParam,
    staleTime: 0,
  });
}

export function useNotificationUnreadCount({ enabled }) {
  return useQuery({
    queryKey: notificationKeys.unreadCount(),
    queryFn: fetchNotificationUnreadCount,
    staleTime: 0,
    enabled,
  });
}

/**
 * 알림 하나 읽음. 누르자마자 이동하므로 캐시를 먼저 고친다(안 읽은 수 −1). 실패하면 되돌리지 않고 서버 값으로 다시 받는다
 * (404·403도 같다 — 화면에서 따로 알리지 않는다).
 */
export function useMarkNotificationRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notification) => markNotificationRead(notification.notificationId),
    onMutate: (notification) => {
      if (notification.isRead) {
        return;
      }
      const list = markReadInList(
        queryClient.getQueryData(notificationKeys.list()),
        notification.notificationId,
      );
      if (list) {
        queryClient.setQueryData(notificationKeys.list(), list);
      }
      const unread = queryClient.getQueryData(notificationKeys.unreadCount());
      if (unread) {
        queryClient.setQueryData(notificationKeys.unreadCount(), {
          unreadCount: Math.max(0, unread.unreadCount - 1),
        });
      }
    },
    onError: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  });
}

/** 모두 읽음: 성공하면 목록을 전부 읽음, 안 읽은 수 0 */
export function useMarkAllNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      const list = markAllReadInList(queryClient.getQueryData(notificationKeys.list()));
      if (list) {
        queryClient.setQueryData(notificationKeys.list(), list);
      }
      queryClient.setQueryData(notificationKeys.unreadCount(), { unreadCount: 0 });
    },
  });
}

/**
 * 로그인 동안 /user/queue/notifications(NotificationResponse)로 목록·안 읽은 수를 고친다. AppLayout에서 한 번 건다.
 * 본문에 전체 수가 없어 안 읽은 수는 다시 받는다. 대여 알림이면 그 대여 상세도 다시 받는다(상태가 바뀌었을 수 있다).
 */
export function useNotificationUpdates({ enabled }) {
  const queryClient = useQueryClient();
  useStompSubscription(enabled ? NOTIFICATION_DESTINATION : null, (notification) => {
    const list = prependNotification(
      queryClient.getQueryData(notificationKeys.list()),
      notification,
    );
    if (list) {
      queryClient.setQueryData(notificationKeys.list(), list);
    }
    void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount() });
    if (notification.targetType === 'RENTAL' && notification.targetId != null) {
      void queryClient.invalidateQueries({ queryKey: rentalKeys.detail(notification.targetId) });
    }
  });
}
