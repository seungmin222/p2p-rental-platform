// 채팅 REST 목. 요청·응답 형태는 docs/openapi.json 스키마 그대로. 실시간(STOMP)은 chatStomp.js가 같은 저장소(chatDb)를 쓴다.
// 시드는 chatSeed.js 맨 위 표. 검사 순서는 백엔드와 같게(401 → 404 → 403).
import { http } from 'msw';

import {
  countTotalUnread,
  countUnread,
  counterpartOf,
  decodeCursor,
  isParticipant,
  lastMessageOf,
  listMyRooms,
  pageMessages,
} from './chatLogic';
import { createChatSeed, WITHDRAWN_MEMBER_ID } from './chatSeed';
import { findMockItem, MOCK_MEMBERS } from './item';
import { readPathId } from './pageList';
import { fail, ok } from './response';
import { toServerDateTime } from './rentalSeed';
import { db as rentalDb } from './rentalStore';
import { authenticateMockRequest } from './session';

export const chatDb = createChatSeed();

// openapi.json 응답 예시의 메시지
const MESSAGES = {
  AUTH_UNAUTHORIZED: '로그인이 필요합니다.',
  COMMON_INVALID_INPUT: '입력값이 올바르지 않습니다.',
  ITEM_NOT_FOUND: '물품을 찾을 수 없습니다.',
  CHAT_OWN_ITEM: '본인 물품에는 채팅할 수 없습니다.',
  CHAT_ROOM_NOT_FOUND: '채팅방을 찾을 수 없습니다.',
  CHAT_NOT_PARTICIPANT: '채팅방 참여자만 이용할 수 있습니다.',
};

export function chatError(code) {
  return { code, message: MESSAGES[code], details: [] };
}

function failWith(status, code) {
  return fail(status, code, MESSAGES[code]);
}

export function nowServerTime() {
  return toServerDateTime(new Date());
}

/** ChatCounterpartResponse. 탈퇴 회원은 닉네임·사진·동네 null */
function toCounterpart(memberId) {
  if (memberId === WITHDRAWN_MEMBER_ID || !MOCK_MEMBERS[memberId]) {
    return {
      memberId,
      nickname: null,
      profileImageUrl: null,
      regionName: null,
      isWithdrawn: true,
    };
  }
  const member = MOCK_MEMBERS[memberId];
  return {
    memberId,
    nickname: member.nickname,
    profileImageUrl: null,
    regionName: member.regionName,
    isWithdrawn: false,
  };
}

/** ChatItemResponse. 삭제된 물품도 이름·썸네일을 준다 */
function toChatItem(itemId) {
  const item = findMockItem(itemId);
  return {
    itemId,
    name: item?.name ?? '알 수 없는 물품',
    thumbnailUrl: item?.thumbnailUrl ?? null,
    isDeleted: item?.isDeleted ?? true,
  };
}

/** ChatLatestRentalResponse: (물품, 대여자)의 id 최대 건(상태 무관). 대여 목 저장소에서 찾는다 */
function toLatestRental(room) {
  const latest = rentalDb.rentals
    .filter((rental) => rental.itemId === room.itemId && rental.renterId === room.renterId)
    .sort((a, b) => b.rentalId - a.rentalId)[0];
  return latest
    ? {
        rentalId: latest.rentalId,
        status: latest.status,
        startDate: latest.startDate,
        endDate: latest.endDate,
      }
    : null;
}

function toSummary(room, memberId) {
  const last = lastMessageOf(chatDb, room.roomId);
  return {
    roomId: room.roomId,
    counterpart: toCounterpart(counterpartOf(room, memberId)),
    item: toChatItem(room.itemId),
    lastMessagePreview: last?.content ?? null,
    lastMessageAt: last?.sentAt ?? null,
    unreadCount: countUnread(chatDb, room.roomId, memberId),
  };
}

function toDetail(room, memberId) {
  const counterpartId = counterpartOf(room, memberId);
  return {
    roomId: room.roomId,
    ownerId: room.ownerId,
    renterId: room.renterId,
    counterpart: toCounterpart(counterpartId),
    item: toChatItem(room.itemId),
    latestRental: toLatestRental(room),
    counterpartLastReadMessageId: chatDb.lastRead[`${room.roomId}:${counterpartId}`] ?? null,
  };
}

/** 방 조회 공통: 404 → 403. 통과하면 { room } */
function findRoomFor(roomIdParam, memberId) {
  const room = chatDb.rooms.find((candidate) => candidate.roomId === readPathId(roomIdParam));
  if (!room) {
    return { response: failWith(404, 'CHAT_ROOM_NOT_FOUND') };
  }
  if (!isParticipant(room, memberId)) {
    return { response: failWith(403, 'CHAT_NOT_PARTICIPANT') };
  }
  return { room };
}

export const chatHandlers = [
  // REQ-026 생성 또는 기존 방
  http.post('/api/chat-rooms', async ({ request }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    const body = await request.json().catch(() => null);
    const itemId = body?.itemId;
    if (!Number.isInteger(itemId) || itemId <= 0) {
      return failWith(400, 'COMMON_INVALID_INPUT');
    }
    const item = findMockItem(itemId);
    if (!item || item.isDeleted) {
      return failWith(404, 'ITEM_NOT_FOUND');
    }
    if (item.ownerId === me.memberId) {
      return failWith(400, 'CHAT_OWN_ITEM');
    }
    const existing = chatDb.rooms.find(
      (room) => room.itemId === itemId && room.renterId === me.memberId,
    );
    const room = existing ?? {
      roomId: chatDb.nextRoomId,
      itemId,
      ownerId: item.ownerId,
      renterId: me.memberId,
      createdAt: nowServerTime(),
    };
    if (!existing) {
      chatDb.rooms.push(room);
      chatDb.nextRoomId += 1;
    }
    return ok(
      { roomId: room.roomId, itemId, ownerId: room.ownerId, renterId: room.renterId },
      { status: existing ? 200 : 201 },
    );
  }),

  // REQ-028 목록(페이지 없음)
  http.get('/api/chat-rooms', ({ request }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    return ok(listMyRooms(chatDb, me.memberId).map((room) => toSummary(room, me.memberId)));
  }),

  // REQ-029 전체 안 읽은 수. /:roomId보다 먼저(경로가 겹친다)
  http.get('/api/chat-rooms/unread-count', ({ request }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    return ok({ unreadCount: countTotalUnread(chatDb, me.memberId) });
  }),

  http.get('/api/chat-rooms/:roomId', ({ request, params }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    const { room, response } = findRoomFor(params.roomId, me.memberId);
    return response ?? ok(toDetail(room, me.memberId));
  }),

  // REQ-028 내역(최신 50건, 커서로 이전). 읽음은 바꾸지 않는다
  http.get('/api/chat-rooms/:roomId/messages', ({ request, params }) => {
    const me = authenticateMockRequest(request);
    if (!me) {
      return failWith(401, 'AUTH_UNAUTHORIZED');
    }
    const { room, response } = findRoomFor(params.roomId, me.memberId);
    if (response) {
      return response;
    }
    const cursorParam = new URL(request.url).searchParams.get('cursor');
    const before = cursorParam == null ? null : decodeCursor(cursorParam);
    if (before === undefined) {
      return failWith(400, 'COMMON_INVALID_INPUT');
    }
    const page = pageMessages(chatDb, room.roomId, before);
    return ok({
      ...page,
      items: page.items.map((message) => ({ ...message, clientMessageId: null })),
    });
  }),
];
