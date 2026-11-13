// 앱 전체에서 STOMP 연결 하나(/ws). 로그인(AT 있음) 동안만 연결하고, 구독은 여기 등록해 두면 다시 연결될 때마다 다시 건다.
// 목적지·본문은 docs/api.md "STOMP 메시지". 서버는 AT 만료를 CONNECT 때만 본다(연결 중 재발급은 다시 연결하지 않는다).
import { Client, ReconnectionTimeMode } from '@stomp/stompjs';

import { reissueAccessToken, useAuthStore } from '../api';
import { useStompStatusStore } from './stompStatusStore';

// 서버 ERROR 프레임의 message 헤더(에러 코드). 이 코드면 CONNECT가 AT 만료·형식 오류로 거부된 것이다
const AUTH_ERROR_CODE = 'AUTH_UNAUTHORIZED';

// 재연결 간격: 1초부터 두 배씩, 30초까지(정책 값이 아닌 연결 설정)
const RECONNECT_DELAY_MS = 1_000;
const MAX_RECONNECT_DELAY_MS = 30_000;
// CONNECT가 AT 문제로 연속 거부될 때 재발급·재연결을 시도하는 횟수(정책 값이 아닌 연결 설정).
// 넘으면 로그인 화면으로 보낸다. 재발급은 되는데 서버가 계속 거부하는 경우 무한 반복을 막는다
const MAX_AUTH_RETRY = 3;

function toBrokerUrl() {
  const { protocol, host } = window.location;
  return `${protocol === 'https:' ? 'wss' : 'ws'}://${host}/ws`;
}

// 등록된 구독. key → { destination, handler, onReady, onError, stompSubscription }
const registry = new Map();
let subscriptionSequence = 0;
// 직전 CONNECT가 AT 문제로 거부됐으면 다음 연결 전에 재발급한다
let needsReissue = false;
// 연속 AUTH 거부 횟수. 연결에 성공하면 0으로
let authRejectCount = 0;
let onUnauthorized = () => {};
let client = null;

function subscribeEntry(key, entry) {
  // receipt를 달면 서버가 구독을 거부할 때 ERROR 프레임의 receipt-id로 어느 구독인지 알 수 있다
  const receipt = `sub-${key}`;
  client.watchForReceipt(receipt, () => entry.onReady?.());
  entry.stompSubscription = client.subscribe(
    entry.destination,
    (frame) => {
      let body;
      try {
        body = JSON.parse(frame.body);
      } catch {
        return;
      }
      entry.handler(body);
    },
    { receipt },
  );
}

function handleConnect() {
  needsReissue = false;
  authRejectCount = 0;
  for (const [key, entry] of registry) {
    subscribeEntry(key, entry);
  }
  useStompStatusStore.getState().setConnected();
}

function handleStompError(frame) {
  const code = frame.headers.message;
  if (code === AUTH_ERROR_CODE) {
    authRejectCount += 1;
    if (authRejectCount > MAX_AUTH_RETRY) {
      giveUpUnauthorized();
      return;
    }
    needsReissue = true;
  }
  // 구독 거부: 그 구독을 빼서 다시 연결할 때 같은 거부가 반복되지 않게 한다
  const receiptId = frame.headers['receipt-id'];
  const key = receiptId?.startsWith('sub-') ? receiptId.slice(4) : null;
  const entry = key && registry.get(key);
  if (entry) {
    registry.delete(key);
    entry.onError?.(code);
  }
}

// 재연결을 멈추고 REST와 같은 unauthorized 핸들러로 넘긴다
function giveUpUnauthorized() {
  disconnect();
  onUnauthorized();
}

/** 연결 직전: 거부됐던 AT면 먼저 재발급(single-flight), 최신 AT를 헤더에 넣는다 */
async function beforeConnect(stompClient) {
  useStompStatusStore.getState().setConnecting();
  if (needsReissue) {
    try {
      await reissueAccessToken();
      needsReissue = false;
    } catch {
      // 재발급 실패: AT가 지워져 아래 구독이 연결을 끊는다. REST와 같이 로그인 화면으로
      onUnauthorized();
      return;
    }
  }
  const { accessToken } = useAuthStore.getState();
  stompClient.connectHeaders = { Authorization: `Bearer ${accessToken}` };
}

function createClient() {
  return new Client({
    brokerURL: toBrokerUrl(),
    reconnectDelay: RECONNECT_DELAY_MS,
    maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    beforeConnect,
    onConnect: handleConnect,
    onStompError: handleStompError,
    onWebSocketClose: () => {
      if (client?.active) {
        useStompStatusStore.getState().setDisconnected();
      }
    },
  });
}

function connect() {
  if (!client) {
    client = createClient();
  }
  if (!client.active) {
    client.activate();
  }
}

function disconnect() {
  needsReissue = false;
  authRejectCount = 0;
  if (client?.active) {
    void client.deactivate();
  }
  useStompStatusStore.getState().setIdle();
}

/**
 * 로그인 상태에 연결을 묶는다. 앱 시작 때 한 번 부른다(app/router.jsx).
 * AT가 생기면 연결, 지워지면(로그아웃·재발급 실패) 끊는다. 재발급으로 AT만 바뀌면 그대로 둔다.
 * onUnauthorizedHandler: 연결 중 재발급이 실패했을 때(REST의 setUnauthorizedHandler와 같은 함수).
 */
export function startStompSession({ onUnauthorized: handler }) {
  onUnauthorized = handler;
  const sync = (accessToken) => (accessToken ? connect() : disconnect());
  sync(useAuthStore.getState().accessToken);
  useAuthStore.subscribe((state, previous) => {
    if (Boolean(state.accessToken) !== Boolean(previous.accessToken)) {
      sync(state.accessToken);
    }
  });
}

/**
 * 구독을 등록한다. 연결돼 있으면 바로, 아니면 연결될 때(다시 연결될 때마다) 건다. 해제 함수를 돌려준다.
 * - handler(body): JSON 본문
 * - onReady(): 서버가 구독을 받았을 때(연결마다 한 번). 재연결 뒤 보충 조회 시점
 * - onError(code): 서버가 구독을 거부했을 때(이 구독은 해제된다)
 */
export function subscribe(destination, handler, { onReady, onError } = {}) {
  subscriptionSequence += 1;
  const key = String(subscriptionSequence);
  const entry = { destination, handler, onReady, onError, stompSubscription: null };
  registry.set(key, entry);
  if (client?.connected) {
    subscribeEntry(key, entry);
  }
  return () => {
    registry.delete(key);
    if (client?.connected && entry.stompSubscription) {
      entry.stompSubscription.unsubscribe();
    }
  };
}

/** SEND. 연결돼 있지 않으면 보내지 않고 false */
export function publish(destination, body) {
  if (!client?.connected) {
    return false;
  }
  try {
    client.publish({ destination, body: JSON.stringify(body) });
    return true;
  } catch {
    return false;
  }
}
