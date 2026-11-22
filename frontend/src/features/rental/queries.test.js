import { QueryClient, QueryObserver } from '@tanstack/query-core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { fetchInspections } from './api';
import { rentalInspectionsQueryOptions } from './queries';

// query-core는 window가 없으면 서버로 보고 refetchInterval 타이머를 걸지 않는다(테스트 환경은 node).
// import보다 먼저 실행되도록 vi.hoisted에서 빈 window를 둔다(이벤트 구독은 addEventListener가 없어 건너뛴다)
vi.hoisted(() => {
  globalThis.window ??= {};
});

vi.mock('./api', async (importOriginal) => ({
  ...(await importOriginal()),
  fetchInspections: vi.fn(),
}));

// 반납 5분 뒤(서울). AI 비교 결과를 기다리는 시간(ai.compare.processing-timeout-minutes) 안
const NOW = new Date('2026-11-20T12:00:00+09:00');

function inspections(aiResult) {
  return [
    {
      inspectionId: 1,
      phase: 'HANDOVER',
      imageUrls: ['/files/h0.jpg'],
      createdAt: '2026-11-18T10:00:00',
    },
    {
      inspectionId: 2,
      phase: 'RETURN',
      imageUrls: ['/files/r0.jpg'],
      createdAt: '2026-11-20T11:55:00',
      aiResult,
    },
  ];
}

const DONE = {
  status: 'DONE',
  analyzedAt: '2026-11-20T11:56:00',
  result: { changed: false, changeTypes: [], description: '변화 없음', regions: [] },
};

describe('검사 기록 재조회(REQ-040 AI 비교 대기)', () => {
  let queryClient;
  let unsubscribe;

  beforeEach(() => {
    vi.useFakeTimers({ now: NOW });
    vi.clearAllMocks();
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  afterEach(() => {
    unsubscribe?.();
    queryClient.clear();
    vi.useRealTimers();
  });

  function observe() {
    const observer = new QueryObserver(
      queryClient,
      rentalInspectionsQueryOptions(7, { enabled: true }),
    );
    unsubscribe = observer.subscribe(() => {});
  }

  it('준비 중(aiResult null)·비교 중(PROCESSING)이면 5초마다 다시 부르고, 결과가 오면 멈춘다', async () => {
    fetchInspections
      .mockResolvedValueOnce(inspections(null))
      .mockResolvedValueOnce(inspections({ status: 'PROCESSING' }))
      .mockResolvedValue(inspections(DONE));
    observe();
    await vi.advanceTimersByTimeAsync(0);
    expect(fetchInspections).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(5_000);
    expect(fetchInspections).toHaveBeenCalledTimes(2);

    await vi.advanceTimersByTimeAsync(5_000);
    expect(fetchInspections).toHaveBeenCalledTimes(3);

    // DONE이 왔으니 더 부르지 않는다
    await vi.advanceTimersByTimeAsync(30_000);
    expect(fetchInspections).toHaveBeenCalledTimes(3);
  });

  it('결과가 이미 있으면(FAILED 포함) 다시 부르지 않는다', async () => {
    fetchInspections.mockResolvedValue(inspections({ status: 'FAILED' }));
    observe();
    await vi.advanceTimersByTimeAsync(30_000);

    expect(fetchInspections).toHaveBeenCalledTimes(1);
  });
});
