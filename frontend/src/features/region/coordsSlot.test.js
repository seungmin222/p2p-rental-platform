import { describe, expect, it } from 'vitest';

import { createCoordsSlot } from './coordsSlot';

const COORDS = { lat: 37.3925, lng: 126.639 };

function filledSlot() {
  const slot = createCoordsSlot();
  const token = slot.begin();
  slot.put(token, COORDS);
  return { slot, token };
}

describe('createCoordsSlot', () => {
  it('take는 한 번만 값을 준다', () => {
    const { slot } = filledSlot();
    expect(slot.take()).toEqual(COORDS);
    expect(slot.take()).toBeNull();
  });

  it('비워지는 때: 새 검색·검색 결과 선택·언마운트(clear)', () => {
    // RegionFinder는 세 경우 모두 clear를 부른다
    for (let i = 0; i < 3; i += 1) {
      const { slot } = filledSlot();
      slot.clear();
      expect(slot.take()).toBeNull();
    }
  });

  it('비워지는 때: 찾은 결과로 추가를 시도한 직후(take 뒤 clear)', () => {
    const { slot } = filledSlot();
    // 추가 성공 → 인증이 take, 실패 → 버리려고 take. 어느 쪽이든 RegionFinder가 끝에 clear
    slot.take();
    slot.clear();
    expect(slot.take()).toBeNull();
  });

  it('다시 찾으면(begin) 이전 좌표를 비운다', () => {
    const { slot } = filledSlot();
    slot.begin();
    expect(slot.take()).toBeNull();
  });

  it('이전 토큰의 좌표는 들어가지 않는다(늦게 온 응답)', () => {
    const slot = createCoordsSlot();
    const first = slot.begin();
    const second = slot.begin();
    expect(slot.put(first, { lat: 1, lng: 1 })).toBe(false);
    expect(slot.take()).toBeNull();
    expect(slot.put(second, COORDS)).toBe(true);
    expect(slot.take()).toEqual(COORDS);
  });

  it('clear 뒤에는 진행 중이던 찾기의 응답이 들어가지 않고 현재 호출도 아니다', () => {
    const slot = createCoordsSlot();
    const token = slot.begin();
    slot.clear();
    expect(slot.isCurrent(token)).toBe(false);
    expect(slot.put(token, COORDS)).toBe(false);
    expect(slot.take()).toBeNull();
  });

  it('현재 토큰이면 isCurrent', () => {
    const slot = createCoordsSlot();
    const token = slot.begin();
    expect(slot.isCurrent(token)).toBe(true);
  });
});
