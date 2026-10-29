import { describe, expect, it, vi } from 'vitest';

import { applyFieldErrors } from './errorMessages';

const invalid = (details) => ({ code: 'COMMON_INVALID_INPUT', details });

describe('applyFieldErrors', () => {
  it('fieldMap에 있는 details를 모두 붙이고 첫 필드 이름을 돌려준다(포커스는 옮기지 않음)', () => {
    const setError = vi.fn();
    const result = applyFieldErrors(
      invalid([
        { field: 'unknown', reason: '무시' },
        { field: 'checklist', reason: '형식 오류' },
        { field: 'note', reason: '메모 필요' },
      ]),
      setError,
      { checklist: 'checklist.NO_EXTERIOR_DAMAGE', note: 'note' },
    );
    expect(result).toBe('checklist.NO_EXTERIOR_DAMAGE');
    expect(setError.mock.calls).toEqual([
      ['checklist.NO_EXTERIOR_DAMAGE', { type: 'server', message: '형식 오류' }],
      ['note', { type: 'server', message: '메모 필요' }],
    ]);
  });

  it('붙일 필드가 없거나 COMMON_INVALID_INPUT이 아니면 null', () => {
    const setError = vi.fn();
    expect(applyFieldErrors(invalid([{ field: 'x', reason: 'y' }]), setError, {})).toBeNull();
    expect(applyFieldErrors(invalid(undefined), setError, { x: 'x' })).toBeNull();
    expect(
      applyFieldErrors({ code: 'RENTAL_INVALID_TRANSITION', details: [] }, setError, {}),
    ).toBeNull();
    expect(setError).not.toHaveBeenCalled();
  });
});
