import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { useForm } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';

import { ItemTradePlace } from './ItemInfo';
import TradePlaceField from './TradePlaceField';

// 지도가 준비된(ready) 상태. 정적 렌더라 effect가 돌지 않아 실제 지도·마커(new maps.Map)는 만들지 않는다
vi.mock('../../../shared/hooks/useKakaoMap', () => ({
  useKakaoMap: () => ({ status: 'ready', kakao: { maps: {} } }),
}));

const ADDRESS = '인천 연수구 송도동 24-5';

function renderField(isEdit, defaultValues) {
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

describe('TradePlaceField — 지도 준비됨', () => {
  it('수정 화면은 저장된 좌표로 처음부터 지도를 그리고 주소 칸은 읽기 전용이다', () => {
    const html = renderField(true, { address: ADDRESS, latitude: 37.3925, longitude: 126.639 });
    expect(html).toContain('role="img"');
    expect(html).toContain('aria-label="거래 장소 지도"');
    expect(html).toContain('readOnly=""');
    expect(html).toContain('장소 검색');
    expect(html).not.toContain('장소가 바뀌었다면');
    expect(html).not.toContain('animate-pulse');
  });

  it('등록 화면은 좌표가 없으면 지도를 그리지 않는다', () => {
    const html = renderField(false, { address: '', latitude: null, longitude: null });
    expect(html).not.toContain('role="img"');
    expect(html).toContain('아직 장소를 고르지 않았어요');
    expect(html).toContain('장소 검색');
  });
});

describe('ItemTradePlace — 지도 준비됨', () => {
  it('좌표가 있으면 지도와 주소를 함께 보여 준다', () => {
    const html = renderToStaticMarkup(
      createElement(ItemTradePlace, { address: ADDRESS, latitude: 37.3925, longitude: 126.639 }),
    );
    expect(html).toContain('role="img"');
    expect(html).toContain(`aria-label="거래 장소 지도: ${ADDRESS}"`);
    expect(html).toContain(ADDRESS);
  });
});
