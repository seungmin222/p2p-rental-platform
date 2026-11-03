// 카카오 주소 검색·장소 검색·좌표→주소 결과를 거래 장소 { address, lat, lng }로 바꾼다(REQ-030).
// 카카오 응답은 x = 경도, y = 위도이고 문자열이다. 저장할 주소는 도로명, 없으면 지번(장소 이름은 저장하지 않음).

export const PLACE_ERROR = {
  SEARCH_FAILED: 'CLIENT_PLACE_SEARCH_FAILED',
  ADDRESS_NOT_FOUND: 'CLIENT_PLACE_ADDRESS_NOT_FOUND',
};

/** 장소 검색·좌표→주소 실패. 분기는 LocationError처럼 code로 한다(문구는 errorMessages.js) */
export class PlaceError extends Error {
  constructor(code) {
    super(code);
    this.name = 'PlaceError';
    this.code = code;
  }
}

function toCoords(x, y) {
  const lat = Number(y);
  const lng = Number(x);
  if (
    x == null ||
    y == null ||
    x === '' ||
    y === '' ||
    !Number.isFinite(lat) ||
    !Number.isFinite(lng)
  ) {
    return null;
  }
  return { lat, lng };
}

function pickAddress(roadAddress, jibunAddress) {
  return (roadAddress?.trim() || jibunAddress?.trim()) ?? '';
}

/** Geocoder.addressSearch 결과 한 건 → { label, address, lat, lng } | null */
export function fromAddressResult(result) {
  const coords = toCoords(result?.x, result?.y);
  const address =
    pickAddress(result?.road_address?.address_name, result?.address?.address_name) ||
    (result?.address_name?.trim() ?? '');
  if (!coords || !address) {
    return null;
  }
  return { label: address, address, ...coords };
}

/** Places.keywordSearch 결과 한 건 → { label(장소 이름, 목록 표시용), address, lat, lng } | null */
export function fromKeywordResult(place) {
  const coords = toCoords(place?.x, place?.y);
  const address = pickAddress(place?.road_address_name, place?.address_name);
  if (!coords || !address) {
    return null;
  }
  return { label: place.place_name?.trim() || address, address, ...coords };
}

/** Geocoder.coord2Address 결과 → 주소 문자열 | null */
export function fromCoord2AddressResult(results) {
  const first = results?.[0];
  return pickAddress(first?.road_address?.address_name, first?.address?.address_name) || null;
}

function call(kakao, run) {
  const { Status } = kakao.maps.services;
  return new Promise((resolve, reject) => {
    run((data, status) => {
      if (status === Status.OK) {
        resolve(data ?? []);
      } else if (status === Status.ZERO_RESULT) {
        resolve([]);
      } else {
        reject(new PlaceError(PLACE_ERROR.SEARCH_FAILED));
      }
    });
  });
}

/**
 * 주소로 먼저 찾고, 없으면 장소 이름으로 찾는다. 좌표·주소가 없는 결과는 뺀다.
 * @returns {Promise<Array<{ label: string, address: string, lat: number, lng: number }>>}
 */
export async function searchPlaces(kakao, query) {
  const { services } = kakao.maps;
  const addresses = await call(kakao, (done) => new services.Geocoder().addressSearch(query, done));
  const fromAddresses = addresses.map(fromAddressResult).filter(Boolean);
  if (fromAddresses.length > 0) {
    return fromAddresses;
  }
  const places = await call(kakao, (done) => new services.Places().keywordSearch(query, done));
  return places.map(fromKeywordResult).filter(Boolean);
}

/** 좌표 → { address, lat, lng }. 주소가 없는 곳(바다 등)은 PlaceError(ADDRESS_NOT_FOUND) */
export async function reverseGeocode(kakao, { lat, lng }) {
  const { services } = kakao.maps;
  const results = await call(kakao, (done) =>
    new services.Geocoder().coord2Address(lng, lat, done),
  );
  const address = fromCoord2AddressResult(results);
  if (!address) {
    throw new PlaceError(PLACE_ERROR.ADDRESS_NOT_FOUND);
  }
  return { address, lat, lng };
}
