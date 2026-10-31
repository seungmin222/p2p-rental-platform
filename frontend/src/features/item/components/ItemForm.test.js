import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { useForm } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';

import { CategoryField } from './ItemForm';

const QUERY = {
  loading: { isPending: true, isFetching: true },
  failed: { isPending: false, isFetching: false, error: { message: '실패' }, refetch: () => {} },
  loaded: { isPending: false, isFetching: false, data: [{ categoryId: 1, name: '캠핑' }] },
};

/** 서버 렌더링으로 그려 HTML과 폼 메서드를 돌려준다 */
function renderCategory(query, categoryId = null) {
  let form;
  function Harness() {
    form = useForm({ defaultValues: { categoryId } });
    return createElement(CategoryField, { control: form.control, query });
  }
  return { html: renderToStaticMarkup(createElement(Harness)), form };
}

async function validate(form) {
  const onValid = vi.fn();
  const onInvalid = vi.fn();
  await form.handleSubmit(onValid, onInvalid)();
  return { onValid, onInvalid };
}

describe('CategoryField', () => {
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
