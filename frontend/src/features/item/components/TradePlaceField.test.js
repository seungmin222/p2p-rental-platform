import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { useForm } from 'react-hook-form';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ItemTradePlace } from './ItemInfo';
import TradePlaceField from './TradePlaceField';

// 실제 카카오는 부르지 않는다. 키를 비우면 처음부터 실패 경로, 키가 있으면 첫 렌더는 불러오는 중(정적 렌더라 effect가 돌지 않음)
afterEach(() => {
  vi.unstubAllEnvs();
});

function renderField(isEdit, defaultValues = { address: '', latitude: null, longitude: null }) {
  function Harness() {
    const { control, register } = useForm({ defaultValues });
    return createElement(TradePlaceField, {
      control,
      register,
      setValue: () => {},
      isEdit,
      isLocating: false,
      onLocatingChange: () => {},
    });
  }
  return renderToStaticMarkup(createElement(Harness));
}

describe('TradePlaceField — 지도 키 없음(실패 경로)', () => {
  it.each([false, true])(
    '주소 직접 입력 + 현재 위치 흐름을 그대로 보여 준다(수정 %s)',
    (isEdit) => {
      vi.stubEnv('VITE_KAKAO_MAP_KEY', '');
      const html = renderField(isEdit);
      expect(html).toContain('가져온 위치는 거래 장소로 다른 사람에게 공개돼요');
      expect(html).toContain('현재 위치로 가져오기');
      expect(html).toContain('예: 인천 연수구 송도동 24-5');
      expect(html).not.toContain('readOnly');
      expect(html).not.toContain('장소 검색');
    },
  );

  it('수정이면 주소를 바꿀 때 위치를 다시 가져오라고 안내한다', () => {
    vi.stubEnv('VITE_KAKAO_MAP_KEY', '');
    expect(renderField(true)).toContain('장소가 바뀌었다면 현재 위치를 다시 가져와 주세요');
    expect(renderField(false)).not.toContain('장소가 바뀌었다면');
  });
});

describe('TradePlaceField — 지도를 불러오는 중', () => {
  it('주소 칸은 읽기 전용이고 직접 입력 안내가 없다', () => {
    vi.stubEnv('VITE_KAKAO_MAP_KEY', 'test-key');
    const html = renderField(true, { address: JIBUN_ADDRESS, latitude: 37.4, longitude: 126.6 });
    expect(html).toContain('readOnly=""');
    expect(html).toContain('장소를 고르면 채워져요');
    expect(html).not.toContain('장소가 바뀌었다면');
    expect(html).toContain('가져온 위치는 거래 장소로 다른 사람에게 공개돼요');
  });
});

const JIBUN_ADDRESS = '인천 연수구 송도동 24-5';

describe('ItemTradePlace', () => {
  it('지도 키가 없으면 주소 텍스트만', () => {
    vi.stubEnv('VITE_KAKAO_MAP_KEY', '');
    const html = renderToStaticMarkup(
      createElement(ItemTradePlace, { address: JIBUN_ADDRESS, latitude: 37.4, longitude: 126.6 }),
    );
    expect(html).toContain(JIBUN_ADDRESS);
    expect(html).not.toContain('animate-pulse');
    expect(html).not.toContain('거래 장소 지도');
  });

  it('좌표가 없으면 키가 있어도 지도 자리를 그리지 않는다', () => {
    vi.stubEnv('VITE_KAKAO_MAP_KEY', 'test-key');
    const html = renderToStaticMarkup(
      createElement(ItemTradePlace, { address: JIBUN_ADDRESS, latitude: null, longitude: null }),
    );
    expect(html).toContain(JIBUN_ADDRESS);
    expect(html).not.toContain('animate-pulse');
  });

  it('키와 좌표가 있으면 불러오는 동안 지도 자리를 잡는다', () => {
    vi.stubEnv('VITE_KAKAO_MAP_KEY', 'test-key');
    const html = renderToStaticMarkup(
      createElement(ItemTradePlace, { address: JIBUN_ADDRESS, latitude: 37.4, longitude: 126.6 }),
    );
    expect(html).toContain('animate-pulse');
    expect(html).toContain(JIBUN_ADDRESS);
  });
});
