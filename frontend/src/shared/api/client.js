import axios from 'axios';

import { toApiError } from './ApiError';
import { useAuthStore } from './authStore';
import { API_BASE_URL, reissueAccessToken } from './reissue';

/** baseURL을 뺀 상대 경로(쿼리 제외). 인터셉터 시점의 config.url은 아직 baseURL이 붙기 전이다. */
function toPath(config) {
  const path = (config.url ?? '').split('?')[0];
  return path.startsWith('/') ? path : `/${path}`;
}

/** Authorization을 붙이지 않는 공개 API: /auth/**, POST /members */
function isPublicApi(config) {
  const path = toPath(config);
  const method = (config.method ?? 'get').toLowerCase();
  return path.startsWith('/auth/') || (method === 'post' && path === '/members');
}

/** 401이어도 재발급을 시도하지 않는 경로 */
const NO_REISSUE_PATHS = ['/auth/login', '/auth/logout', '/auth/reissue'];

let handleUnauthorized = () => {
  window.location.assign('/login');
};

/** 재발급 실패 시 실행할 이동(로그인 화면으로)을 등록한다. app/에서 라우터로 등록한다. */
export function setUnauthorizedHandler(handler) {
  handleUnauthorized = handler;
}

export const apiClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true });

apiClient.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();
  if (isPublicApi(config)) {
    config.headers.delete('Authorization');
  } else if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response.data?.data,
  async (error) => {
    // 취소는 그대로 넘긴다(TanStack Query가 취소로 인식해야 함)
    if (axios.isCancel(error)) {
      throw error;
    }
    const config = error.config;
    const canReissue =
      error.response?.status === 401 &&
      config &&
      !config._isRetry &&
      !NO_REISSUE_PATHS.includes(toPath(config));
    if (!canReissue) {
      throw toApiError(error);
    }

    config._isRetry = true;
    try {
      await reissueAccessToken();
    } catch {
      handleUnauthorized();
      throw toApiError(error);
    }
    return apiClient(config);
  },
);
