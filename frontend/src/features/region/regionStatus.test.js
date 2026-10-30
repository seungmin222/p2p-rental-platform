import { describe, expect, it } from 'vitest';

import { getVerifyStatus } from './regionStatus';

describe('getVerifyStatus', () => {
  it('isVerified true면 만료일(서울 날짜)까지 인증 완료', () => {
    expect(
      getVerifyStatus({
        isVerified: true,
        verifiedAt: '2026-10-20T09:30:00',
        verifyExpiresAt: '2026-11-19T09:30:00',
      }),
    ).toEqual({ code: 'VERIFIED', label: '인증 완료 · 11월 19일까지', tone: 'green' });
  });

  it('만료 시각이 서울 자정 직전이어도 서울 날짜로 표시한다(브라우저 시간대와 무관)', () => {
    expect(
      getVerifyStatus({
        isVerified: true,
        verifiedAt: '2026-10-01T23:59:00',
        verifyExpiresAt: '2026-10-31T23:59:00',
      }).label,
    ).toBe('인증 완료 · 10월 31일까지');
  });

  it('isVerified true인데 만료 시각을 해석할 수 없으면 날짜 없이', () => {
    expect(
      getVerifyStatus({ isVerified: true, verifiedAt: 'x', verifyExpiresAt: null }).label,
    ).toBe('인증 완료');
  });

  it('isVerified false이고 verifiedAt이 있으면 인증 만료', () => {
    expect(
      getVerifyStatus({
        isVerified: false,
        verifiedAt: '2026-09-01T10:00:00',
        verifyExpiresAt: '2026-10-01T10:00:00',
      }),
    ).toEqual({ code: 'EXPIRED', label: '인증 만료', tone: 'amber' });
  });

  it('서버 판정을 따른다: 만료일이 미래여도 isVerified false면 만료로 보인다', () => {
    expect(
      getVerifyStatus({
        isVerified: false,
        verifiedAt: '2099-01-01T10:00:00',
        verifyExpiresAt: '2099-01-31T10:00:00',
      }).code,
    ).toBe('EXPIRED');
  });

  it('verifiedAt이 null이면 미인증', () => {
    expect(getVerifyStatus({ isVerified: false, verifiedAt: null, verifyExpiresAt: null })).toEqual(
      {
        code: 'UNVERIFIED',
        label: '미인증',
        tone: 'gray',
      },
    );
  });
});
