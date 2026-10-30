import { describe, expect, it } from 'vitest';

import { resolveFeedRegion } from './homeRegion';

const GUEST = { regionId: 1106, name: '청학동', fullName: '인천광역시 연수구 청학동' };
const ME = {
  memberId: 1,
  primaryRegion: { regionId: 1101, regionName: '송도1동', isVerified: false },
};

function resolve(patch) {
  return resolveFeedRegion({
    isLoggedIn: false,
    me: undefined,
    isMePending: false,
    isMeError: false,
    guestRegion: null,
    ...patch,
  });
}

describe('resolveFeedRegion', () => {
  describe('비회원', () => {
    it('임시 동네가 있으면 그 동네', () => {
      expect(resolve({ guestRegion: GUEST })).toEqual({
        status: 'ready',
        regionId: 1106,
        regionName: '청학동',
      });
    });

    it('임시 동네가 없으면 동네 없음', () => {
      expect(resolve({})).toEqual({ status: 'none' });
    });

    it('내 정보 쿼리에 이전 데이터가 남아 있어도 보지 않는다(로그아웃 직후)', () => {
      expect(resolve({ me: ME, guestRegion: GUEST })).toMatchObject({ regionId: 1106 });
      expect(resolve({ me: ME })).toEqual({ status: 'none' });
    });
  });

  describe('로그인', () => {
    it('대표 동네가 있으면 그 동네 — 인증이 만료·미인증이어도', () => {
      expect(resolve({ isLoggedIn: true, me: ME })).toEqual({
        status: 'ready',
        regionId: 1101,
        regionName: '송도1동',
      });
    });

    it('임시 동네는 보지 않는다', () => {
      expect(resolve({ isLoggedIn: true, me: ME, guestRegion: GUEST })).toMatchObject({
        regionId: 1101,
      });
      expect(
        resolve({ isLoggedIn: true, me: { memberId: 3, primaryRegion: null }, guestRegion: GUEST }),
      ).toEqual({ status: 'none' });
    });

    it('대표 동네가 없으면(primaryRegion null) 동네 없음', () => {
      expect(resolve({ isLoggedIn: true, me: { memberId: 3, primaryRegion: null } })).toEqual({
        status: 'none',
      });
    });

    it('내 정보를 받는 중이면 loading, 실패하면 error', () => {
      expect(resolve({ isLoggedIn: true, isMePending: true })).toEqual({ status: 'loading' });
      expect(resolve({ isLoggedIn: true, isMeError: true })).toEqual({ status: 'error' });
    });

    it('무효화된 내 정보를 다시 받는 중이면 이전 동네 대신 loading', () => {
      expect(resolve({ isLoggedIn: true, me: ME, isMeOutdated: true })).toEqual({
        status: 'loading',
      });
    });

    it('이미 받은 데이터가 있으면 재조회가 실패해도 그 동네를 쓴다', () => {
      expect(resolve({ isLoggedIn: true, me: ME, isMeError: true })).toMatchObject({
        status: 'ready',
      });
    });
  });
});
