// 가짜 STOMP 서버(MSW ws.link). 목적지·본문은 docs/api.md "STOMP 메시지", 거부 방식은 docs/domains/chat.md "구조"와 같게:
// CONNECT 인증 실패·구독 거부는 ERROR(message = 에러 코드) 후 연결 종료, /pub SEND의 업무 거부는 /user/queue/errors(연결 유지).
// 이 탭 안의 연결끼리만 전달된다(다른 탭·계정은 서로 못 받는다). 상대 쪽은 아래 "상대 흉내"가 대신한다.
import { ws } from 'msw';

import { chatDb, chatError, nowServerTime } from './chat';
import {
  applyRead,
  counterpartOf,
  countTotalUnread,
  countUnread,
  isParticipant,
  validateSend,
} from './chatLogic';
import { WITHDRAWN_MEMBER_ID } from './chatSeed';
import { authenticateMockAuthorization } from './session';
import { parseFrames, serializeFrame } from './stompFrame';

// 상대 흉내: 내가 보내면 이만큼 뒤에 상대가 읽고, 답장한다(목 전용 값)
const COUNTERPART_READ_DELAY_MS = 1_500;
const COUNTERPART_REPLY_DELAY_MS = 3_000;
// 이 내용을 보내면 서버 거부(/user/queue/errors)를 흉내 낸다
const FAIL_COMMAND = '/fail';

const PERSONAL_QUEUES = [
  '/user/queue/chat-rooms',
  '/user/queue/chat-reads',
  '/user/queue/notifications',
  '/user/queue/errors',
];
const ROOM_TOPIC = /^\/topic\/chat-rooms\.(\d+)$/;
const SEND_MESSAGE = /^\/pub\/chat-rooms\/(\d+)$/;
const SEND_READ = /^\/pub\/chat-rooms\/(\d+)\/read$/;

const chatSocket = ws.link(`${window.location.origin.replace(/^http/, 'ws')}/ws`);

// 연결: { client, memberId, subscriptions: Map<subId, destination> }
const connections = new Set();
let messageSequence = 0;

function send(connection, command, headers, body) {
  connection.client.send(serializeFrame(command, headers, body));
}

function deliver(connection, destination, payload) {
  for (const [subscriptionId, subscribed] of connection.subscriptions) {
    if (subscribed === destination) {
      messageSequence += 1;
      send(
        connection,
        'MESSAGE',
        {
          destination,
          subscription: subscriptionId,
          'message-id': `mock-${messageSequence}`,
          'content-type': 'application/json',
        },
        JSON.stringify(payload),
      );
    }
  }
}

function deliverToMember(memberId, destination, payload) {
  for (const connection of connections) {
    if (connection.memberId === memberId) {
      deliver(connection, destination, payload);
    }
  }
}

function broadcast(destination, payload) {
  for (const connection of connections) {
    deliver(connection, destination, payload);
  }
}

function rejectAndClose(connection, code, headers = {}) {
  send(connection, 'ERROR', { message: code, ...headers });
  connection.client.close();
}

/** 메시지 저장 → 방 토픽 → 받는 쪽 목록 갱신 이벤트(서버 ChatBroadcastListener 흉내) */
function saveAndBroadcast(room, senderId, type, content, clientMessageId = null) {
  const message = {
    messageId: chatDb.nextMessageId,
    roomId: room.roomId,
    senderId,
    type,
    content,
    sentAt: nowServerTime(),
  };
  chatDb.nextMessageId += 1;
  chatDb.messages.push(message);
  broadcast(`/topic/chat-rooms.${room.roomId}`, { ...message, clientMessageId });
  const receivers =
    senderId == null ? [room.ownerId, room.renterId] : [counterpartOf(room, senderId)];
  for (const receiverId of receivers) {
    deliverToMember(receiverId, '/user/queue/chat-rooms', {
      roomId: room.roomId,
      lastMessageId: message.messageId,
      lastMessagePreview: message.content,
      lastMessageAt: message.sentAt,
      unreadCount: countUnread(chatDb, room.roomId, receiverId),
      totalUnreadCount: countTotalUnread(chatDb, receiverId),
    });
  }
  return message;
}

function readAndNotify(room, memberId, lastMessageId) {
  const result = applyRead(chatDb, room.roomId, memberId, lastMessageId);
  if (result.changed) {
    deliverToMember(counterpartOf(room, memberId), '/user/queue/chat-reads', {
      roomId: room.roomId,
      memberId,
      lastReadMessageId: lastMessageId,
    });
  }
  return result;
}

/** 상대 흉내: 상대가 이 탭에 접속해 있지 않으면 읽고 답장한다(탈퇴한 상대는 하지 않음) */
function simulateCounterpart(room, myMemberId, message) {
  const counterpartId = counterpartOf(room, myMemberId);
  const isOnline = [...connections].some((c) => c.memberId === counterpartId);
  if (isOnline || counterpartId === WITHDRAWN_MEMBER_ID) {
    return;
  }
  setTimeout(
    () => readAndNotify(room, counterpartId, message.messageId),
    COUNTERPART_READ_DELAY_MS,
  );
  setTimeout(() => {
    const preview = [...message.content].slice(0, 20).join('');
    saveAndBroadcast(room, counterpartId, 'TEXT', `(자동 답장) "${preview}" 잘 받았어요`);
  }, COUNTERPART_REPLY_DELAY_MS);
}

function findRoom(roomId) {
  return chatDb.rooms.find((room) => room.roomId === Number(roomId)) ?? null;
}

function replyError(connection, clientMessageId, code) {
  deliver(connection, '/user/queue/errors', {
    clientMessageId: clientMessageId ?? null,
    error: chatError(code),
  });
}

function handleSend(connection, frame) {
  const { destination } = frame.headers;
  let body = null;
  try {
    body = JSON.parse(frame.body);
  } catch {
    // 아래에서 형식 오류로
  }
  const messageMatch = SEND_MESSAGE.exec(destination);
  const readMatch = SEND_READ.exec(destination);
  if (!messageMatch && !readMatch) {
    rejectAndClose(connection, 'AUTH_FORBIDDEN');
    return;
  }
  const room = findRoom((messageMatch ?? readMatch)[1]);
  const clientMessageId = messageMatch ? body?.clientMessageId : null;
  if (!room) {
    replyError(connection, clientMessageId, 'CHAT_ROOM_NOT_FOUND');
    return;
  }
  if (!isParticipant(room, connection.memberId)) {
    replyError(connection, clientMessageId, 'CHAT_NOT_PARTICIPANT');
    return;
  }
  if (readMatch) {
    const result = readAndNotify(room, connection.memberId, body?.lastMessageId);
    if (result.error) {
      replyError(connection, null, result.error);
    }
    return;
  }
  const invalid =
    validateSend(body) ?? (body.content === FAIL_COMMAND ? 'COMMON_INVALID_INPUT' : null);
  if (invalid) {
    replyError(connection, clientMessageId, invalid);
    return;
  }
  const message = saveAndBroadcast(
    room,
    connection.memberId,
    'TEXT',
    body.content,
    clientMessageId,
  );
  simulateCounterpart(room, connection.memberId, message);
}

function handleSubscribe(connection, frame) {
  const { id, destination, receipt } = frame.headers;
  const receiptHeader = receipt ? { 'receipt-id': receipt } : {};
  const topic = ROOM_TOPIC.exec(destination ?? '');
  if (topic) {
    const room = findRoom(topic[1]);
    if (!room || !isParticipant(room, connection.memberId)) {
      rejectAndClose(
        connection,
        room ? 'CHAT_NOT_PARTICIPANT' : 'CHAT_ROOM_NOT_FOUND',
        receiptHeader,
      );
      return;
    }
  } else if (!PERSONAL_QUEUES.includes(destination)) {
    rejectAndClose(connection, 'AUTH_FORBIDDEN', receiptHeader);
    return;
  }
  connection.subscriptions.set(id, destination);
  if (receipt) {
    send(connection, 'RECEIPT', { 'receipt-id': receipt });
  }
}

function handleFrame(connection, frame) {
  if (frame.command === 'CONNECT' || frame.command === 'STOMP') {
    const member = authenticateMockAuthorization(frame.headers.Authorization);
    if (!member) {
      rejectAndClose(connection, 'AUTH_UNAUTHORIZED');
      return;
    }
    connection.memberId = member.memberId;
    send(connection, 'CONNECTED', { version: '1.2', 'heart-beat': '0,0' });
    return;
  }
  if (connection.memberId === null) {
    rejectAndClose(connection, 'AUTH_UNAUTHORIZED');
    return;
  }
  switch (frame.command) {
    case 'SUBSCRIBE':
      handleSubscribe(connection, frame);
      break;
    case 'UNSUBSCRIBE':
      connection.subscriptions.delete(frame.headers.id);
      break;
    case 'SEND':
      handleSend(connection, frame);
      break;
    case 'DISCONNECT':
      if (frame.headers.receipt) {
        send(connection, 'RECEIPT', { 'receipt-id': frame.headers.receipt });
      }
      connection.client.close();
      break;
    default:
      break;
  }
}

export const chatStompHandlers = [
  chatSocket.addEventListener('connection', ({ client }) => {
    const connection = { client, memberId: null, subscriptions: new Map() };
    connections.add(connection);
    client.addEventListener('message', (event) => {
      for (const frame of parseFrames(event.data)) {
        handleFrame(connection, frame);
      }
    });
    client.addEventListener('close', () => connections.delete(connection));
  }),
];

/**
 * 개발 확인용(콘솔 window.__mockChat):
 * - disconnect(): 모든 연결을 끊는다 → 자동 재연결·보충 조회·보내는 중 메시지 실패 확인
 * - system(roomId, content): 그 방에 SYSTEM 안내 메시지(대여 상태 변경 흉내)
 */
export const mockChatControls = {
  disconnect() {
    for (const connection of connections) {
      connection.client.close();
    }
  },
  system(roomId, content = '예약이 확정됐어요') {
    const room = findRoom(roomId);
    if (room) {
      saveAndBroadcast(room, null, 'SYSTEM', content);
    }
  },
};
