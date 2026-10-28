/** 로그인 후 돌아올 경로를 담은 로그인 화면 주소 */
export function toLoginPath(returnTo) {
  return `/login?redirect=${encodeURIComponent(returnTo)}`;
}
