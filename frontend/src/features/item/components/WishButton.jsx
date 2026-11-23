/**
 * 관심(REQ-022) 하트 토글 버튼. 담겨 있으면 채운 하트(primary), 아니면 외곽선(ink-muted).
 * 누를 때 할 일(로그인 유도·요청)은 부르는 쪽이 정한다. 요청 중이면 aria-busy만 걸고 누름은 부르는 쪽이 무시한다.
 */
export default function WishButton({
  isWished,
  onClick,
  isPending = false,
  label,
  className = '',
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={isWished}
      aria-busy={isPending || undefined}
      onClick={onClick}
      className={`flex size-11 shrink-0 items-center justify-center rounded-full hover:bg-surface focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary ${
        isWished ? 'text-primary' : 'text-ink-muted'
      } ${className}`}
    >
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        fill={isWished ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinejoin="round"
        className="size-6"
      >
        <path d="M12 20s-7-4.4-7-10a4 4 0 017-2.5A4 4 0 0119 10c0 5.6-7 10-7 10z" />
      </svg>
    </button>
  );
}
