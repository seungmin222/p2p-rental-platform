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
    <header className="sticky top-0 z-10 -mx-4 flex h-14 items-center gap-1 border-b border-gray-200 bg-white px-2">
      <button
        type="button"
        aria-label="뒤로 가기"
        onClick={handleBackClick}
        className="flex size-11 shrink-0 items-center justify-center rounded-full text-gray-700 hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-primary-600"
      >
        <svg
          aria-hidden="true"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="size-6"
        >
          <path d="M15 5l-7 7 7 7" />
        </svg>
      </button>
      <h1 className="min-w-0 flex-1 truncate text-base font-bold">{title}</h1>
      {action && <div className="flex shrink-0 items-center">{action}</div>}
    </header>
  );
}
