import { afterEach, describe, expect, it } from 'vitest';

import { useAuthStore } from '../../shared/api';
import { shouldShowTermsPrompt } from './termsPrompt';

const REQUIRED = { accessToken: 'at', requiresTermsAgreement: true, termsPromptDismissed: false };

describe('shouldShowTermsPrompt', () => {
  it('로그인 + 재동의 필요 + 미루지 않음이면 띄운다', () => {
    expect(shouldShowTermsPrompt(REQUIRED, '/')).toBe(true);
  });

  it('비로그인·재동의 불필요·"나중에"·약관 화면이면 띄우지 않는다', () => {
    expect(shouldShowTermsPrompt({ ...REQUIRED, accessToken: null }, '/')).toBe(false);
    expect(shouldShowTermsPrompt({ ...REQUIRED, requiresTermsAgreement: false }, '/')).toBe(false);
    expect(shouldShowTermsPrompt({ ...REQUIRED, termsPromptDismissed: true }, '/')).toBe(false);
    expect(shouldShowTermsPrompt(REQUIRED, '/terms/service')).toBe(false);
  });
});

describe('authStore 약관 상태', () => {
  afterEach(() => useAuthStore.getState().clearAccessToken());

  it('AT를 지우면 재동의 필요·"나중에"도 처음으로 돌린다', () => {
    const auth = useAuthStore.getState();
    auth.setAccessToken('at');
    auth.setRequiresTermsAgreement(true);
    auth.dismissTermsPrompt();

    useAuthStore.getState().clearAccessToken();

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: null,
      requiresTermsAgreement: false,
      termsPromptDismissed: false,
    });
  });

  it('true가 아닌 값(응답에 없음)은 false로 본다', () => {
    useAuthStore.getState().setRequiresTermsAgreement(undefined);
    expect(useAuthStore.getState().requiresTermsAgreement).toBe(false);
  });
});
