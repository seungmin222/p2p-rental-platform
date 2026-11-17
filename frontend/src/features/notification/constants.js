/** docs/notification.md 설계 결정: 채팅과 같은 STOMP 연결의 개인 큐. 본문은 NotificationResponse */
export const NOTIFICATION_DESTINATION = '/user/queue/notifications';

/**
 * 알림 종류(state-machines.md §5 NotificationType) 중 경고 색(danger)으로 보일 것. 나머지는 기본 색.
 * 문구는 서버 content를 그대로 쓴다(open-issues #50 임시 문구).
 */
export const DANGER_NOTIFICATION_TYPES = new Set([
  'OVERDUE',
  'PREVIOUS_RENTAL_OVERDUE',
  'LOST_REPORTED',
]);
