import { Link, NavLink } from 'react-router';

import { useFeedRegion } from '../../features/item/useFeedRegion';
import { useMediaQuery, XL_MEDIA_QUERY } from '../../shared/hooks/useMediaQuery';
import { Skeleton } from '../../shared/ui';
import NavIcon from './NavIcon';
import { NAV_ITEMS } from './navItems';

// PC 헤더 오른쪽 메뉴: 홈은 로고, 검색은 검색 상자가 맡으므로 나머지(알림·채팅·나의 대여)만. 순서는 Figma 시안
const HEADER_MENU = ['/notifications', '/chats', '/me'].map((to) =>
  NAV_ITEMS.find((item) => item.to === to),
);

const FOCUS =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';

function menuLinkClassName({ isActive }) {
  return `flex min-h-11 items-center gap-1.5 rounded-lg text-sm ${FOCUS} ${
    isActive ? 'font-semibold text-ink' : 'text-ink-muted hover:text-ink'
  }`;
}

function SearchIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      className="size-4.5 shrink-0"
    >
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-4-4" />
    </svg>
  );
}

function ChevronDownIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4"
    >
      <path d="M6 9l6 6 6-6" />
    </svg>
  );
}

function PlusIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      className="size-4"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

/** 동네 이름 ▾ → /regions. 홈과 같은 기준(useFeedRegion)으로 정한다 */
function RegionLink() {
  const region = useFeedRegion();
  const regionName = region.status === 'ready' ? region.regionName : null;
  return (
    <Link
      to="/regions"
      aria-label={regionName ? `동네 설정, 현재 ${regionName}` : '동네 설정'}
      className={`flex min-h-11 shrink-0 items-center gap-1 rounded-lg text-sm font-semibold ${FOCUS}`}
    >
      {region.status === 'loading' ? (
        <Skeleton className="h-4 w-14" />
      ) : (
        <span className="max-w-32 truncate">{regionName ?? '동네 설정'}</span>
      )}
      <ChevronDownIcon />
    </Link>
  );
}

function DesktopHeaderContent() {
  return (
    <header className="sticky top-0 z-30 border-b border-divider bg-white">
      <div className="mx-auto flex h-16 max-w-5xl items-center gap-6 px-4">
        <NavLink
          to="/"
          end
          className={`flex min-h-11 shrink-0 items-center rounded-lg text-lg font-bold ${FOCUS}`}
        >
          동네 대여
        </NavLink>
        {/* 검색 화면으로 가는 링크(입력은 검색 화면에서). 모양만 시안의 둥근 검색 상자 */}
        <Link
          to="/search"
          className={`flex h-10 max-w-140 flex-1 items-center gap-2 rounded-full bg-surface px-4 text-sm text-ink-subtle hover:text-ink-muted ${FOCUS}`}
        >
          <SearchIcon />
          검색
        </Link>
        <div className="ml-auto flex items-center gap-5">
          <RegionLink />
          <nav aria-label="주 메뉴">
            <ul className="flex items-center gap-5">
              {HEADER_MENU.map((item) => (
                <li key={item.to}>
                  <NavLink to={item.to} end={item.end} className={menuLinkClassName}>
                    {({ isActive }) => (
                      <>
                        <NavIcon name={item.icon} active={isActive} />
                        {item.label}
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <Link
            to="/items/new"
            className={`flex h-10 shrink-0 items-center gap-1 rounded-full bg-primary px-4 text-sm font-semibold text-white hover:bg-primary-hover ${FOCUS}`}
          >
            <PlusIcon />
            물품 등록
          </Link>
        </div>
      </div>
    </header>
  );
}

/**
 * PC(xl, 1280px~) 상단 헤더(Figma 시안): 로고 → 검색 상자 → 동네 ▾ → 알림·채팅·나의 대여 → "+ 물품 등록".
 * 내용은 본문과 같은 폭(max-w-5xl). 동네 이름 때문에 내 정보를 부르므로 모바일에서는 아예 그리지 않는다.
 */
export default function DesktopHeader() {
  const isDesktop = useMediaQuery(XL_MEDIA_QUERY);
  return isDesktop ? <DesktopHeaderContent /> : null;
}
