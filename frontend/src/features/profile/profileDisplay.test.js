import { describe, expect, it } from 'vitest';

import { formatJoinedMonth, getProfilePath } from './profileDisplay';

describe('formatJoinedMonth', () => {
  it('서버 시각의 연·월만 쓴다(시간대 변환 없이)', () => {
    expect(formatJoinedMonth('2026-01-31T23:59:59')).toBe('2026년 1월 가입');
  });

  it('없거나 형식이 다르면 null', () => {
    expect(formatJoinedMonth(null)).toBeNull();
    expect(formatJoinedMonth('어제')).toBeNull();
  });
});

describe('getProfilePath', () => {
  it('회원이면 /users/:memberId', () => {
    expect(getProfilePath({ memberId: 2 })).toBe('/users/2');
  });

  it('탈퇴 회원·id 없음은 링크 없음', () => {
    expect(getProfilePath({ memberId: 98, isWithdrawn: true })).toBeNull();
    expect(getProfilePath({ nickname: 'x' })).toBeNull();
    expect(getProfilePath(null)).toBeNull();
  });
});
