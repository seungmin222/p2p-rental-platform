// 알림 목 시드. 나 = 회원 1(동네주민). 시각은 지금 기준 상대값, 대상 대여 id는 rentalSeed.js 표.
// 문구는 백엔드 NotificationContentTemplate(임시 문구, open-issues #50)을 옮긴 것이다.
//
// | id | 종류 | 대상 | 경우 |
// | --- | --- | --- | --- |
// | 30 | PREVIOUS_RENTAL_OVERDUE | 대여 12 | 앞 대여 연체 → 수수료 없는 취소(안 읽음) |
// | 29 | OVERDUE | 대여 8 | 대여자 쪽 연체(안 읽음) |
// | 28 | OVERDUE | 대여 27 | 제공자 쪽 연체(안 읽음) |
// | 27 | HANDOVER_REMINDER | 대여 6 | 인계 리마인더(안 읽음) |
// | 26 | RETURN_REQUESTED | 대여 28 | 제공자가 받은 반납 요청(안 읽음) |
// | 25 | CANCEL_AGREEMENT_REQUESTED | 대여 6 | 상대의 동의 요청(읽음) |
// | 1~24 | 여러 종류 | 대여 2·10·11 | 읽은 알림(무한 스크롤 2페이지) |
// | 900 | OVERDUE | 대여 900 | 남의 알림 → 읽음 403 NOTIFICATION_NOT_OWNER |
import { toServerDateTime } from './rentalSeed';

export const NOTIFICATION_CONTENT = {
  RESERVATION_CONFIRMED: '예약이 확정됐어요.',
  PAYMENT_COMPLETED: '결제가 완료돼 예약이 확정됐어요.',
  RESERVATION_CANCELED: '예약이 취소됐어요.',
  CANCEL_AGREEMENT_REQUESTED: '상대방이 예약 취소 동의를 요청했어요.',
  HANDOVER_CONFIRMED: '물품 인계가 확인됐어요. 대여가 시작됐어요.',
  HANDOVER_REMINDER: '아직 물품 인계 확인이 없어요.',
  RETURN_REMINDER: '내일이 반납 예정일이에요.',
  RETURN_REQUESTED: '대여자가 반납을 요청했어요. 물품 상태를 확인해 주세요.',
  INSPECTION_RESULT: '반납 물품의 상태 확인 결과가 나왔어요.',
  OVERDUE: '반납 예정일이 지났어요.',
  PREVIOUS_RENTAL_OVERDUE:
    '앞선 대여가 아직 반납되지 않아 예약한 날에 물품을 받지 못할 수 있어요. 수수료 없이 취소할 수 있어요.',
  REVIEW_RECEIVED: '새 후기가 도착했어요.',
  AI_COMPARE_DONE: 'AI 상태 비교 결과가 나왔어요.',
};

const HOUR_MS = 60 * 60 * 1000;

function hoursAgo(hours) {
  return toServerDateTime(new Date(Date.now() - hours * HOUR_MS));
}

function notification(
  notificationId,
  type,
  targetId,
  hours,
  { memberId = 1, isRead = false } = {},
) {
  return {
    notificationId,
    memberId,
    type,
    content: NOTIFICATION_CONTENT[type],
    targetType: 'RENTAL',
    targetId,
    isRead,
    createdAt: hoursAgo(hours),
  };
}

const PAST_TYPES = [
  ['RESERVATION_CONFIRMED', 2],
  ['HANDOVER_CONFIRMED', 10],
  ['RETURN_REMINDER', 10],
  ['INSPECTION_RESULT', 10],
  ['REVIEW_RECEIVED', 10],
  ['RESERVATION_CANCELED', 11],
];

export function createNotificationSeed() {
  const past = Array.from({ length: 24 }, (_, i) => {
    const [type, targetId] = PAST_TYPES[i % PAST_TYPES.length];
    return notification(i + 1, type, targetId, 24 * (25 - i), { isRead: true });
  });
  return [
    ...past,
    notification(25, 'CANCEL_AGREEMENT_REQUESTED', 6, 20, { isRead: true }),
    notification(26, 'RETURN_REQUESTED', 28, 6),
    notification(27, 'HANDOVER_REMINDER', 6, 3),
    notification(28, 'OVERDUE', 27, 2),
    notification(29, 'OVERDUE', 8, 1),
    notification(30, 'PREVIOUS_RENTAL_OVERDUE', 12, 0.1),
    notification(900, 'OVERDUE', 900, 1, { memberId: 4 }),
  ];
}
