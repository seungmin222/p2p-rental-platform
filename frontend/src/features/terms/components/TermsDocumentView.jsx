import { parseMarkdownBlocks } from '../../../shared/lib/markdown';

const HEADING_CLASS = {
  1: 'text-heading',
  2: 'mt-2 text-body font-semibold',
  3: 'text-body font-semibold',
};

function Block({ block }) {
  switch (block.type) {
    case 'heading': {
      // 문서 제목(#)은 화면 제목과 겹쳐 h2, 조(##)는 h3부터
      const Tag = `h${Math.min(block.level + 1, 4)}`;
      return <Tag className={HEADING_CLASS[block.level]}>{block.text}</Tag>;
    }
    case 'list': {
      const Tag = block.ordered ? 'ol' : 'ul';
      return (
        <Tag className={`flex flex-col gap-1 pl-5 ${block.ordered ? 'list-decimal' : 'list-disc'}`}>
          {block.items.map((item, index) => (
            <li key={index}>{item}</li>
          ))}
        </Tag>
      );
    }
    case 'quote':
      return (
        <p className="rounded-xl bg-surface px-4 py-3 text-caption text-ink-muted">{block.text}</p>
      );
    default:
      return <p>{block.text}</p>;
  }
}

/**
 * 약관 본문(마크다운)을 읽기 전용으로 그린다. HTML로 넣지 않고 블록마다 요소를 만든다(shared/lib/markdown.js).
 * hideTitle: 화면·시트 제목과 같은 첫 # 제목을 뺄 때.
 */
export default function TermsDocumentView({ document, hideTitle = false }) {
  let blocks = parseMarkdownBlocks(document.content);
  if (hideTitle) {
    const titleIndex = blocks.findIndex((block) => block.type === 'heading' && block.level === 1);
    blocks = blocks.filter((_, index) => index !== titleIndex);
  }
  return (
    <article className="flex flex-col gap-3 text-body break-keep text-ink">
      {blocks.map((block, index) => (
        <Block key={index} block={block} />
      ))}
      {document.version && <p className="text-xs text-ink-muted">버전 {document.version}</p>}
    </article>
  );
}
