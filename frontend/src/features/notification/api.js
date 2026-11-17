// 알림 REST 호출(REQ-032). 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient } from '../../shared/api';

/** GET /api/notifications?cursor= → CursorResponse<NotificationResponse> (최신순, 한 번에 policies.md notification.page건, size 없음) */
export function fetchNotifications(cursor) {
  return apiClient.get('/notifications', { params: cursor ? { cursor } : undefined });
}

/** GET /api/notifications/unread-count → UnreadCountResponse { unreadCount } */
export function fetchNotificationUnreadCount() {
  return apiClient.get('/notifications/unread-count');
}

/** PATCH /api/notifications/{notificationId}/read (멱등, 없음 404·남의 알림 403) */
export function markNotificationRead(notificationId) {
  return apiClient.patch(`/notifications/${notificationId}/read`);
}

/** PATCH /api/notifications/read-all → NotificationReadAllResponse { updatedCount } */
export function markAllNotificationsRead() {
  return apiClient.patch('/notifications/read-all');
}
