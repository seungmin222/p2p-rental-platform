import { describe, expect, it, vi } from 'vitest';

import {
  GEOLOCATION_OPTIONS,
  getCurrentCoords,
  LOCATION_ERROR,
  LocationError,
} from './geolocation';

function failingGeolocation(code) {
  return { getCurrentPosition: (_success, error) => error({ code }) };
}

describe('getCurrentCoords', () => {
  it('성공하면 { lat, lng }만, 정해진 옵션으로 부른다', async () => {
    const getCurrentPosition = vi.fn((success) =>
      success({ coords: { latitude: 37.39, longitude: 126.63, accuracy: 20 } }),
    );
    await expect(getCurrentCoords({ getCurrentPosition })).resolves.toEqual({
      lat: 37.39,
      lng: 126.63,
    });
    expect(getCurrentPosition.mock.calls[0][2]).toEqual({
      enableHighAccuracy: false,
      timeout: 10000,
      maximumAge: 60000,
    });
    expect(getCurrentPosition.mock.calls[0][2]).toBe(GEOLOCATION_OPTIONS);
  });

  it.each([
    [1, LOCATION_ERROR.DENIED],
    [2, LOCATION_ERROR.UNAVAILABLE],
    [3, LOCATION_ERROR.TIMEOUT],
  ])('GeolocationPositionError %i → %s', async (code, expected) => {
    const error = await getCurrentCoords(failingGeolocation(code)).catch((e) => e);
    expect(error).toBeInstanceOf(LocationError);
    expect(error.code).toBe(expected);
    expect(error.status).toBeUndefined();
  });

  it('geolocation이 없으면 미지원', async () => {
    await expect(getCurrentCoords(undefined)).rejects.toMatchObject({
      code: LOCATION_ERROR.UNSUPPORTED,
    });
    await expect(getCurrentCoords({})).rejects.toMatchObject({
      code: LOCATION_ERROR.UNSUPPORTED,
    });
  });

  it('모르는 오류 code는 위치 없음으로', async () => {
    await expect(getCurrentCoords(failingGeolocation(99))).rejects.toMatchObject({
      code: LOCATION_ERROR.UNAVAILABLE,
    });
  });
});
