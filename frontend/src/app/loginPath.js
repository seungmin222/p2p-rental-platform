// 로그인·가입 화면 주소와 로그인 후 돌아갈 경로(redirect 쿼리).

/** 로그인 후 돌아올 경로를 담은 로그인 화면 주소 */
export function toLoginPath(returnTo) {
  return `/login?redirect=${encodeURIComponent(returnTo)}`;
}

/** 로그인 ↔ 가입 이동에서 redirect를 유지한 주소. redirect가 없으면 경로만 */
export function withRedirect(path, redirect) {
  return redirect ? `${path}?redirect=${encodeURIComponent(redirect)}` : path;
}

// 돌아가면 다시 로그인·가입 화면이 되는 경로
const AUTH_PATHS = ['/login', '/signup'];
// 제어 문자(탭·줄바꿈 등). 브라우저가 URL에서 지워 "/\t/evil.com"이 "//evil.com"이 된다
function hasControlChar(value) {
  return [...value].some((char) => char.charCodeAt(0) < 0x20 || char.charCodeAt(0) === 0x7f);
}

/**
 * redirect 쿼리 값이 안전한 앱 안 경로면 그대로, 아니면 null(오픈 리다이렉트 방지).
 * '/'로 시작하고 '//'·'/\'로 시작하지 않을 때만 허용(둘 다 브라우저가 다른 호스트로 해석). 로그인·가입 화면이면 무시.
 */
export function getSafeRedirect(value) {
  if (typeof value !== 'string' || !value.startsWith('/')) {
    return null;
  }
  if (value.startsWith('//') || value.startsWith('/\\') || hasControlChar(value)) {
    return null;
  }
  const pathname = value.split(/[?#]/)[0].replace(/\/+$/, '') || '/';
  return AUTH_PATHS.includes(pathname) ? null : value;
}
