import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { useForm } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import AiDraftField from './AiDraftField';

function render(images) {
  function Harness() {
    const form = useForm({ defaultValues: { images } });
    return createElement(AiDraftField, {
      control: form.control,
      initialValues: { images: [] },
      getValues: form.getValues,
      setValue: form.setValue,
      getFieldState: form.getFieldState,
    });
  }
  return renderToStaticMarkup(
    createElement(QueryClientProvider, { client: new QueryClient() }, createElement(Harness)),
  );
}

describe('AiDraftField', () => {
  it('새 사진이 없으면 버튼을 막는다', () => {
    const html = render([{ id: 1, url: '/old.jpg' }]);
    expect(html).toContain('AI로 채우기');
    expect(html).toMatch(/<button[^>]* disabled=""/);
  });

  it('새 사진이 있으면 누를 수 있다', () => {
    const html = render([{ id: 'a', file: { name: 'tent.jpg' } }]);
    expect(html).not.toMatch(/<button[^>]* disabled=""/);
  });
});
