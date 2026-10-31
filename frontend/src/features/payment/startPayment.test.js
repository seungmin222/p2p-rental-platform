import { QueryClient } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createPaymentOrder } from './api';
import { openTossPaymentWindow } from './tossPayment';
import {
  paymentKeys,
  releaseStartingPayments,
  startPayment,
  watchPageRestore,
} from './startPayment';
import { toast } from '../../shared/ui';

vi.mock('../../shared/ui', () => ({ toast: { show: vi.fn() } }));
vi.mock('../rental/queries', () => ({
  rentalKeys: { all: ['rentals'], detail: (id) => ['rentals', id] },
}));
vi.mock('./api', () => ({ createPaymentOrder: vi.fn() }));
vi.mock('./pendingPayment', () => ({ savePendingPayment: vi.fn() }));
vi.mock('./tossPayment', async (importOriginal) => ({
  ...(await importOriginal()),
  openTossPaymentWindow: vi.fn(),
}));

const flush = () => new Promise((resolve) => setTimeout(resolve));

function isStarting(queryClient, rentalId = 1) {
  return queryClient.isMutating({ mutationKey: paymentKeys.start(rentalId) }) > 0;
}

describe('startPayment', () => {
  let queryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient();
    createPaymentOrder.mockResolvedValue({ orderId: 'order-1' });
  });

  it('진행 중에 다시 불러도 주문·결제창은 한 번이다', async () => {
    openTossPaymentWindow.mockResolvedValue();
    await Promise.all([startPayment(queryClient, 1), startPayment(queryClient, 1)]);
    expect(createPaymentOrder).toHaveBeenCalledTimes(1);
    expect(openTossPaymentWindow).toHaveBeenCalledTimes(1);
  });

  it('창을 닫으면 두 번 불렀어도 토스트는 한 번이고 진행 상태가 풀린다', async () => {
    openTossPaymentWindow.mockRejectedValue({ code: 'USER_CANCEL' });
    const first = startPayment(queryClient, 1);
    const second = startPayment(queryClient, 1);
    expect(isStarting(queryClient)).toBe(true);
    await Promise.all([first, second]);
    expect(toast.show).toHaveBeenCalledTimes(1);
    expect(toast.show.mock.calls[0][0].tone).toBeUndefined();
    expect(isStarting(queryClient)).toBe(false);
  });

  it('주문 실패는 오류 토스트이고 결제창을 열지 않는다', async () => {
    createPaymentOrder.mockRejectedValue({ code: 'PAYMENT_EXPIRED' });
    await startPayment(queryClient, 1);
    expect(openTossPaymentWindow).not.toHaveBeenCalled();
    expect(toast.show).toHaveBeenCalledWith({
      message: '결제 시간이 지났어요. 다시 예약해 주세요',
      tone: 'danger',
    });
  });

  it('끝난 뒤에는 다시 시작할 수 있고, 다른 대여는 서로 막지 않는다', async () => {
    openTossPaymentWindow.mockResolvedValue();
    await startPayment(queryClient, 1);
    await startPayment(queryClient, 1);
    expect(createPaymentOrder).toHaveBeenCalledTimes(2);

    openTossPaymentWindow.mockReturnValue(new Promise(() => {}));
    startPayment(queryClient, 2);
    await flush();
    expect(isStarting(queryClient, 2)).toBe(true);
    expect(isStarting(queryClient, 3)).toBe(false);
  });
});

describe('진행 상태 풀기', () => {
  it('끝나지 않는 결제 시작을 지우면 다시 시작할 수 있다', async () => {
    const queryClient = new QueryClient();
    createPaymentOrder.mockResolvedValue({ orderId: 'order-1' });
    openTossPaymentWindow.mockReturnValue(new Promise(() => {}));
    startPayment(queryClient, 1);
    await flush();
    expect(isStarting(queryClient)).toBe(true);

    releaseStartingPayments(queryClient);
    expect(isStarting(queryClient)).toBe(false);
  });

  it.each([
    [true, false],
    [false, true],
  ])('pageshow persisted=%s면 진행 중=%s', async (persisted, stillStarting) => {
    const queryClient = new QueryClient();
    const target = new EventTarget();
    watchPageRestore(queryClient, target);
    createPaymentOrder.mockResolvedValue({ orderId: 'order-1' });
    openTossPaymentWindow.mockReturnValue(new Promise(() => {}));
    startPayment(queryClient, 1);
    await flush();

    target.dispatchEvent(Object.assign(new Event('pageshow'), { persisted }));
    expect(isStarting(queryClient)).toBe(stillStarting);
  });
});
