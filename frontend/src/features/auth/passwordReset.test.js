import { describe, expect, it } from 'vitest';

import { isResetTokenError, readResetToken } from './passwordReset';

const TOKEN = 'q1w2e3r4t5y6u7i8o9p0a1s2d3f4g5h6j7k8l9z0x1c';

describe('readResetToken', () => {
  it('Base64URL 43자만 받는다', () => {
    expect(readResetToken(TOKEN)).toBe(TOKEN);
    expect(readResetToken(`${TOKEN.slice(0, 42)}_`)).toBe(`${TOKEN.slice(0, 42)}_`);
  });

  it('길이·문자가 다르거나 없으면 null', () => {
    expect(readResetToken(TOKEN.slice(0, 42))).toBeNull();
    expect(readResetToken(`${TOKEN}a`)).toBeNull();
    expect(readResetToken(`${TOKEN.slice(0, 42)}=`)).toBeNull();
    expect(readResetToken('rate:user@example.com')).toBeNull();
    expect(readResetToken(null)).toBeNull();
  });
});

describe('isResetTokenError', () => {
  it('AUTH_RESET_TOKEN_INVALID와 token 필드 오류는 토큰 문제', () => {
    expect(isResetTokenError({ code: 'AUTH_RESET_TOKEN_INVALID' })).toBe(true);
    expect(isResetTokenError({ code: 'COMMON_INVALID_INPUT', details: [{ field: 'token' }] })).toBe(
      true,
    );
  });

  it('새 비밀번호 오류·그 밖의 실패는 아니다', () => {
    expect(
      isResetTokenError({ code: 'COMMON_INVALID_INPUT', details: [{ field: 'newPassword' }] }),
    ).toBe(false);
    expect(isResetTokenError({ code: 'CLIENT_NETWORK_ERROR' })).toBe(false);
  });
});
