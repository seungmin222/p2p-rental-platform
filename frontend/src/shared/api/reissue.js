import axios from 'axios';

import { toApiError } from './ApiError';
import { useAuthStore } from './authStore';

export const API_BASE_URL = import.meta.env.VITE_API_URL ?? '/api';

/** 인터셉터를 달지 않은 별도 인스턴스. 재발급 401이 다시 재발급을 부르는 루프를 막는다. */
const reissueClient = axios.create({ baseURL: API_BASE_URL, withCredentials: true });

// 응답 본문을 읽는 곳은 이 두 함수뿐이다(openapi.json AuthTokenResponse { accessToken, requiresTermsAgreement }).
function extractAccessToken(body) {
  return body?.data?.accessToken;
}

/** 약관 재동의 필요 여부(REQ-037). 로그인 응답과 같은 필드라 재발급할 때마다 다시 정한다 */
function extractRequiresTermsAgreement(body) {
  return body?.data?.requiresTermsAgreement === true;
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
        useAuthStore
          .getState()
          .setRequiresTermsAgreement(extractRequiresTermsAgreement(response.data));
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
