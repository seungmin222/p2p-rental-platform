/**
 * 서비스 로고 마크(Figma 시안 PC 헤더): 둥근 primary 사각형(36, 모서리 12) 안에 흰 텐트 선 아이콘.
 * 장식이라 aria-hidden — 감싸는 링크에 aria-label을 단다.
 */
export default function AppLogo({ className = '' }) {
  return (
    <span
      aria-hidden="true"
      className={`flex size-9 shrink-0 items-center justify-center rounded-xl bg-primary text-white ${className}`}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-6"
      >
        <path d="M12 4L3 20h18z" />
        <path d="M12 4v16" />
      </svg>
    </span>
  );
}
