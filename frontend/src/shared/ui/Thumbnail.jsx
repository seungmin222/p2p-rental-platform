/**
 * 목록 썸네일(모서리 10px, lazy). 이미지가 없으면(null) 시안의 이미지 자리 면 + 아이콘.
 * 크기는 className으로(예: "size-16"). 옆에 이름이 있으므로 alt는 비운다.
 */
export default function Thumbnail({ url, className = '' }) {
  if (!url) {
    return (
      <div
        aria-hidden="true"
        className={`flex shrink-0 items-center justify-center rounded-[10px] bg-placeholder text-ink-faint ${className}`}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.6"
          strokeLinejoin="round"
          className="size-6"
        >
          <rect x="3" y="5" width="18" height="14" rx="2" />
          <path d="M3 16l5-5 4 4 3-3 6 6" />
        </svg>
      </div>
    );
  }
  return (
    <img
      src={url}
      alt=""
      loading="lazy"
      className={`shrink-0 rounded-[10px] bg-placeholder object-cover ${className}`}
    />
  );
}
