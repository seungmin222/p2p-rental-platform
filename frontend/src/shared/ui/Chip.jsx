/**
 * 선택 칩(선택 Primary 면·흰 글씨 13/600 — Figma 시안, chore/fe-design-refactor. 처음 시안 Main.html은 Ink 면.
 * 비선택 Line 테두리 13/500, 높이 34px).
 * 겉모양은 34px이고 터치 영역은 before로 늘려 44px을 맞춘다. absolute는 테두리 안쪽(32px) 기준이라
 * 위아래 6px씩 늘린다(겉 테두리 밖으로는 5px씩).
 * 가로로는 늘리지 않으므로 한 줄 목록에서는 겹치지 않는다. 여러 줄로 감쌀 때는 줄 간격을 10px(gap-y-2.5)
 * 이상 둬야 위아래 칩의 터치 영역이 겹치지 않는다. 가로 스크롤 목록은 화면에서 만든다.
 */
export default function Chip({ selected = false, className = '', children, ...rest }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      className={`relative inline-flex h-8.5 shrink-0 items-center rounded-full border px-3.5 text-caption whitespace-nowrap before:absolute before:inset-x-0 before:-inset-y-1.5 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
        selected
          ? 'border-primary bg-primary font-semibold text-white'
          : 'border-line bg-white font-medium text-ink hover:bg-surface'
      } ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
