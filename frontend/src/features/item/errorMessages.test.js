import { describe, expect, it } from 'vitest';

import { LocationError } from '../region/geolocation';
import { ITEM_IMAGE_MAX_MB, ITEM_IMAGES, LOCATION_NO_RESPONSE } from './constants';
import { getDeletedMessage, getErrorMessage } from './errorMessages';

describe('물품 문구', () => {
  it('삭제 완료: 이미 지워졌으면 다른 문구', () => {
    expect(getDeletedMessage({ alreadyDeleted: false })).toBe('물품을 삭제했어요');
    expect(getDeletedMessage({ alreadyDeleted: true })).toBe('이미 삭제된 물품이에요');
  });

  it('사진 오류의 수치는 constants 값과 같다', () => {
    expect(getErrorMessage({ code: 'IMAGE_SIZE_EXCEEDED' })).toContain(`${ITEM_IMAGE_MAX_MB}MB`);
    expect(getErrorMessage({ code: 'IMAGE_COUNT_INVALID' })).toContain(
      `${ITEM_IMAGES.MIN_COUNT}~${ITEM_IMAGES.MAX_COUNT}장`,
    );
  });

  it('위치 상한 초과는 "위치를 가져오지 못했어요" 안내', () => {
    expect(getErrorMessage(new LocationError(LOCATION_NO_RESPONSE))).toContain(
      '위치를 가져오지 못했어요',
    );
  });
});
