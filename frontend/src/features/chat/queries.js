import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getNextCursorParam } from '../../shared/lib/listParams';
import { useStompSubscription } from '../../shared/ws';
import { getActiveChatRoom } from './activeRoom';
import {
  createChatRoom,
  fetchChatMessages,
  fetchChatRoom,
  fetchChatRooms,
  fetchChatUnreadCount,
} from './api';
import { applyOwnMessage, applyRoomUpdate, markRoomRead } from './chatCache';
import { CHAT_DESTINATIONS } from './constants';

export const chatKeys = {
  all: ['chats'],
  list: () => ['chats', 'list'],
  unreadCount: () => ['chats', 'unread-count'],
  room: (roomId) => ['chats', 'rooms', roomId],
  messages: (roomId) => ['chats', 'rooms', roomId, 'messages'],
};

// 목록·안 읽은 수는 실시간 이벤트로 고치지만, 다른 기기에서 읽은 것은 이벤트가 없어 화면에 들어올 때마다 다시 받는다
export function useChatRooms() {
  return useQuery({ queryKey: chatKeys.list(), queryFn: fetchChatRooms, staleTime: 0 });
}

export function useChatUnreadCount({ enabled }) {
  return useQuery({
    queryKey: chatKeys.unreadCount(),
    queryFn: fetchChatUnreadCount,
    staleTime: 0,
    enabled,
  });
}

export function useChatRoom(roomId) {
  return useQuery({
    queryKey: chatKeys.room(roomId),
    queryFn: () => fetchChatRoom(roomId),
    staleTime: 10_000,
    enabled: roomId != null,
  });
}

/**
 * 내역(최신 50건씩, 위로 스크롤하면 이전). 방에 있는 동안은 실시간 메시지·재연결 보충으로 고치므로 다시 받지 않고,
 * 방을 나가면(구독 해제) 바로 버린다(gcTime 0) — 다음에 들어올 때 새로 받는다.
 */
export function useChatMessages(roomId, { enabled }) {
  return useInfiniteQuery({
    queryKey: chatKeys.messages(roomId),
    queryFn: ({ pageParam }) => fetchChatMessages(roomId, pageParam),
    initialPageParam: null,
    getNextPageParam: getNextCursorParam,
    staleTime: Infinity,
    gcTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    enabled,
  });
}

/** 채팅하기: 방을 만들거나(201) 기존 방(200)을 받는다. 새 방이면 목록에 없으므로 목록을 무효화 */
export function useCreateChatRoom() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createChatRoom,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: chatKeys.list() }),
  });
}

/**
 * 로그인 동안 /user/queue/chat-rooms(상대가 보낸 메시지)로 목록·안 읽은 수를 고친다. AppLayout에서 한 번 건다.
 * 보고 있는 방이면 안 읽은 수를 더하지 않는다(activeRoom.js).
 */
export function useChatRoomUpdates({ enabled }) {
  const queryClient = useQueryClient();
  useStompSubscription(enabled ? CHAT_DESTINATIONS.roomUpdates : null, (event) => {
    const isActive = getActiveChatRoom() === event.roomId;
    const update = isActive ? { ...event, unreadCount: 0 } : event;
    const total = isActive ? event.totalUnreadCount - event.unreadCount : event.totalUnreadCount;

    const rooms = queryClient.getQueryData(chatKeys.list());
    const next = applyRoomUpdate(rooms, update);
    if (next === null) {
      void queryClient.invalidateQueries({ queryKey: chatKeys.list() });
    } else if (next) {
      queryClient.setQueryData(chatKeys.list(), next);
    }
    queryClient.setQueryData(chatKeys.unreadCount(), { unreadCount: Math.max(0, total) });
  });
}

/** 내가 보낸 메시지가 방 토픽으로 돌아오면 목록의 마지막 메시지를 고친다(나에게는 목록 이벤트가 없다) */
export function applyOwnMessageToList(queryClient, message) {
  const rooms = queryClient.getQueryData(chatKeys.list());
  const next = applyOwnMessage(rooms, message);
  if (next === null) {
    void queryClient.invalidateQueries({ queryKey: chatKeys.list() });
  } else if (next) {
    queryClient.setQueryData(chatKeys.list(), next);
  }
}

/**
 * 읽음을 보낸 뒤: 목록의 그 방을 0으로, 전체 수에서 그 방 수만큼 뺀다. 나에게 오는 이벤트가 없어서다.
 * 목록이 없어 얼마나 뺄지 모르면 전체 수를 다시 받는다.
 */
export function applyReadToCaches(queryClient, roomId) {
  const rooms = queryClient.getQueryData(chatKeys.list());
  const room = rooms?.find((candidate) => candidate.roomId === roomId);
  const unread = queryClient.getQueryData(chatKeys.unreadCount());
  if (room && unread) {
    queryClient.setQueryData(chatKeys.unreadCount(), {
      unreadCount: Math.max(0, unread.unreadCount - room.unreadCount),
    });
  } else {
    void queryClient.invalidateQueries({ queryKey: chatKeys.unreadCount() });
  }
  if (rooms) {
    queryClient.setQueryData(chatKeys.list(), markRoomRead(rooms, roomId));
  }
}
