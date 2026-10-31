import { describe, expect, it, vi } from 'vitest';

import { CONFIRM_ACTION, getConfirmOutcome, parseSuccessParams, runOnce } from './paymentResult';

const params = (query) => new URLSearchParams(query);

describe('parseSuccessParams', () => {
  it('세 값이 있으면 승인 요청 본문', () => {
    expect(parseSuccessParams(params('paymentKey=k&orderId=o&amount=35000'))).toEqual({
      paymentKey: 'k',
      orderId: 'o',
      amount: 35000,
    });
  });

  it.each([
    ['paymentKey 없음', 'orderId=o&amount=1'],
    ['orderId 없음', 'paymentKey=k&amount=1'],
    ['amount 없음', 'paymentKey=k&orderId=o'],
    ['amount 0', 'paymentKey=k&orderId=o&amount=0'],
    ['amount 음수', 'paymentKey=k&orderId=o&amount=-1'],
    ['amount 소수', 'paymentKey=k&orderId=o&amount=1.5'],
    ['amount 너무 큼', 'paymentKey=k&orderId=o&amount=99999999999999999999'],
  ])('%s면 null', (_, query) => {
    expect(parseSuccessParams(params(query))).toBeNull();
  });
});

describe('runOnce', () => {
  it('진행 중이면 요청을 한 번만 보내고 같은 결과를 돌려준다', async () => {
    const request = vi.fn(() => Promise.resolve('done'));
    const [first, second] = await Promise.all([runOnce('dup', request), runOnce('dup', request)]);
    expect(request).toHaveBeenCalledTimes(1);
    expect([first, second]).toEqual(['done', 'done']);
  });

  it('끝난 뒤에는 다시 보낼 수 있다(다시 확인)', async () => {
    const request = vi.fn(() => Promise.reject(new Error('fail')));
    await expect(runOnce('again', request)).rejects.toThrow('fail');
    await expect(runOnce('again', request)).rejects.toThrow('fail');
    expect(request).toHaveBeenCalledTimes(2);
  });
});

describe('getConfirmOutcome', () => {
  it.each([
    ['PAYMENT_APPROVAL_FAILED', CONFIRM_ACTION.TO_DETAIL],
    ['PAYMENT_RESULT_UNKNOWN', CONFIRM_ACTION.RETRY],
    ['PAYMENT_AMOUNT_MISMATCH', CONFIRM_ACTION.TO_DETAIL],
    ['PAYMENT_CANCELED_AFTER_EXPIRY', CONFIRM_ACTION.TO_DETAIL],
    ['PAYMENT_EXPIRED', CONFIRM_ACTION.TO_DETAIL],
    ['PAYMENT_INVALID_STATUS', CONFIRM_ACTION.TO_DETAIL],
    ['PAYMENT_RENTER_ONLY', CONFIRM_ACTION.TO_HOME],
    ['PAYMENT_NOT_FOUND', CONFIRM_ACTION.TO_HOME],
    ['COMMON_INVALID_INPUT', CONFIRM_ACTION.TO_HOME],
  ])('%s → %s', (code, action) => {
    expect(getConfirmOutcome({ code }).action).toBe(action);
  });

  it('결과를 모르는 오류(네트워크 등)는 다시 확인', () => {
    const outcome = getConfirmOutcome({ code: 'CLIENT_NETWORK_ERROR', message: '연결 오류' });
    expect(outcome.action).toBe(CONFIRM_ACTION.RETRY);
    expect(outcome.message).toBe('연결 오류');
  });
});
