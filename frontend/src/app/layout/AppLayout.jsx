import { NavLink, Outlet, useMatches } from 'react-router';

import NavIcon from './NavIcon';
import { NAV_ITEMS } from './navItems';

function headerLinkClassName({ isActive }) {
  return `flex min-h-11 min-w-11 items-center justify-center px-3 text-sm ${
    isActive ? 'font-bold text-gray-900' : 'text-gray-500'
  }`;
}

function tabLinkClassName({ isActive }) {
  return `flex min-h-14 flex-col items-center justify-center gap-0.5 text-xs ${
    isActive ? 'font-bold text-gray-900' : 'text-gray-500'
  }`;
}

/**
 * 모바일(~1279px)은 하단 탭, PC(xl, 1280px~)는 상단 헤더. 본문은 중앙 정렬 최대 폭 컨테이너.
 * 하단 고정 바가 있는 화면은 라우트 handle.hideTabBar로 하단 탭을 숨긴다.
 */
export default function AppLayout() {
  const matches = useMatches();
  const hideTabBar = matches.some((match) => match.handle?.hideTabBar);

  return (
    <div className="min-h-dvh bg-white text-gray-900">
      <header className="hidden border-b border-gray-200 xl:block">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-4">
          <NavLink to="/" className="flex min-h-11 items-center text-lg font-bold">
            동네 대여
          </NavLink>
          <nav aria-label="주 메뉴">
            <ul className="flex gap-2">
              {NAV_ITEMS.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} end={item.end} className={headerLinkClassName}>
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
        </div>
      </header>

      <main
        className={`mx-auto w-full max-w-5xl px-4 ${hideTabBar ? 'pb-8' : 'pb-[calc(5rem+env(safe-area-inset-bottom))] xl:pb-8'}`}
      >
        <Outlet />
      </main>

      {!hideTabBar && (
        <nav
          aria-label="하단 탭"
          className="fixed inset-x-0 bottom-0 z-30 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)] xl:hidden"
        >
          <ul className="mx-auto grid max-w-5xl grid-cols-5">
            {NAV_ITEMS.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} end={item.end} className={tabLinkClassName}>
                  <NavIcon name={item.icon} />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </div>
  );
}
