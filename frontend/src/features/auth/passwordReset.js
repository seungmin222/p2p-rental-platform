// 비밀번호 재설정(REQ-036) 화면 계산(순수 함수).

// openapi.json PasswordResetConfirmRequest.token pattern(Base64URL 43자). 서버도 형식이 틀리면 Redis를 보지 않고
// AUTH_RESET_TOKEN_INVALID라, 형식이 틀린 링크는 요청하지 않고 바로 "다시 요청" 안내를 보인다.
const RESET_TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;

/** 메일 링크의 token 값. 형식이 맞으면 그대로, 아니면 null */
export function readResetToken(value) {
  return typeof value === 'string' && RESET_TOKEN_PATTERN.test(value) ? value : null;
}

/** 재설정 제출 실패 중 토큰 문제(다시 요청해야 함)인지: AUTH_RESET_TOKEN_INVALID 또는 details의 token */
export function isResetTokenError(error) {
  if (error?.code === 'AUTH_RESET_TOKEN_INVALID') {
    return true;
  }
  return (
    error?.code === 'COMMON_INVALID_INPUT' &&
    (error.details ?? []).some((detail) => detail.field === 'token')
  );
}
