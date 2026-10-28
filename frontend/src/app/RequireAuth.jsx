import { Navigate, Outlet, useLocation } from 'react-router';

import { useAuthStore } from '../shared/api';
import { toLoginPath } from './loginPath';

/** 로그인한 사용자만 하위 라우트를 볼 수 있다. 비로그인이면 로그인 화면으로 보내고 돌아올 경로를 넘긴다. */
export default function RequireAuth() {
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);
  const location = useLocation();

  if (!isLoggedIn) {
    return <Navigate to={toLoginPath(location.pathname + location.search)} replace />;
  }
  return <Outlet />;
}
