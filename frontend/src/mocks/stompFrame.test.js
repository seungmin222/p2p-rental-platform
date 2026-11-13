import { describe, expect, it } from 'vitest';

import { parseFrames, serializeFrame } from './stompFrame';

describe('stompFrame', () => {
  it('쓰고 다시 읽으면 같다', () => {
    const data = serializeFrame('SEND', { destination: '/pub/chat-rooms/1' }, '{"a":1}');
    expect(parseFrames(data)).toEqual([
      { command: 'SEND', headers: { destination: '/pub/chat-rooms/1' }, body: '{"a":1}' },
    ]);
  });

  it('한 메시지의 여러 프레임과 하트비트', () => {
    const data = `\n${serializeFrame('SUBSCRIBE', { id: 'sub-0' })}\n${serializeFrame('DISCONNECT')}`;
    expect(parseFrames(data).map((frame) => frame.command)).toEqual(['SUBSCRIBE', 'DISCONNECT']);
    expect(parseFrames('\n')).toEqual([]);
  });

  it('헤더 값에 콜론이 있어도 첫 콜론에서 나눈다', () => {
    expect(parseFrames(serializeFrame('CONNECT', { host: 'a:1' }))[0].headers.host).toBe('a:1');
  });
});
