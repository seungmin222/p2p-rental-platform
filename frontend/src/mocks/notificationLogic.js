// 알림 목의 순수 로직(node 테스트). 응답 형태는 docs/openapi.json NotificationResponse·CursorResponse 그대로.

/** policies.md notification.page — 한 번에 20건(size를 받지 않는다) */
export const NOTIFICATION_PAGE = 20;

export function encodeCursor(notificationId) {
  return btoa(String(notificationId));
}

/** 잘못된 커서면 undefined(400) */
export function decodeCursor(cursor) {
  try {
    const value = atob(cursor);
    return /^\d+$/.test(value) ? Number(value) : undefined;
  } catch {
    return undefined;
  }
}

/** 내 알림 최신순(id 내림차순) 한 페이지. cursor는 이전 페이지 마지막 id(그보다 작은 것부터) */
export function pageNotifications(notifications, memberId, cursorId = null) {
  const mine = notifications
    .filter((notification) => notification.memberId === memberId)
    .filter((notification) => cursorId == null || notification.notificationId < cursorId)
    .sort((a, b) => b.notificationId - a.notificationId);
  const items = mine.slice(0, NOTIFICATION_PAGE).map(toNotificationResponse);
  const hasNext = mine.length > NOTIFICATION_PAGE;
  return {
    items,
    hasNext,
    nextCursor: hasNext ? encodeCursor(items.at(-1).notificationId) : null,
  };
}

export function toNotificationResponse(notification) {
  return {
    notificationId: notification.notificationId,
    type: notification.type,
    content: notification.content,
    targetType: notification.targetType,
    targetId: notification.targetId,
    isRead: notification.isRead,
    createdAt: notification.createdAt,
  };
}

export function countUnreadNotifications(notifications, memberId) {
  return notifications.filter((n) => n.memberId === memberId && !n.isRead).length;
}

/** 내 안 읽은 알림을 전부 읽음으로 바꾸고 바꾼 수 */
export function markAllRead(notifications, memberId) {
  let updatedCount = 0;
  for (const notification of notifications) {
    if (notification.memberId === memberId && !notification.isRead) {
      notification.isRead = true;
      updatedCount += 1;
    }
  }
  return updatedCount;
}
