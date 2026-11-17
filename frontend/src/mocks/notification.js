// 알림 REST 목(REQ-032). 요청·응답 형태는 docs/openapi.json 그대로. 시드는 notificationSeed.js 맨 위 표.
// 실시간 전달은 chatStomp.js의 가짜 STOMP 서버로 /user/queue/notifications에 보낸다(mockNotificationControls).
import { http } from 'msw';

import { pushToMember } from './chatStomp';
import {
  countUnreadNotifications,
  decodeCursor,
  markAllRead,
  pageNotifications,
  toNotificationResponse,
} from './notificationLogic';
import { createNotificationSeed, NOTIFICATION_CONTENT } from './notificationSeed';
import { readPathId } from './pageList';
import { toServerDateTime } from './rentalSeed';
import { fail, ok } from './response';
import { authenticateMockRequest } from './session';

const notifications = createNotificationSeed();

// openapi.json 응답 예시의 메시지
const MESSAGES = {
  AUTH_UNAUTHORIZED: '로그인이 필요합니다.',
  COMMON_INVALID_INPUT: '입력값이 올바르지 않습니다.',
  NOTIFICATION_NOT_FOUND: '알림을 찾을 수 없습니다.',
  NOTIFICATION_NOT_OWNER: '본인의 알림만 처리할 수 있습니다.',
};

function failWith(status, code) {
  return fail(status, code, MESSAGES[code]);
}

export const notificationHandlers = [
  http.get('/api/notifications', ({ request }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    const cursor = new URL(request.url).searchParams.get('cursor');
    const cursorId = cursor == null ? null : decodeCursor(cursor);
    if (cursorId === undefined) {
      return failWith(400, 'COMMON_INVALID_INPUT');
    }
    return ok(pageNotifications(notifications, me.memberId, cursorId));
  }),

  http.get('/api/notifications/unread-count', ({ request }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    return ok({ unreadCount: countUnreadNotifications(notifications, me.memberId) });
  }),

  http.patch('/api/notifications/read-all', ({ request }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    return ok({ updatedCount: markAllRead(notifications, me.memberId) });
  }),

  http.patch('/api/notifications/:notificationId/read', ({ request, params }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    const notificationId = readPathId(params.notificationId);
    if (notificationId == null) {
      return failWith(400, 'COMMON_INVALID_INPUT');
    }
    const notification = notifications.find((n) => n.notificationId === notificationId);
    if (!notification) {
      return failWith(404, 'NOTIFICATION_NOT_FOUND');
    }
    if (notification.memberId !== me.memberId) {
      return failWith(403, 'NOTIFICATION_NOT_OWNER');
    }
    notification.isRead = true;
    return ok(null);
  }),
];

/**
 * 개발 확인용(콘솔 window.__mockNotification):
 * - push(type, rentalId, memberId): 알림 하나를 저장하고 접속 중이면 /user/queue/notifications로 보낸다 → 배지·목록 갱신 확인
 */
export const mockNotificationControls = {
  push(type = 'OVERDUE', rentalId = 8, memberId = 1) {
    const notification = {
      notificationId: Math.max(0, ...notifications.map((n) => n.notificationId)) + 1,
      memberId,
      type,
      content: NOTIFICATION_CONTENT[type] ?? '새 알림이 도착했어요.',
      targetType: 'RENTAL',
      targetId: rentalId,
      isRead: false,
      createdAt: toServerDateTime(new Date()),
    };
    notifications.push(notification);
    pushToMember(memberId, '/user/queue/notifications', toNotificationResponse(notification));
    return notification.notificationId;
  },
};
