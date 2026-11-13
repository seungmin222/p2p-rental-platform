// 채팅방 말풍선 줄을 만드는 순수 함수. 서버 일시(오프셋 없음)는 서울 시각으로 해석한다.
import { parseServerDateTime } from '../../shared/lib/format';
import { CHAT_MESSAGE_TYPE } from './constants';

const SEOUL = 'Asia/Seoul';
const dateKeyFormat = new Intl.DateTimeFormat('en-CA', { timeZone: SEOUL });
const dateLabelFormat = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  month: 'long',
  day: 'numeric',
  weekday: 'long',
});
const timeFormat = new Intl.DateTimeFormat('ko-KR', {
  timeZone: SEOUL,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

/** "2026-10-07T10:12:30" → "10:12". 해석할 수 없으면 "" */
export function formatMessageTime(sentAt) {
  const date = parseServerDateTime(sentAt);
  return date ? timeFormat.format(date) : '';
}

function toDateKey(date) {
  return dateKeyFormat.format(date);
}

/** 상대 이름. 탈퇴한 상대는 닉네임이 null이다(서버가 가린다) */
export function getCounterpartName(counterpart) {
  return counterpart.isWithdrawn ? '탈퇴한 사용자' : (counterpart.nickname ?? '알 수 없는 사용자');
}

/** 내 회원 ID: 방의 두 참여자 중 상대가 아닌 쪽 */
export function getMyMemberId(room) {
  return room.counterpart.memberId === room.ownerId ? room.renterId : room.ownerId;
}

/**
 * 메시지(과거→최신)를 화면 줄로. 날짜가 바뀌면 날짜 줄을 넣는다.
 * - kind 'date' { key, label } · 'system' { message } · 'message' { message, isMine, isRead, showTime }
 * - isRead: 내 메시지이고 상대 읽음 위치(counterpartLastReadMessageId) 이하
 * - showTime: 같은 사람이 같은 분에 연달아 보낸 묶음의 마지막에만
 */
export function buildMessageRows(messages, { myMemberId, counterpartLastReadMessageId }) {
  const rows = [];
  let lastDateKey = null;
  messages.forEach((message, index) => {
    const date = parseServerDateTime(message.sentAt);
    const dateKey = date ? toDateKey(date) : lastDateKey;
    if (date && dateKey !== lastDateKey) {
      rows.push({ kind: 'date', key: `date-${dateKey}`, label: dateLabelFormat.format(date) });
      lastDateKey = dateKey;
    }
    if (message.type === CHAT_MESSAGE_TYPE.SYSTEM || message.senderId == null) {
      rows.push({ kind: 'system', key: `m-${message.messageId}`, message });
      return;
    }
    const next = messages[index + 1];
    const time = formatMessageTime(message.sentAt);
    const continues =
      next &&
      next.type !== CHAT_MESSAGE_TYPE.SYSTEM &&
      next.senderId === message.senderId &&
      formatMessageTime(next.sentAt) === time &&
      toDateKey(parseServerDateTime(next.sentAt) ?? date) === dateKey;
    const isMine = message.senderId === myMemberId;
    rows.push({
      kind: 'message',
      key: `m-${message.messageId}`,
      message,
      isMine,
      isRead:
        isMine &&
        counterpartLastReadMessageId != null &&
        message.messageId <= counterpartLastReadMessageId,
      showTime: !continues,
      time,
    });
  });
  return rows;
}

/** 대여 기간 "2026-10-09"~"2026-10-11" → "10/9 ~ 10/11"(시안 상단 바) */
export function formatShortPeriod(startDate, endDate) {
  const short = (date) => {
    const [, month, day] = date.split('-').map(Number);
    return `${month}/${day}`;
  };
  return startDate === endDate ? short(startDate) : `${short(startDate)} ~ ${short(endDate)}`;
}

/** 배지 숫자: 0이면 null(그리지 않음), max 초과면 "99+" */
export function formatUnreadCount(count, max) {
  if (!count || count <= 0) {
    return null;
  }
  return count > max ? `${max}+` : String(count);
}
