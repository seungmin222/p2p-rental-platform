import { createBrowserRouter } from 'react-router';

import ComingSoonPage from '../pages/ComingSoonPage';
import HomePage from '../pages/HomePage';
import ItemDetailPage from '../pages/ItemDetailPage';
import ItemFormPage from '../pages/ItemFormPage';
import LoginPage from '../pages/LoginPage';
import MyItemsPage from '../pages/MyItemsPage';
import MyPage from '../pages/MyPage';
import MyProfilePage from '../pages/MyProfilePage';
import MyRentalsPage from '../pages/MyRentalsPage';
import NotFoundPage from '../pages/NotFoundPage';
import PaymentFailPage from '../pages/PaymentFailPage';
import PaymentSuccessPage from '../pages/PaymentSuccessPage';
import RegionPage from '../pages/RegionPage';
import RentalDetailPage from '../pages/RentalDetailPage';
import RentalReservePage from '../pages/RentalReservePage';
import SearchPage from '../pages/SearchPage';
import SignupPage from '../pages/SignupPage';
import { clearLocalSession } from '../features/auth/queries';
import { setUnauthorizedHandler } from '../shared/api';
import { queryClient } from './queryClient';
import AppLayout from './layout/AppLayout';
import { toLoginPath } from './loginPath';
import RequireAuth from './RequireAuth';

// 하단 고정 바가 있는 화면은 하단 탭을 숨긴다(AppLayout이 읽음)
const hideTabBar = { hideTabBar: true };

// 개발 서버에서만 등록. 빌드에서는 import.meta.env.DEV가 false로 바뀌어 이 import가 번들에서 빠진다.
const devRoutes = import.meta.env.DEV
  ? [
      {
        path: '/dev/ui',
        lazy: () =>
          import('../dev/UiPreviewPage').then((module) => ({ Component: module.default })),
      },
      {
        path: '/dev/ui/action-bar',
        handle: hideTabBar,
        lazy: () =>
          import('../dev/ActionBarPreviewPage').then((module) => ({ Component: module.default })),
      },
    ]
  : [];

// 1순위 라우트만 등록한다(frontend/CLAUDE.md "화면 ↔ 라우트"). 2순위 탭(채팅·알림)은 "준비 중"으로 연결.
export const router = createBrowserRouter([
  // SCR-01
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <HomePage /> }, // SCR-03
      { path: '/search', element: <SearchPage /> }, // SCR-04
      { path: '/regions', element: <RegionPage /> }, // SCR-02 (비회원 임시 동네 선택 가능)
      { path: '/items/:id', element: <ItemDetailPage />, handle: hideTabBar }, // SCR-05
      { path: '/chats', element: <ComingSoonPage /> }, // SCR-09 (2순위)
      { path: '/notifications', element: <ComingSoonPage /> }, // SCR-11 (2순위)
      {
        element: <RequireAuth />,
        children: [
          { path: '/items/new', element: <ItemFormPage />, handle: hideTabBar }, // SCR-06
          { path: '/items/:id/edit', element: <ItemFormPage />, handle: hideTabBar }, // SCR-06
          { path: '/items/:id/reserve', element: <RentalReservePage />, handle: hideTabBar }, // SCR-07
          { path: '/payments/success', element: <PaymentSuccessPage />, handle: hideTabBar }, // SCR-07
          { path: '/payments/fail', element: <PaymentFailPage />, handle: hideTabBar }, // SCR-07
          { path: '/rentals/:id', element: <RentalDetailPage />, handle: hideTabBar }, // SCR-08
          // SCR-12
          { path: '/me', element: <MyPage /> },
          { path: '/me/rentals', element: <MyRentalsPage /> },
          { path: '/me/items', element: <MyItemsPage /> },
          { path: '/me/profile', element: <MyProfilePage /> },
        ],
      },
      ...devRoutes,
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

// 재발급 실패 시 새로고침 없이 로그인 화면으로 보내고, 돌아올 경로를 넘긴다.
// 로그아웃과 같이 캐시도 지운다 — 남겨 두면 비회원으로 돌아온 화면에 이전 사용자의 isOwner·내 정보가 보인다
setUnauthorizedHandler(() => {
  const { pathname, search } = router.state.location;
  if (pathname !== '/login') {
    router.navigate(toLoginPath(pathname + search), { replace: true });
  }
  clearLocalSession(queryClient);
});
