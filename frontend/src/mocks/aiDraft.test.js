import { describe, expect, it } from 'vitest';

import { buildMockDraft } from './aiDraft';
import { calculateDepositGuide } from './itemWriteLogic';

describe('buildMockDraft', () => {
  it('기본은 DONE, 보증금 범위는 deposit-guide 목과 같은 계산', () => {
    const draft = buildMockDraft('tent.jpg');
    expect(draft.status).toBe('DONE');
    expect({ minDeposit: draft.minDeposit, maxDeposit: draft.maxDeposit }).toEqual(
      calculateDepositGuide(draft.categoryId, draft.itemPrice),
    );
  });

  it.each([
    ['FAIL.png', 'FAILED'],
    ['limit.jpg', 'LIMIT_EXCEEDED'],
  ])('%s → %s, 나머지 필드는 null', (name, status) => {
    const draft = buildMockDraft(name);
    expect(draft.status).toBe(status);
    expect(draft.categoryId).toBeNull();
    expect(draft.minDeposit).toBeNull();
  });

  it('etc → ETC + 후보 이름', () => {
    expect(buildMockDraft('etc.jpg')).toMatchObject({
      categoryCode: 'ETC',
      suggestedCategoryName: '악기',
    });
  });

  it('partial → 원가가 없으면 보증금 범위도 null', () => {
    expect(buildMockDraft('partial.jpg')).toMatchObject({
      status: 'DONE',
      itemPrice: null,
      minDeposit: null,
      maxDeposit: null,
    });
  });
});
