import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useReducer, useRef, useState, useSyncExternalStore } from 'react';

import {
  publish,
  STOMP_STATUS,
  useStompStatus,
  useStompStatusStore,
  useStompSubscription,
} from '../../shared/ws';
import { clearActiveChatRoom, setActiveChatRoom } from './activeRoom';
import { fetchChatMessages } from './api';
import { collectNewerMessages, getLatestMessageId, mergeMessages } from './chatCache';
import { CHAT_DESTINATIONS } from './constants';
import { getErrorMessage } from './errorMessages';
import { PENDING_ACTION, pendingReducer } from './pendingMessages';
import { applyOwnMessageToList, applyReadToCaches, chatKeys, useChatMessages } from './queries';

const DISCONNECTED_MESSAGE = '연결이 끊겨 보내지 못했어요';

function subscribeVisibility(callback) {
  document.addEventListener('visibilitychange', callback);
  return () => document.removeEventListener('visibilitychange', callback);
}

function useIsPageVisible() {
  return useSyncExternalStore(subscribeVisibility, () => document.visibilityState === 'visible');
}

// 낙관적 표시용 임시 id(서버는 저장하지 않는다). randomUUID는 보안 출처(https·localhost)에서만 있다
function createClientMessageId() {
  return (
    globalThis.crypto?.randomUUID?.() ?? `c-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

/**
 * 채팅방 하나의 실시간 처리(SCR-10). room은 방 정보(ChatRoomDetailResponse)를 받은 뒤에 넘긴다.
 * - 방 토픽 구독 → 내역 캐시에 합치고, 같은 clientMessageId의 임시 메시지를 지운다
 * - 구독이 서버에 받아들여질 때마다(재연결 포함) 내역을 받은 상태면 빈 구간을 REST로 보충
 * - /user/queue/errors → 임시 메시지 실패, 연결이 끊기면 보내는 중인 것을 모두 실패
 * - 화면이 보이는 동안 가장 최근 메시지 id로 읽음 전송, /user/queue/chat-reads로 상대 읽음 위치 갱신
 */
export function useChatRoomSession(roomId, room) {
  const queryClient = useQueryClient();
  const status = useStompStatus();
  const isVisible = useIsPageVisible();
  const messages = useChatMessages(roomId, { enabled: room != null });
  const [pending, dispatch] = useReducer(pendingReducer, []);
  const [readyCount, setReadyCount] = useState(0);
  const lastReadSentRef = useRef(null);
  const hasData = messages.data !== undefined;

  const topic = room ? CHAT_DESTINATIONS.roomTopic(roomId) : null;
  useStompSubscription(
    topic,
    (message) => {
      queryClient.setQueryData(chatKeys.messages(roomId), (data) => mergeMessages(data, [message]));
      if (message.clientMessageId) {
        dispatch({ type: PENDING_ACTION.CONFIRM, clientMessageId: message.clientMessageId });
      }
      if (message.senderId != null && message.senderId !== room.counterpart.memberId) {
        applyOwnMessageToList(queryClient, message);
      }
    },
    { onReady: () => setReadyCount((count) => count + 1) },
  );

  useStompSubscription(room ? CHAT_DESTINATIONS.reads : null, (event) => {
    if (event.roomId !== roomId || event.memberId !== room.counterpart.memberId) {
      return;
    }
    queryClient.setQueryData(chatKeys.room(roomId), (detail) =>
      detail && (detail.counterpartLastReadMessageId ?? 0) < event.lastReadMessageId
        ? { ...detail, counterpartLastReadMessageId: event.lastReadMessageId }
        : detail,
    );
  });

  useStompSubscription(room ? CHAT_DESTINATIONS.errors : null, (event) => {
    // 읽음 전송 거부(clientMessageId null)는 표시할 곳이 없다 — 다음 메시지 때 다시 보낸다
    if (event.clientMessageId) {
      dispatch({
        type: PENDING_ACTION.FAIL,
        clientMessageId: event.clientMessageId,
        errorMessage: getErrorMessage(event.error),
      });
    }
  });

  // 연결이 끊기면 서버 확인을 못 받은 메시지는 실패로(서버가 clientMessageId를 저장하지 않아 보충 조회로 짝을 못 맞춘다)
  useEffect(
    () =>
      useStompStatusStore.subscribe((state) => {
        if (state.status !== STOMP_STATUS.CONNECTED) {
          dispatch({ type: PENDING_ACTION.FAIL_ALL_SENDING, errorMessage: DISCONNECTED_MESSAGE });
        }
      }),
    [],
  );

  // 빈 구간 보충: 구독이 받아들여진 뒤 + 내역이 있을 때. 그 뒤의 메시지는 토픽으로 오므로 사이에 빠지는 것이 없다
  useEffect(() => {
    if (readyCount === 0 || !hasData) {
      return undefined;
    }
    let canceled = false;
    async function fillGap() {
      const known = getLatestMessageId(queryClient.getQueryData(chatKeys.messages(roomId)));
      const collected = [];
      let cursor = null;
      for (;;) {
        const page = await fetchChatMessages(roomId, cursor);
        const { newer, done } = collectNewerMessages(page, known);
        collected.push(...newer);
        if (done || canceled) {
          break;
        }
        cursor = page.nextCursor;
      }
      if (!canceled) {
        queryClient.setQueryData(chatKeys.messages(roomId), (data) =>
          mergeMessages(data, collected),
        );
      }
    }
    fillGap().catch(() => {
      // 보충 실패는 화면을 막지 않는다. 다음 재연결 때 다시 보충한다
    });
    return () => {
      canceled = true;
    };
  }, [readyCount, hasData, queryClient, roomId]);

  // 보고 있는 방: 목록 이벤트가 안 읽은 수를 더하지 않게
  useEffect(() => {
    if (!isVisible) {
      return undefined;
    }
    setActiveChatRoom(roomId);
    return () => clearActiveChatRoom(roomId);
  }, [isVisible, roomId]);

  // 읽음: 화면이 보이고 연결돼 있을 때, 표시한 가장 최근 메시지가 이전에 보낸 값보다 크면
  const latestMessageId = getLatestMessageId(messages.data);
  useEffect(() => {
    if (!isVisible || status !== STOMP_STATUS.CONNECTED || latestMessageId === null) {
      return;
    }
    if (lastReadSentRef.current !== null && latestMessageId <= lastReadSentRef.current) {
      return;
    }
    if (publish(CHAT_DESTINATIONS.read(roomId), { lastMessageId: latestMessageId })) {
      lastReadSentRef.current = latestMessageId;
      applyReadToCaches(queryClient, roomId);
    }
  }, [isVisible, status, latestMessageId, queryClient, roomId]);

  const publishPending = useCallback(
    (clientMessageId, content) =>
      publish(CHAT_DESTINATIONS.send(roomId), { type: 'TEXT', content, clientMessageId }),
    [roomId],
  );

  const send = useCallback(
    (content) => {
      const clientMessageId = createClientMessageId();
      const sent = publishPending(clientMessageId, content);
      dispatch({
        type: PENDING_ACTION.ADD,
        clientMessageId,
        content,
        failed: !sent,
        errorMessage: sent ? null : DISCONNECTED_MESSAGE,
      });
    },
    [publishPending],
  );

  const retry = useCallback(
    (item) => {
      if (publishPending(item.clientMessageId, item.content)) {
        dispatch({ type: PENDING_ACTION.RETRY, clientMessageId: item.clientMessageId });
      } else {
        dispatch({
          type: PENDING_ACTION.FAIL,
          clientMessageId: item.clientMessageId,
          errorMessage: DISCONNECTED_MESSAGE,
        });
      }
    },
    [publishPending],
  );

  const remove = useCallback(
    (item) => dispatch({ type: PENDING_ACTION.REMOVE, clientMessageId: item.clientMessageId }),
    [],
  );

  return { messages, pending, status, send, retry, remove };
}
