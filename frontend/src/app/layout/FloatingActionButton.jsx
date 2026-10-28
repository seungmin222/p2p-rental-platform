import { Link } from 'react-router';

/**
 * 우측 하단 "+ 물품 등록" 같은 알약형 버튼(시안 Home.html). 글자가 보이므로 별도 aria-label은 두지 않는다.
 * 모바일은 하단 탭(64px) 위 16px, PC는 최대 폭 컨테이너의 오른쪽 끝에 맞춘다.
 */
export default function FloatingActionButton({ to, label }) {
  return (
    <Link
      to={to}
      className="fixed right-[max(1rem,calc((100vw-64rem)/2+1rem))] bottom-[calc(5rem+env(safe-area-inset-bottom))] z-20 flex h-13 items-center gap-1.5 rounded-full bg-primary pr-5 pl-4 text-[15px] font-semibold text-white shadow-[0_6px_16px_rgba(28,28,26,0.18)] hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary xl:bottom-8"
    >
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        className="size-5"
      >
        <path d="M12 5v14M5 12h14" />
      </svg>
      {label}
    </Link>
  );
}
