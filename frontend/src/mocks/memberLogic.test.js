import { describe, expect, it } from 'vitest';

import {
  findAccount,
  findSignupConflict,
  readBearerToken,
  toMemberResponse,
  toMockAccessToken,
  toVerification,
  validateLogin,
  validateSignup,
} from './memberLogic';
import { createMemberDb, MOCK_REGION_MASTER } from './memberSeed';

const VALID = { email: 'new@example.com', password: 'rental123!', nickname: '새이웃' };

describe('validateLogin', () => {
  it('빈 값만 400 details(형식은 보지 않는다)', () => {
    expect(validateLogin({ email: ' ', password: '' })).toEqual([
      { field: 'email', reason: '공백일 수 없습니다' },
      { field: 'password', reason: '공백일 수 없습니다' },
    ]);
    expect(validateLogin({ email: 'not-an-email', password: 'x' })).toEqual([]);
    expect(validateLogin(null)).toHaveLength(2);
  });
});

describe('validateSignup', () => {
  it('통과하면 빈 배열', () => {
    expect(validateSignup(VALID)).toEqual([]);
  });

  it('필드마다 첫 위반 하나', () => {
    expect(validateSignup({ email: 'bad', password: 'short1!', nickname: '가' })).toEqual([
      { field: 'email', reason: '올바른 형식의 이메일 주소여야 합니다' },
      { field: 'password', reason: '8자 이상 20자 이하로 입력해 주세요.' },
      { field: 'nickname', reason: '2자 이상 10자 이하로 입력해 주세요.' },
    ]);
  });

  it('비밀번호 구성은 백엔드 문구, 닉네임은 코드 포인트로 센다', () => {
    expect(validateSignup({ ...VALID, password: 'rental1234' })).toEqual([
      {
        field: 'password',
        reason: '공백·한글 없이 영문, 숫자, 기호를 각각 1개 이상 포함해 주세요.',
      },
    ]);
    expect(validateSignup({ ...VALID, nickname: '가'.repeat(9) + '😀' })).toEqual([]);
    expect(validateSignup({ ...VALID, nickname: '가'.repeat(10) + '😀' })).toHaveLength(1);
  });

  it('이메일 100자 통과, 101자는 글자 수 문구(local part 64자 + 도메인)', () => {
    const local = 'a'.repeat(64);
    expect(validateSignup({ ...VALID, email: `${local}@${'b'.repeat(23)}.example.com` })).toEqual(
      [],
    );
    expect(validateSignup({ ...VALID, email: `${local}@${'b'.repeat(24)}.example.com` })).toEqual([
      { field: 'email', reason: '100자 이하로 입력해 주세요.' },
    ]);
  });

  it('local part 65자는 @Email 형식 문구', () => {
    expect(validateSignup({ ...VALID, email: `${'a'.repeat(65)}@example.com` })).toEqual([
      { field: 'email', reason: '올바른 형식의 이메일 주소여야 합니다' },
    ]);
  });
});

describe('가입 중복·로그인 계정', () => {
  const { members } = createMemberDb();

  it('이메일 중복이 닉네임 중복보다 먼저', () => {
    expect(findSignupConflict(members, { email: 'user1@example.com', nickname: '캠핑조아' })).toBe(
      'MEMBER_EMAIL_DUPLICATED',
    );
    expect(findSignupConflict(members, { email: 'x@example.com', nickname: '캠핑조아' })).toBe(
      'MEMBER_NICKNAME_DUPLICATED',
    );
    expect(findSignupConflict(members, VALID)).toBeNull();
  });

  it('이메일·비밀번호가 모두 맞아야 계정', () => {
    expect(
      findAccount(members, { email: 'user3@example.com', password: 'rental123!' })?.memberId,
    ).toBe(3);
    expect(findAccount(members, { email: 'user3@example.com', password: 'wrong' })).toBeNull();
    expect(findAccount(members, { email: 'none@example.com', password: 'rental123!' })).toBeNull();
  });
});

describe('목 AT', () => {
  it('회원·순번이 들어가 발급마다 다르다', () => {
    expect(toMockAccessToken(1, 1)).toBe('mock-at.1.1');
    expect(toMockAccessToken(1, 2)).not.toBe(toMockAccessToken(1, 1));
  });

  it('Bearer 헤더에서 토큰만', () => {
    expect(readBearerToken('Bearer mock-at.1.3')).toBe('mock-at.1.3');
    expect(readBearerToken('mock-at.1.3')).toBeNull();
    expect(readBearerToken(null)).toBeNull();
  });
});

describe('toVerification', () => {
  const now = new Date('2026-10-30T12:00:00+09:00');

  it('verifiedAt + 30일 전이면 유효, 만료 시각을 서버 형식으로', () => {
    expect(toVerification('2026-10-25T10:00:00', now)).toEqual({
      isVerified: true,
      verifiedAt: '2026-10-25T10:00:00',
      verifyExpiresAt: '2026-11-24T10:00:00',
    });
  });

  it('만료 시각 정각부터 만료', () => {
    expect(toVerification('2026-09-30T12:00:00', now).isVerified).toBe(false);
    expect(toVerification('2026-09-30T12:00:01', now).isVerified).toBe(true);
  });

  it('인증한 적 없으면 모두 null·false', () => {
    expect(toVerification(null, now)).toEqual({
      isVerified: false,
      verifiedAt: null,
      verifyExpiresAt: null,
    });
  });
});

describe('toMemberResponse', () => {
  const db = createMemberDb();

  it('회원 1: 대표 동네 송도1동, 인증 유효', () => {
    const me = toMemberResponse(db, 1, MOCK_REGION_MASTER);
    expect(Object.keys(me).sort()).toEqual(
      [
        'createdAt',
        'email',
        'memberId',
        'nickname',
        'primaryRegion',
        'profileImageUrl',
        'role',
      ].sort(),
    );
    expect(me.primaryRegion).toMatchObject({
      memberRegionId: 1,
      regionId: 1101,
      regionName: '송도1동',
      isVerified: true,
    });
  });

  it('회원 3: 동네가 없으면 primaryRegion null', () => {
    expect(toMemberResponse(db, 3, MOCK_REGION_MASTER).primaryRegion).toBeNull();
  });

  it('회원 4: 대표 동네 미인증', () => {
    expect(toMemberResponse(db, 4, MOCK_REGION_MASTER).primaryRegion).toMatchObject({
      regionName: '동춘1동',
      isVerified: false,
      verifiedAt: null,
      verifyExpiresAt: null,
    });
  });
});
