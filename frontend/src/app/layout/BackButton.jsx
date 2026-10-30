import { useNavigate } from 'react-router';

import { canGoBackInApp } from './backNavigation';

/** 뒤로 가기 버튼(44px). 앱에 처음 들어온 화면(이전 기록 없음)이면 홈으로 간다. className은 모양(예: 이미지 위의 흰 원)을 바꿀 때 */
export default function BackButton({ className = 'text-ink hover:bg-surface' }) {
  const navigate = useNavigate();

  function handleClick() {
    // 기록 위치로 판단한다(backNavigation.js). 직접 URL로 들어와 탭만 바꾼(replace) 경우도 첫 화면이다
    if (canGoBackInApp(window.history.state)) {
      navigate(-1);
    } else {
      navigate('/', { replace: true });
    }
  }

  return (
    <button
      type="button"
      aria-label="뒤로 가기"
      onClick={handleClick}
      className={`flex size-11 shrink-0 items-center justify-center rounded-full focus-visible:outline-2 focus-visible:outline-primary ${className}`}
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
  );
}
