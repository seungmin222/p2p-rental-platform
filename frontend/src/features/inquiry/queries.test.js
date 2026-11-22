import { MutationObserver, QueryClient } from '@tanstack/query-core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { paymentHistoryKeys } from '../payment/keys';
import { rentalKeys } from '../rental/queries';
import { createInquiry } from './api';
import { createInquiryMutationOptions, inquiryKeys } from './queries';

vi.mock('./api', () => ({ createInquiry: vi.fn(), fetchMyInquiries: vi.fn() }));

function invalidatedKeys(spy) {
  return spy.mock.calls.map(([filters]) => filters.queryKey);
}

describe('문의 접수 뮤테이션', () => {
  let queryClient;
  let invalidate;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient();
    invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  });

  function mutate(body) {
    const observer = new MutationObserver(queryClient, createInquiryMutationOptions(queryClient));
    return observer.mutate(body).catch(() => {});
  }

  it('일반 문의: 내 문의만 무효화한다', async () => {
    createInquiry.mockResolvedValue({ inquiryId: 1, type: 'GENERAL' });

    await mutate({ type: 'GENERAL', content: '문의' });

    expect(invalidatedKeys(invalidate)).toEqual([inquiryKeys.mine()]);
  });

  it('분쟁: 정산 내역도 무효화한다(정산 HELD)', async () => {
    createInquiry.mockResolvedValue({ inquiryId: 2, type: 'DISPUTE', rentalId: 10 });

    await mutate({ type: 'DISPUTE', rentalId: 10, content: '이의' });

    expect(invalidatedKeys(invalidate)).toEqual([
      inquiryKeys.mine(),
      paymentHistoryKeys.settlements(),
    ]);
  });

  it('결제 대기라 거절되면 대여 상세를 다시 불러온다', async () => {
    createInquiry.mockRejectedValue({ code: 'INQUIRY_DISPUTE_NOT_ALLOWED' });

    await mutate({ type: 'DISPUTE', rentalId: 10, content: '이의' });

    expect(invalidatedKeys(invalidate)).toEqual([rentalKeys.detail(10)]);
  });

  it('이미 열린 분쟁은 아무것도 무효화하지 않는다', async () => {
    createInquiry.mockRejectedValue({ code: 'INQUIRY_DISPUTE_ALREADY_OPEN' });

    await mutate({ type: 'DISPUTE', rentalId: 10, content: '이의' });

    expect(invalidate).not.toHaveBeenCalled();
  });
});
