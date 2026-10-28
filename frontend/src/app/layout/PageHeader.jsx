import { useLocation, useNavigate } from 'react-router';

/** 뒤로 가기 + 제목 + 우측 액션. 앱에 처음 들어온 화면(이전 기록 없음)이면 홈으로 간다. */
export default function PageHeader({ title, action }) {
  const navigate = useNavigate();
  const location = useLocation();

  function handleBackClick() {
    // 직접 URL로 들어오면 location.key가 'default'
    if (location.key === 'default') {
      navigate('/', { replace: true });
    } else {
      navigate(-1);
    }
  }

  return (
    <header className="sticky top-0 z-10 -mx-4 flex h-14 items-center gap-1 border-b border-divider bg-white pr-4 pl-1">
      <button
        type="button"
        aria-label="뒤로 가기"
        onClick={handleBackClick}
        className="flex size-11 shrink-0 items-center justify-center rounded-full text-ink hover:bg-surface focus-visible:outline-2 focus-visible:outline-primary"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-[22px]"
        >
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </button>
      <h1 className="min-w-0 flex-1 truncate text-lg font-bold">{title}</h1>
      {action && <div className="flex shrink-0 items-center">{action}</div>}
    </header>
  );
}
