import { describe, expect, it } from 'vitest';

import {
  buildMessageRows,
  formatMessageTime,
  formatShortPeriod,
  formatUnreadCount,
  getMyMemberId,
} from './messageView';

function message(messageId, senderId, sentAt, extra = {}) {
  return {
    messageId,
    roomId: 1,
    senderId,
    type: 'TEXT',
    content: `m${messageId}`,
    sentAt,
    ...extra,
  };
}

describe('formatMessageTime', () => {
  it('서버 시각(오프셋 없음)을 서울 시각 HH:mm으로', () => {
    expect(formatMessageTime('2026-10-07T09:05:30')).toBe('09:05');
    expect(formatMessageTime('2026-10-07T21:40:00')).toBe('21:40');
    expect(formatMessageTime(null)).toBe('');
  });
});

describe('getMyMemberId', () => {
  it('상대가 아닌 참여자', () => {
    expect(getMyMemberId({ ownerId: 2, renterId: 5, counterpart: { memberId: 2 } })).toBe(5);
    expect(getMyMemberId({ ownerId: 2, renterId: 5, counterpart: { memberId: 5 } })).toBe(2);
  });
});

describe('buildMessageRows', () => {
  const options = { myMemberId: 1, counterpartLastReadMessageId: 2 };

  it('날짜가 바뀌면 날짜 줄을 넣는다', () => {
    const rows = buildMessageRows(
      [
        message(1, 2, '2026-10-07T10:00:00'),
        message(2, 1, '2026-10-07T10:01:00'),
        message(3, 2, '2026-10-08T09:00:00'),
      ],
      options,
    );
    expect(rows.map((row) => row.kind)).toEqual(['date', 'message', 'message', 'date', 'message']);
    expect(rows[0].label).toBe('10월 7일 수요일');
  });

  it('내 메시지 중 상대 읽음 위치 이하만 읽음', () => {
    const rows = buildMessageRows(
      [message(2, 1, '2026-10-07T10:00:00'), message(3, 1, '2026-10-07T10:05:00')],
      options,
    ).filter((row) => row.kind === 'message');
    expect(rows.map((row) => [row.isMine, row.isRead])).toEqual([
      [true, true],
      [true, false],
    ]);
  });

  it('같은 사람이 같은 분에 연달아 보내면 마지막에만 시각', () => {
    const rows = buildMessageRows(
      [
        message(1, 2, '2026-10-07T10:00:05'),
        message(2, 2, '2026-10-07T10:00:40'),
        message(3, 1, '2026-10-07T10:00:50'),
      ],
      options,
    ).filter((row) => row.kind === 'message');
    expect(rows.map((row) => row.showTime)).toEqual([false, true, true]);
  });

  it('SYSTEM 메시지(senderId null)는 안내 줄', () => {
    const rows = buildMessageRows(
      [message(1, null, '2026-10-07T10:00:00', { type: 'SYSTEM' })],
      options,
    );
    expect(rows[1].kind).toBe('system');
  });
});

describe('formatShortPeriod · formatUnreadCount', () => {
  it('기간을 짧게', () => {
    expect(formatShortPeriod('2026-10-09', '2026-10-11')).toBe('10/9 ~ 10/11');
    expect(formatShortPeriod('2026-10-09', '2026-10-09')).toBe('10/9');
  });

  it('0이면 null, 상한을 넘으면 99+', () => {
    expect(formatUnreadCount(0, 99)).toBeNull();
    expect(formatUnreadCount(5, 99)).toBe('5');
    expect(formatUnreadCount(99, 99)).toBe('99');
    expect(formatUnreadCount(100, 99)).toBe('99+');
  });
});
