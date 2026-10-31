import { describe, expect, it, vi } from 'vitest';

import {
  getCurrentCoords,
  LOCATE_GEOLOCATION_OPTIONS,
  LOCATION_ERROR,
  LocationError,
  TRADE_PLACE_GEOLOCATION_OPTIONS,
  VERIFY_GEOLOCATION_OPTIONS,
} from './geolocation';

function failingGeolocation(code) {
  return { getCurrentPosition: (_success, error) => error({ code }) };
}

describe('getCurrentCoords', () => {
  function succeeding() {
    return vi.fn((success) =>
      success({ coords: { latitude: 37.39, longitude: 126.63, accuracy: 20 } }),
    );
  }

  it('성공하면 { lat, lng }만. 기본은 찾기 옵션(1분 캐시)', async () => {
    const getCurrentPosition = succeeding();
    await expect(getCurrentCoords(undefined, { getCurrentPosition })).resolves.toEqual({
      lat: 37.39,
      lng: 126.63,
    });
    expect(getCurrentPosition.mock.calls[0][2]).toEqual({
      enableHighAccuracy: false,
      timeout: 10000,
      maximumAge: 60000,
    });
    expect(getCurrentPosition.mock.calls[0][2]).toBe(LOCATE_GEOLOCATION_OPTIONS);
  });

  it('인증 옵션은 캐시를 쓰지 않는다(maximumAge 0), 나머지는 같다', async () => {
    const getCurrentPosition = succeeding();
    await getCurrentCoords(VERIFY_GEOLOCATION_OPTIONS, { getCurrentPosition });
    expect(getCurrentPosition.mock.calls[0][2]).toEqual({
      enableHighAccuracy: false,
      timeout: 10000,
      maximumAge: 0,
    });
  });

  it.each([
    [1, LOCATION_ERROR.DENIED],
    [2, LOCATION_ERROR.UNAVAILABLE],
    [3, LOCATION_ERROR.TIMEOUT],
  ])('GeolocationPositionError %i → %s', async (code, expected) => {
    const error = await getCurrentCoords(undefined, failingGeolocation(code)).catch((e) => e);
    expect(error).toBeInstanceOf(LocationError);
    expect(error.code).toBe(expected);
    expect(error.status).toBeUndefined();
  });

  it('geolocation이 없으면 미지원', async () => {
    // node에는 navigator.geolocation이 없다(기본값)
    await expect(getCurrentCoords()).rejects.toMatchObject({
      code: LOCATION_ERROR.UNSUPPORTED,
    });
    await expect(getCurrentCoords(undefined, {})).rejects.toMatchObject({
      code: LOCATION_ERROR.UNSUPPORTED,
    });
  });

  it('모르는 오류 code는 위치 없음으로', async () => {
    await expect(getCurrentCoords(undefined, failingGeolocation(99))).rejects.toMatchObject({
      code: LOCATION_ERROR.UNAVAILABLE,
    });
  });
});

describe('용도별 옵션', () => {
  it('거래 장소만 고정밀이고 동네 위치·인증은 그대로다', () => {
    expect(TRADE_PLACE_GEOLOCATION_OPTIONS).toMatchObject({
      enableHighAccuracy: true,
      maximumAge: 0,
    });
    expect(LOCATE_GEOLOCATION_OPTIONS).toMatchObject({
      enableHighAccuracy: false,
      maximumAge: 60000,
    });
    expect(VERIFY_GEOLOCATION_OPTIONS).toMatchObject({ enableHighAccuracy: false, maximumAge: 0 });
  });
});
