import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';

// 비회원 임시 동네(member.md REQ-004: 브라우저 localStorage에만, 서버 저장 없음). 로그인해도 서버에 올리지 않고 지우지도 않는다
// (올리는 API·명세가 없고, 로그아웃하면 다시 비회원 기준 동네로 쓰이므로). 로그인 사용자 화면에는 보여 주지 않는다.
export const GUEST_REGION_STORAGE_KEY = 'rental.guestRegion';

/** 저장할 값: RegionResponse의 { regionId, name, fullName }만(좌표 등 다른 필드는 버린다). 형식이 틀리면 null */
export function toGuestRegion(value) {
  if (
    !Number.isInteger(value?.regionId) ||
    typeof value.name !== 'string' ||
    typeof value.fullName !== 'string'
  ) {
    return null;
  }
  return { regionId: value.regionId, name: value.name, fullName: value.fullName };
}

/** storage는 테스트에서 바꿔 넣는다. 저장소를 못 쓰면(사생활 보호 모드 등) 메모리에만 있다 */
export function createGuestRegionStore(storage = createJSONStorage(() => localStorage)) {
  return create(
    persist(
      (set) => ({
        guestRegion: null,
        setGuestRegion: (region) => set({ guestRegion: toGuestRegion(region) }),
      }),
      {
        name: GUEST_REGION_STORAGE_KEY,
        version: 1,
        storage,
        partialize: ({ guestRegion }) => ({ guestRegion }),
        // 저장된 값을 손으로 바꿨거나 깨졌으면 버린다
        merge: (persisted, current) => ({
          ...current,
          guestRegion: toGuestRegion(persisted?.guestRegion),
        }),
      },
    ),
  );
}

export const useGuestRegionStore = createGuestRegionStore();
