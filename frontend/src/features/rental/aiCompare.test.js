import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';

import {
  AI_NONE_REASON,
  AI_VIEW,
  getAiCompareHeadline,
  getAiCompareView,
  isAiComparePending,
} from './aiCompare';
import { rentalKeys } from './queries';

// 2026-11-20T12:00:00 서울
const NOW = new Date('2026-11-20T12:00:00+09:00').getTime();

function returnRecord(overrides = {}) {
  return {
    inspectionId: 2,
    phase: 'RETURN',
    imageUrls: ['/files/r0.jpg', '/files/r1.jpg'],
    createdAt: '2026-11-20T11:55:00',
    aiResult: null,
    ...overrides,
  };
}

const DONE_CHANGED = {
  status: 'DONE',
  analyzedAt: '2026-11-20T11:56:00',
  result: {
    changed: true,
    changeTypes: ['SCRATCH', 'MISSING_PART'],
    confidence: 0.82,
    description: '오른쪽 모서리에 긁힘이 보여요.',
    regions: [
      { imageIndex: 1, note: '오른쪽 아래 모서리' },
      { imageIndex: 5, note: '범위 밖' },
    ],
  },
};

describe('getAiCompareView', () => {
  it('반납 기록이 없으면 그리지 않는다', () => {
    expect(getAiCompareView(null, NOW)).toBeNull();
    expect(getAiCompareView(undefined, NOW)).toBeNull();
  });

  it('미반납 신고 기록(사진 없음, aiResult null)은 그리지 않는다', () => {
    expect(getAiCompareView(returnRecord({ imageUrls: [] }), NOW)).toBeNull();
  });

  it('aiResult null이고 반납 후 10분 안이면 비교 준비 중(#59)', () => {
    expect(getAiCompareView(returnRecord(), NOW)).toEqual({ kind: AI_VIEW.WAITING });
    expect(getAiCompareView(returnRecord({ createdAt: '2026-11-20T11:50:01' }), NOW).kind).toBe(
      AI_VIEW.WAITING,
    );
  });

  it('aiResult null이고 10분이 지났으면 AI 결과 없음', () => {
    expect(getAiCompareView(returnRecord({ createdAt: '2026-11-20T11:50:00' }), NOW)).toEqual({
      kind: AI_VIEW.NONE,
      reason: AI_NONE_REASON.NOT_RUN,
    });
  });

  it('작성 시각이 미래(시계 차이)면 준비 중으로 본다', () => {
    expect(getAiCompareView(returnRecord({ createdAt: '2026-11-20T12:01:00' }), NOW).kind).toBe(
      AI_VIEW.WAITING,
    );
  });

  it('PROCESSING', () => {
    const view = getAiCompareView(
      returnRecord({ aiResult: { status: 'PROCESSING', analyzedAt: null, result: null } }),
      NOW,
    );
    expect(view).toEqual({ kind: AI_VIEW.PROCESSING });
  });

  it('DONE: 유형 라벨, 위치는 반납 사진에 대응하고 범위 밖은 사진 없이', () => {
    const view = getAiCompareView(returnRecord({ aiResult: DONE_CHANGED }), NOW);
    expect(view).toEqual({
      kind: AI_VIEW.DONE,
      changed: true,
      changeTypes: [
        { code: 'SCRATCH', label: '긁힘' },
        { code: 'MISSING_PART', label: '부품 없음' },
      ],
      description: '오른쪽 모서리에 긁힘이 보여요.',
      regions: [
        { imageIndex: 1, imageUrl: '/files/r1.jpg', note: '오른쪽 아래 모서리' },
        { imageIndex: 5, imageUrl: null, note: '범위 밖' },
      ],
    });
    // 확신도는 보이지 않는다
    expect(view).not.toHaveProperty('confidence');
  });

  it('DONE 변화 없음, 모르는 유형 코드는 코드 그대로', () => {
    const view = getAiCompareView(
      returnRecord({
        aiResult: {
          status: 'DONE',
          result: {
            changed: false,
            changeTypes: ['NEW_TYPE'],
            description: '차이 없음',
            regions: [],
          },
        },
      }),
      NOW,
    );
    expect(view.changed).toBe(false);
    expect(view.changeTypes).toEqual([{ code: 'NEW_TYPE', label: 'NEW_TYPE' }]);
    expect(getAiCompareHeadline(view)).toBe('AI가 눈에 띄는 변화를 찾지 못했어요');
  });

  it('FAILED·LIMIT_EXCEEDED·결과 없는 DONE은 AI 결과 없음', () => {
    const failed = getAiCompareView(returnRecord({ aiResult: { status: 'FAILED' } }), NOW);
    const limit = getAiCompareView(returnRecord({ aiResult: { status: 'LIMIT_EXCEEDED' } }), NOW);
    const empty = getAiCompareView(
      returnRecord({ aiResult: { status: 'DONE', result: null } }),
      NOW,
    );
    expect(failed).toEqual({ kind: AI_VIEW.NONE, reason: AI_NONE_REASON.FAILED });
    expect(limit).toEqual({ kind: AI_VIEW.NONE, reason: AI_NONE_REASON.LIMIT_EXCEEDED });
    expect(empty).toEqual({ kind: AI_VIEW.NONE, reason: AI_NONE_REASON.FAILED });
    expect(getAiCompareHeadline(limit)).toBe('AI 결과 없음');
  });
});

describe('isAiComparePending', () => {
  it('준비 중·비교 중일 때만 true', () => {
    expect(isAiComparePending(returnRecord(), NOW)).toBe(true);
    expect(isAiComparePending(returnRecord({ aiResult: { status: 'PROCESSING' } }), NOW)).toBe(
      true,
    );
    expect(isAiComparePending(returnRecord({ aiResult: DONE_CHANGED }), NOW)).toBe(false);
    expect(isAiComparePending(returnRecord({ aiResult: { status: 'FAILED' } }), NOW)).toBe(false);
    expect(isAiComparePending(returnRecord({ createdAt: '2026-11-20T11:00:00' }), NOW)).toBe(false);
    expect(isAiComparePending(returnRecord({ imageUrls: [] }), NOW)).toBe(false);
    expect(isAiComparePending(null, NOW)).toBe(false);
  });
});

describe('AI_COMPARE_DONE 알림 수신', () => {
  it('알림이 무효화하는 상세 키 아래에 inspections가 있어 함께 다시 받는다', async () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(rentalKeys.inspections(9), []);
    // features/notification/queries.js: targetType RENTAL → rentalKeys.detail(targetId) 무효화
    await queryClient.invalidateQueries({ queryKey: rentalKeys.detail(9) });
    expect(queryClient.getQueryState(rentalKeys.inspections(9)).isInvalidated).toBe(true);
  });
});
