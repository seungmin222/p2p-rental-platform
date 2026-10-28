import axios from 'axios';

import { toApiError } from './ApiError';
import { useAuthStore } from './authStore';

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api';

/** 인터셉터를 달지 않은 별도 인스턴스. 재발급 401이 다시 재발급을 부르는 루프를 막는다. */
const reissueClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true });

// TODO: 백엔드 확정 전 임시 — feat/member-auth의 재발급 응답 DTO가 머지되면 필드명을 openapi.json으로 확인해 맞춘다.
function extractAccessToken(body) {
  return body?.data?.accessToken;
}

let pendingReissue = null;

/**
 * RT 쿠키로 AT를 재발급해 authStore에 저장한다. 동시에 여러 번 불려도 요청은 한 번만 나간다(single-flight).
 * 실패하면 AT를 지우고 ApiError를 throw한다.
 */
export function reissueAccessToken() {
  if (!pendingReissue) {
    pendingReissue = reissueClient
      .post('/auth/reissue')
      .then((response) => {
        const accessToken = extractAccessToken(response.data);
        if (!accessToken) {
          throw new Error('재발급 응답에 Access Token이 없습니다.');
        }
        useAuthStore.getState().setAccessToken(accessToken);
        return accessToken;
      })
      .catch((error) => {
        useAuthStore.getState().clearAccessToken();
        throw toApiError(error);
      })
      .finally(() => {
        pendingReissue = null;
      });
  }
  return pendingReissue;
}
