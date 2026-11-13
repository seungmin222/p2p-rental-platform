import { describe, expect, it } from 'vitest';

import { PENDING_STATUS } from './constants';
import { PENDING_ACTION, pendingReducer, validateMessage } from './pendingMessages';

function add(state, clientMessageId, extra = {}) {
  return pendingReducer(state, {
    type: PENDING_ACTION.ADD,
    clientMessageId,
    content: 'hi',
    ...extra,
  });
}

describe('pendingReducer', () => {
  it('추가 → 서버 메시지가 오면 제거', () => {
    const state = add([], 'a');
    expect(state[0].status).toBe(PENDING_STATUS.SENDING);
    expect(pendingReducer(state, { type: PENDING_ACTION.CONFIRM, clientMessageId: 'a' })).toEqual(
      [],
    );
  });

  it('연결이 끊기면 보내는 중인 것만 실패로', () => {
    const state = add(add([], 'a'), 'b', { failed: true, errorMessage: 'x' });
    const next = pendingReducer(state, {
      type: PENDING_ACTION.FAIL_ALL_SENDING,
      errorMessage: '연결 끊김',
    });
    expect(next.map((p) => [p.status, p.errorMessage])).toEqual([
      [PENDING_STATUS.FAILED, '연결 끊김'],
      [PENDING_STATUS.FAILED, 'x'],
    ]);
  });

  it('보내는 중이 없으면 같은 배열', () => {
    const state = add([], 'a', { failed: true });
    expect(pendingReducer(state, { type: PENDING_ACTION.FAIL_ALL_SENDING })).toBe(state);
  });

  it('실패 → 재전송하면 보내는 중', () => {
    const state = pendingReducer(add([], 'a'), {
      type: PENDING_ACTION.FAIL,
      clientMessageId: 'a',
      errorMessage: 'e',
    });
    const retried = pendingReducer(state, { type: PENDING_ACTION.RETRY, clientMessageId: 'a' });
    expect(retried[0]).toMatchObject({ status: PENDING_STATUS.SENDING, errorMessage: null });
  });
});

describe('validateMessage', () => {
  it('공백만이면 empty, 코드 포인트로 max 초과면 tooLong', () => {
    expect(validateMessage('  \n ', 3)).toBe('empty');
    expect(validateMessage('😀😀😀', 3)).toBeNull();
    expect(validateMessage('😀😀😀😀', 3)).toBe('tooLong');
  });
});
