import { useMutation, useQuery } from '@tanstack/react-query';

import { useAuthStore } from '../../shared/api';
import { agreeTerms, fetchTermsDocuments } from './api';
import { REAGREE_TYPES } from './constants';

export const termsKeys = {
  all: () => ['terms'],
};

/**
 * 약관 문서 세 개(이용약관·개인정보처리방침·대여 금지 품목). 개정은 배포로만 바뀌어 오래 둔다.
 * enabled는 가입 화면의 "보기"처럼 열 때만 부르려고 받는다.
 */
export function useTermsDocuments({ enabled = true } = {}) {
  return useQuery({
    queryKey: termsKeys.all(),
    queryFn: fetchTermsDocuments,
    staleTime: 10 * 60_000,
    enabled,
  });
}

/** 약관 재동의(필수 세 항목). 응답의 requiresTermsAgreement로 authStore를 고친다(false면 모달이 닫힌다) */
export function useAgreeTerms() {
  return useMutation({
    mutationFn: () => agreeTerms(REAGREE_TYPES),
    onSuccess: ({ requiresTermsAgreement }) => {
      useAuthStore.getState().setRequiresTermsAgreement(requiresTermsAgreement);
    },
  });
}
