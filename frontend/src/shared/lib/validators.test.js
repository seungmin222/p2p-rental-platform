import { describe, expect, it } from 'vitest';

import {
  countCodePoints,
  PASSWORD_RULE_MESSAGE,
  validateEmail,
  validateNickname,
  validatePassword,
  validateRequired,
} from './validators';

// 테스트용 범위(policies.md member.* 값과 같게 넘긴다)
const PASSWORD = { min: 8, max: 20 };
const NICKNAME = { min: 2, max: 10 };
const LENGTH_MESSAGE = (range) => `${range.min}자 이상 ${range.max}자 이하로 입력해 주세요`;

describe('countCodePoints', () => {
  it('이모지(서로게이트 쌍)를 1자로 센다', () => {
    expect(countCodePoints('😀')).toBe(1);
    expect('😀'.length).toBe(2);
    expect(countCodePoints('동네😀주민')).toBe(5);
    expect(countCodePoints(undefined)).toBe(0);
  });
});

describe('validatePassword', () => {
  it.each([
    ['a1!' + 'b'.repeat(4), false], // 7자
    ['a1!' + 'b'.repeat(5), true], // 8자
    ['a1!' + 'b'.repeat(17), true], // 20자
    ['a1!' + 'b'.repeat(18), false], // 21자
  ])('글자 수 경계 %s → %s', (value, isValid) => {
    expect(validatePassword(value, PASSWORD)).toBe(isValid ? true : LENGTH_MESSAGE(PASSWORD));
  });

  it.each([
    ['기호 없음', 'rental1234'],
    ['숫자 없음', 'rental!!!!'],
    ['영문 없음', '1234!!!!'],
    ['공백', 'rental 123!'],
    ['한글', 'rental123!가'],
    ['전각 기호', 'rental123！'],
  ])('구성 규칙 위반: %s', (_, value) => {
    expect(validatePassword(value, PASSWORD)).toBe(PASSWORD_RULE_MESSAGE);
  });

  it.each(['rental123!', 'A1~aaaaa', 'a1[bbbbb', 'a1`bbbbb', 'a1{bbbbb', 'a1/bbbbb', 'a1:bbbbb'])(
    'ASCII 기호 구간 통과: %s',
    (value) => {
      expect(validatePassword(value, PASSWORD)).toBe(true);
    },
  );

  it('빈 값', () => {
    expect(validatePassword('', PASSWORD)).toBe('비밀번호를 입력해 주세요');
  });
});

describe('validateNickname', () => {
  it.each([
    ['가', false],
    ['가나', true],
    ['가'.repeat(10), true],
    ['가'.repeat(11), false],
    ['😀😀', true], // UTF-16으로는 4
    ['가'.repeat(9) + '😀', true], // UTF-16으로는 11
    ['가'.repeat(10) + '😀', false],
  ])('%s → %s', (value, isValid) => {
    expect(validateNickname(value, NICKNAME)).toBe(isValid ? true : LENGTH_MESSAGE(NICKNAME));
  });

  it('공백만이면 빈 값', () => {
    expect(validateNickname('   ', NICKNAME)).toBe('닉네임을 입력해 주세요');
  });
});

describe('validateEmail', () => {
  it('빈 값·형식·최대 글자 수', () => {
    expect(validateEmail(' ', { max: 100 })).toBe('이메일을 입력해 주세요');
    expect(validateEmail('user', { max: 100 })).toBe('이메일 형식을 확인해 주세요');
    expect(validateEmail('us er@example.com', { max: 100 })).toBe('이메일 형식을 확인해 주세요');
    expect(validateEmail('user@example.com', { max: 100 })).toBe(true);
    // 백엔드 @Email처럼 도메인의 점은 요구하지 않는다
    expect(validateEmail('user@localhost', { max: 100 })).toBe(true);
  });

  // 백엔드 @Email은 local part를 64자까지만 받으므로 local 64자 + 도메인으로 길이를 맞춘다
  it('100자는 통과, 101자는 실패', () => {
    const local = 'a'.repeat(64);
    const at100 = `${local}@${'b'.repeat(23)}.example.com`;
    const at101 = `${local}@${'b'.repeat(24)}.example.com`;
    expect(at100).toHaveLength(100);
    expect(at101).toHaveLength(101);
    expect(validateEmail(at100, { max: 100 })).toBe(true);
    expect(validateEmail(at101, { max: 100 })).toBe('100자 이하로 입력해 주세요');
  });
});

describe('validateRequired', () => {
  it('빈 값·공백만이면 문구, 아니면 true(형식은 보지 않는다)', () => {
    expect(validateRequired('', '입력해 주세요')).toBe('입력해 주세요');
    expect(validateRequired('  ', '입력해 주세요')).toBe('입력해 주세요');
    expect(validateRequired('아무 값', '입력해 주세요')).toBe(true);
  });
});
