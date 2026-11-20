import { describe, expect, it } from 'vitest';

import { parseMarkdownBlocks } from './markdown';

describe('parseMarkdownBlocks', () => {
  it('제목·문단·목록·인용을 블록으로 나눈다', () => {
    const source = [
      '> 초안입니다.',
      '',
      '# 이용약관',
      '',
      '## 제1조 (목적)',
      '첫 줄',
      '둘째 줄',
      '',
      '1. 하나',
      '2. 둘',
      '- 가',
      '* 나',
    ].join('\n');

    expect(parseMarkdownBlocks(source)).toEqual([
      { type: 'quote', text: '초안입니다.' },
      { type: 'heading', level: 1, text: '이용약관' },
      { type: 'heading', level: 2, text: '제1조 (목적)' },
      { type: 'paragraph', text: '첫 줄 둘째 줄' },
      { type: 'list', ordered: true, items: ['하나', '둘'] },
      { type: 'list', ordered: false, items: ['가', '나'] },
    ]);
  });

  it('제목 바로 뒤 문단과 CRLF 줄바꿈도 읽는다', () => {
    expect(parseMarkdownBlocks('## 제목\r\n본문')).toEqual([
      { type: 'heading', level: 2, text: '제목' },
      { type: 'paragraph', text: '본문' },
    ]);
  });

  it('지원하지 않는 문법과 HTML은 글자 그대로 둔다', () => {
    expect(parseMarkdownBlocks('**굵게** <b>x</b>')).toEqual([
      { type: 'paragraph', text: '**굵게** <b>x</b>' },
    ]);
  });

  it('빈 값이면 빈 목록', () => {
    expect(parseMarkdownBlocks(null)).toEqual([]);
    expect(parseMarkdownBlocks('')).toEqual([]);
  });
});
