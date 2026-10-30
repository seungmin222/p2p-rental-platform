// 홈·검색이 쓸 동네를 정한다(frontend/CLAUDE.md "홈 동네"). 화면 밖의 값(AT 유무, 내 정보 조회 결과, 임시 동네)만 받는 순수 함수.
//
// - 로그인(AT 있음): /api/members/me의 primaryRegion. 대표 동네의 인증이 만료여도 쓴다(item.md REQ-009는 인증을 요구하지 않는다).
//   임시 동네(rental.guestRegion)는 보지 않는다 — 서버의 내 동네와 섞이면 무엇이 내 동네인지 헷갈린다(feat/fe-region).
// - 비회원: rental.guestRegion.
// - 로그아웃 직후에는 내 정보 쿼리의 이전 데이터가 남아 있을 수 있어(clear()가 화면에 떠 있는 쿼리를 캐시에서만 뗀다)
//   isLoggedIn이 false면 me는 보지 않는다.

/**
 * @returns {{ status: 'loading' | 'error' | 'ready' | 'none', regionId?: number, regionName?: string }}
 *   none = 동네 없음(동네 설정 안내), loading·error = 로그인했는데 내 정보를 아직·못 받음
 */
export function resolveFeedRegion({ isLoggedIn, me, isMePending, isMeError, guestRegion }) {
  if (!isLoggedIn) {
    return guestRegion
      ? { status: 'ready', regionId: guestRegion.regionId, regionName: guestRegion.name }
      : { status: 'none' };
  }
  if (me?.primaryRegion) {
    return {
      status: 'ready',
      regionId: me.primaryRegion.regionId,
      regionName: me.primaryRegion.regionName,
    };
  }
  if (me) {
    return { status: 'none' };
  }
  if (isMeError) {
    return { status: 'error' };
  }
  return isMePending ? { status: 'loading' } : { status: 'error' };
}
