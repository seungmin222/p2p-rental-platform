import { reissueAccessToken } from '../shared/api';

/** 앱 시작 시 한 번 RT 쿠키로 로그인 상태를 복원한다. 실패하면 비로그인으로 진행한다(로그인 화면으로 보내지 않음). */
export function restoreSession() {
  return reissueAccessToken().catch(() => null);
}
