import { Link } from 'react-router';

import LogoutButton from '../features/auth/components/LogoutButton';
import ProfileSummary from '../features/auth/components/ProfileSummary';

// 1순위 메뉴만(REQ-019·020). 프로필 수정·관심·최근 본·결제/정산 내역·후기는 해당 기능 브랜치에서 더한다
const MENU = [
  { to: '/me/rentals', label: '빌린 내역' },
  { to: '/me/items', label: '내 물품 관리' },
];

/** SCR-12 나의 대여. 하단 탭 화면이라 뒤로 가기 없는 제목만 둔다. 위에 프로필 요약, 맨 아래 로그아웃 */
export default function MyPage() {
  return (
    <>
      <h1 className="flex h-14 items-center text-lg font-bold">나의 대여</h1>
      <ProfileSummary />
      <nav aria-label="나의 대여 메뉴">
        <ul className="-mx-4 border-t border-divider">
          {MENU.map((menu) => (
            <li key={menu.to} className="border-b border-divider">
              <Link
                to={menu.to}
                className="flex min-h-14 items-center justify-between px-4 text-body hover:bg-surface focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
              >
                {menu.label}
                <svg
                  aria-hidden="true"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  className="size-5 text-ink-faint"
                >
                  <path d="M9 5l7 7-7 7" />
                </svg>
              </Link>
            </li>
          ))}
        </ul>
      </nav>
      <div className="mt-8">
        <LogoutButton />
      </div>
    </>
  );
}
