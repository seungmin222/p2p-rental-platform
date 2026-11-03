import { describe, expect, it } from 'vitest';

import {
  fromAddressResult,
  fromCoord2AddressResult,
  fromKeywordResult,
  PLACE_ERROR,
  reverseGeocode,
  searchPlaces,
} from './kakaoPlace';

// 카카오 응답 모양: x = 경도, y = 위도, 둘 다 문자열
const ROAD = '인천 연수구 컨벤시아대로 165';
const JIBUN = '인천 연수구 송도동 24-5';

describe('fromKeywordResult', () => {
  it('장소 이름이 아니라 도로명 주소를 저장하고, x·y를 숫자 lng·lat으로 바꾼다', () => {
    const place = fromKeywordResult({
      place_name: '송도 센트럴파크',
      road_address_name: ROAD,
      address_name: JIBUN,
      x: '126.639',
      y: '37.3925',
    });
    expect(place).toEqual({ label: '송도 센트럴파크', address: ROAD, lat: 37.3925, lng: 126.639 });
    expect(typeof place.lat).toBe('number');
    expect(typeof place.lng).toBe('number');
  });

  it('도로명이 없으면(빈 문자열) 지번을 저장한다', () => {
    const place = fromKeywordResult({
      place_name: '공원',
      road_address_name: '',
      address_name: JIBUN,
      x: '126.6',
      y: '37.4',
    });
    expect(place.address).toBe(JIBUN);
  });

  it.each([
    ['좌표 없음', { address_name: JIBUN }],
    ['숫자가 아닌 좌표', { address_name: JIBUN, x: 'abc', y: '37.4' }],
    ['주소 없음', { place_name: '어딘가', x: '126.6', y: '37.4' }],
  ])('%s이면 null', (_, data) => {
    expect(fromKeywordResult(data)).toBeNull();
  });
});

describe('fromAddressResult', () => {
  it('도로명을 우선하고 lat = y, lng = x', () => {
    expect(
      fromAddressResult({
        address_name: JIBUN,
        x: '126.639',
        y: '37.3925',
        road_address: { address_name: ROAD },
        address: { address_name: JIBUN },
      }),
    ).toEqual({ label: ROAD, address: ROAD, lat: 37.3925, lng: 126.639 });
  });

  it('도로명이 없으면(null) 지번', () => {
    expect(
      fromAddressResult({
        address_name: JIBUN,
        x: '126.6',
        y: '37.4',
        road_address: null,
        address: { address_name: JIBUN },
      }).address,
    ).toBe(JIBUN);
  });
});

describe('fromCoord2AddressResult', () => {
  it('도로명, 없으면 지번, 둘 다 없으면 null', () => {
    expect(
      fromCoord2AddressResult([
        { road_address: { address_name: ROAD }, address: { address_name: JIBUN } },
      ]),
    ).toBe(ROAD);
    expect(
      fromCoord2AddressResult([{ road_address: null, address: { address_name: JIBUN } }]),
    ).toBe(JIBUN);
    expect(fromCoord2AddressResult([])).toBeNull();
  });
});

// services 대역. 각 함수는 [data, status]를 돌려주는 응답표로 동작한다
function createFakeKakao({
  address = [[], 'ZERO_RESULT'],
  keyword = [[], 'ZERO_RESULT'],
  coord,
} = {}) {
  const calls = [];
  return {
    calls,
    maps: {
      services: {
        Status: { OK: 'OK', ZERO_RESULT: 'ZERO_RESULT', ERROR: 'ERROR' },
        Geocoder: class {
          addressSearch(query, done) {
            calls.push(['addressSearch', query]);
            done(...address);
          }
          coord2Address(lng, lat, done) {
            calls.push(['coord2Address', lng, lat]);
            done(...coord);
          }
        },
        Places: class {
          keywordSearch(query, done) {
            calls.push(['keywordSearch', query]);
            done(...keyword);
          }
        },
      },
    },
  };
}

describe('searchPlaces', () => {
  it('주소 검색 결과가 있으면 장소 검색을 부르지 않는다', async () => {
    const kakao = createFakeKakao({
      address: [[{ address_name: JIBUN, x: '126.6', y: '37.4', road_address: null }], 'OK'],
    });
    const found = await searchPlaces(kakao, '송도동 24-5');
    expect(found).toEqual([{ label: JIBUN, address: JIBUN, lat: 37.4, lng: 126.6 }]);
    expect(kakao.calls.map(([name]) => name)).toEqual(['addressSearch']);
  });

  it('주소 결과가 없으면 장소 이름으로 찾는다', async () => {
    const kakao = createFakeKakao({
      keyword: [
        [{ place_name: '센트럴파크', road_address_name: ROAD, x: '126.6', y: '37.4' }],
        'OK',
      ],
    });
    const found = await searchPlaces(kakao, '센트럴파크');
    expect(found[0].address).toBe(ROAD);
    expect(kakao.calls.map(([name]) => name)).toEqual(['addressSearch', 'keywordSearch']);
  });

  it('둘 다 없으면 빈 배열, ERROR면 PlaceError', async () => {
    await expect(searchPlaces(createFakeKakao(), '없는곳')).resolves.toEqual([]);
    await expect(
      searchPlaces(createFakeKakao({ address: [null, 'ERROR'] }), '송도'),
    ).rejects.toMatchObject({ code: PLACE_ERROR.SEARCH_FAILED });
  });
});

describe('reverseGeocode', () => {
  it('coord2Address에 (경도, 위도) 순서로 넘기고 주소와 좌표를 돌려준다', async () => {
    const kakao = createFakeKakao({
      coord: [[{ road_address: null, address: { address_name: JIBUN } }], 'OK'],
    });
    await expect(reverseGeocode(kakao, { lat: 37.4, lng: 126.6 })).resolves.toEqual({
      address: JIBUN,
      lat: 37.4,
      lng: 126.6,
    });
    expect(kakao.calls).toEqual([['coord2Address', 126.6, 37.4]]);
  });

  it('주소가 없는 곳이면 ADDRESS_NOT_FOUND', async () => {
    const kakao = createFakeKakao({ coord: [[], 'ZERO_RESULT'] });
    await expect(reverseGeocode(kakao, { lat: 37, lng: 126 })).rejects.toMatchObject({
      code: PLACE_ERROR.ADDRESS_NOT_FOUND,
    });
  });
});
