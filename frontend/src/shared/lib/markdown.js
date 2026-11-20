// 약관 본문(마크다운) → 블록 목록. 라이브러리 대신 약관 문서가 쓰는 문법만 읽는다(notes: feat/fe-member-extra).
// 지원: 제목(#~###), 문단(연속한 줄은 한 문단), 목록(- 또는 *, 1.), 인용(>). 그 밖의 문법(굵게·링크·표)은 글자 그대로 둔다.
// 화면은 블록을 React 요소로 그린다(innerHTML을 쓰지 않는다).

const HEADING = /^(#{1,3})\s+(.*)$/;
const BULLET = /^[-*]\s+(.*)$/;
const ORDERED = /^\d+\.\s+(.*)$/;
const QUOTE = /^>\s?(.*)$/;

/**
 * @returns {Array<{type: 'heading', level: number, text: string}
 *   | {type: 'paragraph', text: string}
 *   | {type: 'list', ordered: boolean, items: string[]}
 *   | {type: 'quote', text: string}>}
 */
export function parseMarkdownBlocks(source) {
  const blocks = [];
  let current = null;

  function close() {
    if (current) {
      blocks.push(current);
      current = null;
    }
  }

  for (const rawLine of String(source ?? '').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line === '') {
      close();
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      close();
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2].trim() });
      continue;
    }
    const bullet = BULLET.exec(line);
    const ordered = bullet ? null : ORDERED.exec(line);
    if (bullet || ordered) {
      const isOrdered = ordered !== null;
      if (current?.type !== 'list' || current.ordered !== isOrdered) {
        close();
        current = { type: 'list', ordered: isOrdered, items: [] };
      }
      current.items.push((bullet ?? ordered)[1].trim());
      continue;
    }
    const quote = QUOTE.exec(line);
    if (quote) {
      if (current?.type !== 'quote') {
        close();
        current = { type: 'quote', text: quote[1] };
      } else {
        current.text += ` ${quote[1]}`;
      }
      continue;
    }
    if (current?.type === 'paragraph') {
      current.text += ` ${line}`;
    } else {
      close();
      current = { type: 'paragraph', text: line };
    }
  }
  close();
  return blocks;
}
