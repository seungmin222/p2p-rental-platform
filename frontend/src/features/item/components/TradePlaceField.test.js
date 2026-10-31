import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { useForm } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import TradePlaceField from './TradePlaceField';

function renderField(isEdit) {
  function Harness() {
    const { control, register } = useForm({ defaultValues: { address: '', latitude: null } });
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

describe('TradePlaceField', () => {
  it.each([false, true])('공개 안내를 보여 준다(수정 %s)', (isEdit) => {
    const html = renderField(isEdit);
    expect(html).toContain('가져온 위치는 거래 장소로 다른 사람에게 공개돼요');
    expect(html).not.toContain('좌표는 화면에 보이지 않아요');
  });
});
