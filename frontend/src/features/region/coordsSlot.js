/**
 * "현재 위치로 찾기"로 받은 좌표를 추가 → 인증에 한 번 쓰기 위해 잠깐 들고 있는 칸(RegionFinder가 하나 둔다).
 * React state가 아니라 이 객체의 클로저에만 있어 Devtools·캐시에 보이지 않는다.
 *
 * 찾기 호출마다 토큰(증가 번호)을 받고, 현재 토큰의 호출만 좌표를 넣을 수 있다. clear는 좌표를 비우고 토큰을 올려
 * 그 전에 시작한 찾기의 늦은 응답을 무효로 만든다. 비우는 때(RegionFinder): 새 검색 입력, 검색 결과에서 고를 때,
 * 찾은 결과로 추가를 시도한 직후(성공·실패 무관), 언마운트.
 */
export function createCoordsSlot() {
  let token = 0;
  let coords = null;
  return {
    /** 새 찾기 호출 시작. 이전 좌표를 비우고 이 호출의 토큰을 돌려준다 */
    begin() {
      token += 1;
      coords = null;
      return token;
    },
    /** callToken이 현재 것일 때만 넣는다. 넣었으면 true */
    put(callToken, value) {
      if (callToken !== token) {
        return false;
      }
      coords = value;
      return true;
    },
    /** 이 토큰이 아직 현재 호출인지(결과 표시 판단) */
    isCurrent(callToken) {
      return callToken === token;
    },
    /** 좌표를 꺼내면서 비운다. 두 번째부터는 null */
    take() {
      const value = coords;
      coords = null;
      return value;
    },
    /** 좌표를 비우고 진행 중인 찾기를 무효로. 새 현재 토큰을 돌려준다 */
    clear() {
      token += 1;
      coords = null;
      return token;
    },
  };
}
