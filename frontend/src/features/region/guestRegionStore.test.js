import { describe, expect, it } from 'vitest';
import { createJSONStorage } from 'zustand/middleware';

import {
  createGuestRegionStore,
  GUEST_REGION_STORAGE_KEY,
  toGuestRegion,
} from './guestRegionStore';

const REGION = { regionId: 1101, name: '송도1동', fullName: '인천광역시 연수구 송도1동' };

function memoryStorage(initial = {}) {
  const items = new Map(Object.entries(initial));
  return {
    items,
    getItem: (key) => items.get(key) ?? null,
    setItem: (key, value) => items.set(key, value),
    removeItem: (key) => items.delete(key),
  };
}

function createStore(storage) {
  return createGuestRegionStore(createJSONStorage(() => storage));
}

describe('toGuestRegion', () => {
  it('regionId·name·fullName만 남긴다(좌표 등은 버림)', () => {
    expect(toGuestRegion({ ...REGION, lat: 37.39, lng: 126.63 })).toEqual(REGION);
  });

  it('형식이 틀리면 null', () => {
    expect(toGuestRegion(null)).toBeNull();
    expect(toGuestRegion({ ...REGION, regionId: '1101' })).toBeNull();
    expect(toGuestRegion({ regionId: 1101, name: '송도1동' })).toBeNull();
  });
});

describe('guestRegionStore', () => {
  it('고른 동네를 정해진 키에 저장한다(좌표 없이)', () => {
    const storage = memoryStorage();
    const store = createStore(storage);
    store.getState().setGuestRegion({ ...REGION, lat: 37.39 });

    expect(store.getState().guestRegion).toEqual(REGION);
    const saved = JSON.parse(storage.items.get(GUEST_REGION_STORAGE_KEY));
    expect(saved).toEqual({ state: { guestRegion: REGION }, version: 1 });
  });

  it('새로 만들면(새로고침) 저장된 동네를 불러온다', () => {
    const storage = memoryStorage();
    createStore(storage).getState().setGuestRegion(REGION);
    expect(createStore(storage).getState().guestRegion).toEqual(REGION);
  });

  it('저장된 값이 깨졌으면 없음으로', () => {
    const storage = memoryStorage({
      [GUEST_REGION_STORAGE_KEY]: JSON.stringify({
        state: { guestRegion: { regionId: 'x', lat: 1 } },
        version: 1,
      }),
    });
    expect(createStore(storage).getState().guestRegion).toBeNull();
  });

  it('저장된 값에 다른 필드가 있으면 버린다', () => {
    const storage = memoryStorage({
      [GUEST_REGION_STORAGE_KEY]: JSON.stringify({
        state: { guestRegion: { ...REGION, lat: 37.39, lng: 126.63 } },
        version: 1,
      }),
    });
    expect(createStore(storage).getState().guestRegion).toEqual(REGION);
  });
});
