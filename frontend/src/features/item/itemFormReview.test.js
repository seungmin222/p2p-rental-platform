import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { useForm } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';

import {
  LOCATE_GEOLOCATION_OPTIONS,
  TRADE_PLACE_GEOLOCATION_OPTIONS,
  VERIFY_GEOLOCATION_OPTIONS,
} from '../region/geolocation';
import { CategoryField } from './components/ItemForm';
import TradePlaceField from './components/TradePlaceField';
import { ITEM_IMAGES, ITEM_MIN_PAID_AMOUNT } from './constants';
import { getDeletedMessage, getErrorMessage } from './errorMessages';
import {
  getCategoryStatus,
  getPaidAmountHint,
  getTradePlaceCoords,
  isSubmitBlocked,
  ITEM_IMAGE_HINT,
} from './itemForm';

/** 폼 하나를 서버 렌더링으로 그려 HTML과 폼 메서드를 돌려준다 */
function renderForm(defaultValues, renderField) {
  let form;
  function Harness() {
    form = useForm({ defaultValues });
    return renderField(form);
  }
  return { html: renderToStaticMarkup(createElement(Harness)), form };
}

const QUERY = {
  loading: { isPending: true, isFetching: true },
  retrying: { isPending: false, isFetching: true, error: {} },
  failed: { isPending: false, isFetching: false, error: { message: '실패' }, refetch: () => {} },
  loaded: { isPending: false, isFetching: false, data: [{ categoryId: 1, name: '캠핑' }] },
};

function renderCategory(query, categoryId = null) {
  return renderForm({ categoryId }, ({ control }) =>
    createElement(CategoryField, { control, query }),
  );
}

async function validate(form) {
  const onValid = vi.fn();
  const onInvalid = vi.fn();
  await form.handleSubmit(onValid, onInvalid)();
  return { onValid, onInvalid };
}

describe('거래 장소 위치', () => {
  it('고정밀·캐시 없이 요청하고 동네 인증용 옵션은 그대로다', async () => {
    const getCurrentPosition = vi.fn((success) =>
      success({ coords: { latitude: 37, longitude: 126 } }),
    );

    await expect(getTradePlaceCoords({ getCurrentPosition })).resolves.toEqual({
      lat: 37,
      lng: 126,
    });

    expect(getCurrentPosition.mock.calls[0][2]).toBe(TRADE_PLACE_GEOLOCATION_OPTIONS);
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

  it.each([false, true])('공개 안내를 보여 준다(수정 %s)', (isEdit) => {
    const { html } = renderForm({ address: '', latitude: null }, ({ control, register }) =>
      createElement(TradePlaceField, {
        control,
        register,
        setValue: () => {},
        isEdit,
        isLocating: false,
        onLocatingChange: () => {},
      }),
    );
    expect(html).toContain('가져온 위치는 거래 장소로 다른 사람에게 공개돼요');
    expect(html).not.toContain('좌표는 화면에 보이지 않아요');
  });
});

describe('카테고리 칸', () => {
  it('상태: 받은 목록이 있으면 재조회 실패여도 ready', () => {
    expect(getCategoryStatus(QUERY.loading)).toBe('loading');
    expect(getCategoryStatus(QUERY.retrying)).toBe('loading');
    expect(getCategoryStatus(QUERY.failed)).toBe('error');
    expect(getCategoryStatus(QUERY.loaded)).toBe('ready');
    expect(getCategoryStatus({ ...QUERY.loaded, error: {} })).toBe('ready');
  });

  it.each(['loading', 'failed', 'loaded'])('%s에도 선택하지 않으면 검증에 걸린다', async (key) => {
    const { form } = renderCategory(QUERY[key]);
    const { onValid, onInvalid } = await validate(form);
    expect(onValid).not.toHaveBeenCalled();
    expect(onInvalid.mock.calls[0][0].categoryId.message).toBe('카테고리를 선택해 주세요');
  });

  it('실패해도 이미 고른 값(수정)은 통과한다', async () => {
    const { form } = renderCategory(QUERY.failed, 3);
    const { onValid } = await validate(form);
    expect(onValid).toHaveBeenCalled();
  });

  it('실패하면 칩 자리에 오류와 재시도를 보여 준다', () => {
    const { html } = renderCategory(QUERY.failed);
    expect(html).toContain('카테고리를 불러오지 못했어요');
    expect(html).toContain('다시 시도');
    expect(html).not.toContain('캠핑');
  });

  it('받으면 칩을 보여 준다', () => {
    const { html } = renderCategory(QUERY.loaded);
    expect(html).toContain('캠핑');
    expect(html).not.toContain('불러오지 못했어요');
  });
});

describe('제출 막기', () => {
  it('카테고리 로딩 중이거나 위치를 가져오는 중이면 막는다', () => {
    expect(isSubmitBlocked({ categoryStatus: 'loading', isLocating: false })).toBe(true);
    expect(isSubmitBlocked({ categoryStatus: 'ready', isLocating: true })).toBe(true);
    expect(isSubmitBlocked({ categoryStatus: 'error', isLocating: true })).toBe(true);
  });

  it('실패는 막지 않는다(검증이 처리하고, 수정은 고른 값으로 저장할 수 있다)', () => {
    expect(isSubmitBlocked({ categoryStatus: 'error', isLocating: false })).toBe(false);
    expect(isSubmitBlocked({ categoryStatus: 'ready', isLocating: false })).toBe(false);
  });
});

describe('문구', () => {
  it('삭제 완료: 이미 지워졌으면 다른 문구', () => {
    expect(getDeletedMessage({ alreadyDeleted: false })).toBe('물품을 삭제했어요');
    expect(getDeletedMessage({ alreadyDeleted: true })).toBe('이미 삭제된 물품이에요');
  });

  it('수치는 constants 값과 같다', () => {
    const maxMb = ITEM_IMAGES.MAX_FILE_SIZE / (1024 * 1024);
    const minPaid = ITEM_MIN_PAID_AMOUNT.toLocaleString();
    expect(getPaidAmountHint('무료')).toBe(`0원(무료) 또는 ${minPaid}원 이상`);
    expect(ITEM_IMAGE_HINT).toContain(`장당 ${maxMb}MB`);
    expect(getErrorMessage({ code: 'IMAGE_SIZE_EXCEEDED' })).toContain(`${maxMb}MB`);
    expect(getErrorMessage({ code: 'IMAGE_COUNT_INVALID' })).toContain(
      `${ITEM_IMAGES.MIN_COUNT}~${ITEM_IMAGES.MAX_COUNT}장`,
    );
  });
});
