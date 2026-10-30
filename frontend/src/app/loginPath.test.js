import { describe, expect, it } from 'vitest';

import { getSafeRedirect, toLoginPath, withRedirect } from './loginPath';

describe('getSafeRedirect', () => {
  it.each(['/', '/me', '/me/rentals?tab=DONE', '/items/101#top', '/loginx'])(
    '허용: %s',
    (value) => {
      expect(getSafeRedirect(value)).toBe(value);
    },
  );

  it.each([
    ['없음', null],
    ['빈 문자열', ''],
    ['상대 경로', 'me'],
    ['절대 URL', 'https://evil.com'],
    ['프로토콜 상대', '//evil.com'],
    ['역슬래시', '/\\evil.com'],
    ['탭', '/\t/evil.com'],
    ['줄바꿈', '/\n/evil.com'],
    ['javascript:', 'javascript:alert(1)'],
    ['로그인 화면', '/login'],
    ['로그인 화면(쿼리)', '/login?redirect=/me'],
    ['로그인 화면(끝 슬래시)', '/login/'],
    ['가입 화면', '/signup'],
  ])('거부: %s', (_, value) => {
    expect(getSafeRedirect(value)).toBeNull();
  });
});

describe('getSafeRedirect 디코드 순서', () => {
  it('인코딩된 채로 들어온 값은 /로 시작하지 않으면 거부', () => {
    expect(getSafeRedirect('%2F%2Fevil.com')).toBeNull();
  });

  it("'/%2F/evil.com'은 앱 안 경로로 허용(브라우저가 //로 풀지 않는다)", () => {
    expect(getSafeRedirect('/%2F/evil.com')).toBe('/%2F/evil.com');
  });

  it('쿼리에서 디코드된 값(URLSearchParams.get)으로 검사하면 //evil.com이라 거부', () => {
    const value = new URLSearchParams('redirect=%2F%2Fevil.com').get('redirect');
    expect(value).toBe('//evil.com');
    expect(getSafeRedirect(value)).toBeNull();
  });
});

describe('주소 만들기', () => {
  it('toLoginPath는 돌아올 경로를 인코딩한다', () => {
    expect(toLoginPath('/me/rentals?tab=DONE')).toBe(
      '/login?redirect=%2Fme%2Frentals%3Ftab%3DDONE',
    );
  });

  it('withRedirect는 redirect가 없으면 경로만', () => {
    expect(withRedirect('/signup', '/me')).toBe('/signup?redirect=%2Fme');
    expect(withRedirect('/signup', null)).toBe('/signup');
  });
});
