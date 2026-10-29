import { useId, useRef } from 'react';

/**
 * 밑줄 탭(같은 폭) + 선택한 탭의 패널(children). items: [{ value, label }].
 * 시안에 탭이 없어 하단 탭 색 규칙(선택 Ink 600, 비선택 Ink muted)을 따른다.
 * 키보드: 좌우 화살표·Home·End로 이동하면서 바로 선택한다(선택한 탭만 Tab 순서에 들어간다).
 * listClassName은 탭 줄을 감싼 요소에 붙는다(예: 헤더 아래 sticky).
 */
export default function Tabs({ items, value, onChange, label, listClassName = '', children }) {
  const idBase = useId();
  const listRef = useRef(null);
  const tabId = (tabValue) => `${idBase}-tab-${tabValue}`;
  const panelId = `${idBase}-panel`;

  function select(index) {
    const next = items[(index + items.length) % items.length];
    onChange(next.value);
    listRef.current?.querySelector(`[data-value="${CSS.escape(next.value)}"]`)?.focus();
  }

  function handleKeyDown(event) {
    const index = items.findIndex((item) => item.value === value);
    const moves = {
      ArrowRight: index + 1,
      ArrowLeft: index - 1,
      Home: 0,
      End: items.length - 1,
    };
    if (event.key in moves) {
      event.preventDefault();
      select(moves[event.key]);
    }
  }

  return (
    <>
      <div className={listClassName}>
        <div
          ref={listRef}
          role="tablist"
          aria-label={label}
          onKeyDown={handleKeyDown}
          className="flex border-b border-line"
        >
          {items.map((item) => {
            const isSelected = item.value === value;
            return (
              <button
                key={item.value}
                type="button"
                role="tab"
                id={tabId(item.value)}
                data-value={item.value}
                aria-selected={isSelected}
                aria-controls={panelId}
                tabIndex={isSelected ? 0 : -1}
                onClick={() => onChange(item.value)}
                className={`-mb-px min-h-12 flex-1 border-b-2 px-2 text-body focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary ${
                  isSelected
                    ? 'border-ink font-semibold text-ink'
                    : 'border-transparent text-ink-muted hover:text-ink'
                }`}
              >
                {item.label}
              </button>
            );
          })}
        </div>
      </div>
      <div role="tabpanel" id={panelId} aria-labelledby={tabId(value)}>
        {children}
      </div>
    </>
  );
}
