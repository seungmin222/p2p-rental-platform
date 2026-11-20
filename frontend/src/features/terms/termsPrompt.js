// 약관 재동의 모달을 띄울지(REQ-037). 상태는 authStore(로그인·재발급 응답 requiresTermsAgreement + "나중에" 여부).

/** 약관 화면(/terms/*)에서는 띄우지 않는다 — 모달의 "보기"로 문서를 읽으러 간 화면을 가리지 않게 */
export const TERMS_PATH_PREFIX = '/terms/';

export function shouldShowTermsPrompt(
  { accessToken, requiresTermsAgreement, termsPromptDismissed },
  pathname,
) {
  return (
    accessToken !== null &&
    requiresTermsAgreement === true &&
    termsPromptDismissed !== true &&
    !pathname.startsWith(TERMS_PATH_PREFIX)
  );
}
