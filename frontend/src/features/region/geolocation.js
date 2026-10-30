// 브라우저 현재 위치(REQ-004). 좌표는 이 함수의 반환값으로만 넘기고 어디에도 보관하지 않는다(queries.js 좌표 규칙).

// 데스크톱에서 고정밀은 느리거나 실패하고 행정동 판정에는 일반 정확도면 충분하다. 1분 안의 캐시 위치는 그대로 받는다
export const GEOLOCATION_OPTIONS = { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 };

// 위치 실패의 프론트 전용 코드(CLIENT_* 규칙). 문구는 errorMessages.js
export const LOCATION_ERROR = {
  DENIED: 'CLIENT_LOCATION_DENIED',
  UNAVAILABLE: 'CLIENT_LOCATION_UNAVAILABLE',
  TIMEOUT: 'CLIENT_LOCATION_TIMEOUT',
  UNSUPPORTED: 'CLIENT_LOCATION_UNSUPPORTED',
};

// GeolocationPositionError.code: 1 PERMISSION_DENIED, 2 POSITION_UNAVAILABLE, 3 TIMEOUT
const POSITION_ERROR_CODES = {
  1: LOCATION_ERROR.DENIED,
  2: LOCATION_ERROR.UNAVAILABLE,
  3: LOCATION_ERROR.TIMEOUT,
};

/**
 * 위치 실패. ApiError가 아니라 status가 없고 axios를 거치지 않는다(재발급·재시도 대상이 아님).
 * 분기는 ApiError처럼 code로 한다.
 */
export class LocationError extends Error {
  constructor(code) {
    super(code);
    this.name = 'LocationError';
    this.code = code;
  }
}

/**
 * 현재 좌표 { lat, lng }. 실패는 LocationError(권한 거부·위치 없음·시간 초과·미지원).
 * geolocation은 테스트에서 바꿔 넣는다. 보안 컨텍스트가 아니면(https·localhost가 아님) navigator.geolocation이 없어 미지원이다.
 */
export function getCurrentCoords(geolocation = globalThis.navigator?.geolocation) {
  return new Promise((resolve, reject) => {
    if (!geolocation?.getCurrentPosition) {
      reject(new LocationError(LOCATION_ERROR.UNSUPPORTED));
      return;
    }
    geolocation.getCurrentPosition(
      ({ coords }) => resolve({ lat: coords.latitude, lng: coords.longitude }),
      (error) =>
        reject(new LocationError(POSITION_ERROR_CODES[error?.code] ?? LOCATION_ERROR.UNAVAILABLE)),
      GEOLOCATION_OPTIONS,
    );
  });
}
