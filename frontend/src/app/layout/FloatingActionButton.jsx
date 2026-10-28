import { Link } from 'react-router';

/**
 * 우측 하단 (+) 버튼. 아이콘만 있으므로 label(aria-label)은 필수.
 * 모바일은 하단 탭 위, PC는 최대 폭 컨테이너의 오른쪽 끝에 맞춘다.
 */
export default function FloatingActionButton({ to, label }) {
  return (
    <Link
      to={to}
      aria-label={label}
      className="fixed right-[max(1rem,calc((100vw-64rem)/2+1rem))] bottom-[calc(4.5rem+env(safe-area-inset-bottom))] z-20 flex size-14 items-center justify-center rounded-full bg-primary-600 text-white shadow-lg hover:bg-primary-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 xl:bottom-8"
    >
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
        className="size-7"
      >
        <path d="M12 5v14M5 12h14" />
      </svg>
    </Link>
  );
}
