import { beforeEach, describe, expect, it, vi } from 'vitest';

// stompjs Client를 가짜로 바꿔 넘겨받은 설정(콜백)을 직접 부른다
const clients = [];
vi.mock('@stomp/stompjs', () => ({
  ReconnectionTimeMode: { EXPONENTIAL: 1 },
  Client: class {
    constructor(config) {
      this.config = config;
      this.active = false;
      this.connected = false;
      this.deactivate = vi.fn(async () => {
        this.active = false;
      });
      clients.push(this);
    }

    activate() {
      this.active = true;
    }
  },
}));

const authState = { accessToken: 'at' };
vi.mock('../api', () => ({
  reissueAccessToken: vi.fn(async () => {}),
  useAuthStore: { getState: () => authState, subscribe: () => () => {} },
}));

const AUTH_ERROR = { headers: { message: 'AUTH_UNAUTHORIZED' } };

async function start() {
  vi.resetModules();
  clients.length = 0;
  const { startStompSession } = await import('./stompClient');
  const onUnauthorized = vi.fn();
  startStompSession({ onUnauthorized });
  return { client: clients[0], onUnauthorized };
}

describe('stompClient CONNECT AUTH 거부', () => {
  beforeEach(() => {
    vi.stubGlobal('window', { location: { protocol: 'http:', host: 'localhost' } });
  });

  it('3회까지는 재연결을 이어 가고, 4번째 거부에서 unauthorized 핸들러를 부르고 끊는다', async () => {
    const { client, onUnauthorized } = await start();

    for (let i = 0; i < 3; i += 1) {
      client.config.onStompError(AUTH_ERROR);
    }
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(client.deactivate).not.toHaveBeenCalled();

    client.config.onStompError(AUTH_ERROR);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
    expect(client.deactivate).toHaveBeenCalledTimes(1);
  });

  it('연결에 성공하면 거부 횟수를 처음부터 센다', async () => {
    const { client, onUnauthorized } = await start();

    for (let i = 0; i < 3; i += 1) {
      client.config.onStompError(AUTH_ERROR);
    }
    client.config.onConnect();
    for (let i = 0; i < 3; i += 1) {
      client.config.onStompError(AUTH_ERROR);
    }
    expect(onUnauthorized).not.toHaveBeenCalled();
    expect(client.deactivate).not.toHaveBeenCalled();
  });

  it('AUTH가 아닌 ERROR는 세지 않는다', async () => {
    const { client, onUnauthorized } = await start();

    for (let i = 0; i < 5; i += 1) {
      client.config.onStompError({ headers: { message: 'CHAT_NOT_PARTICIPANT' } });
    }
    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
