import { describe, expect, it, vi } from 'vitest';

import { applyCodeFieldError, applyFieldErrors, createErrorMessageGetter } from './formErrors';

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

  it('인덱스·점이 붙은 키는 상위 필드에 붙이고, 정확히 맞는 키가 있으면 그것을 먼저 쓴다', () => {
    const setError = vi.fn();
    const result = applyFieldErrors(
      invalid([
        { field: 'availablePeriods[0].startDate', reason: '기간 오류' },
        { field: 'imageOrder[1]', reason: '순서 오류' },
        { field: 'place.address', reason: '주소 오류' },
        { field: 'checklist.A', reason: '정확히 맞음' },
        { field: 'unknown[0].x', reason: '무시' },
      ]),
      setError,
      {
        availablePeriods: 'availablePeriods',
        imageOrder: 'images',
        place: 'address',
        checklist: 'checklist.ALL',
        'checklist.A': 'checklistA',
      },
    );
    expect(result).toBe('availablePeriods');
    expect(setError.mock.calls.map(([name]) => name)).toEqual([
      'availablePeriods',
      'images',
      'address',
      'checklistA',
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

describe('applyCodeFieldError', () => {
  const getMessage = createErrorMessageGetter({
    MEMBER_EMAIL_DUPLICATED: '이미 가입된 이메일이에요',
  });

  it('codeFields에 있는 code면 그 필드에 문구를 붙이고 필드 이름을 돌려준다', () => {
    const setError = vi.fn();
    const result = applyCodeFieldError(
      { code: 'MEMBER_EMAIL_DUPLICATED' },
      setError,
      { MEMBER_EMAIL_DUPLICATED: 'email' },
      getMessage,
    );
    expect(result).toBe('email');
    expect(setError).toHaveBeenCalledWith('email', {
      type: 'server',
      message: '이미 가입된 이메일이에요',
    });
  });

  it('없는 code면 null, setError를 부르지 않는다', () => {
    const setError = vi.fn();
    expect(
      applyCodeFieldError(
        { code: 'X' },
        setError,
        { MEMBER_EMAIL_DUPLICATED: 'email' },
        getMessage,
      ),
    ).toBeNull();
    expect(setError).not.toHaveBeenCalled();
  });
});

describe('createErrorMessageGetter', () => {
  const getMessage = createErrorMessageGetter({ A: '에이' });

  it('표에 있으면 표 문구, 없으면 error.message, 그것도 없으면 기본 문구', () => {
    expect(getMessage({ code: 'A', message: '서버' })).toBe('에이');
    expect(getMessage({ code: 'B', message: '서버 문구' })).toBe('서버 문구');
    expect(getMessage(undefined)).toBe('일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요');
  });
});
