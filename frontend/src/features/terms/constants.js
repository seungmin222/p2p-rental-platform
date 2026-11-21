// 약관·정책(REQ-037). 문서 종류·동의 항목 코드는 docs/openapi.json TermsDocumentResponse.type · TermsReagreeRequest.types.

/** 약관 화면 /terms/:slug ↔ 문서 종류. 순서는 GET /api/terms 응답 순서와 같다 */
export const TERMS_DOCUMENTS = [
  { slug: 'service', type: 'TERMS_OF_SERVICE', title: '이용약관' },
  { slug: 'privacy', type: 'PRIVACY_POLICY', title: '개인정보처리방침' },
  { slug: 'prohibited-items', type: 'PROHIBITED_ITEMS', title: '대여 금지 품목' },
];

export function findTermsDocumentBySlug(slug) {
  return TERMS_DOCUMENTS.find((document) => document.slug === slug) ?? null;
}

export function findTermsDocumentByType(type) {
  return TERMS_DOCUMENTS.find((document) => document.type === type) ?? null;
}

/**
 * 필수 동의 항목(member.md REQ-037). name은 가입 요청 agreements의 필드, reagreeType은 재동의 요청 types 값,
 * documentType은 "보기"로 여는 문서(만 14세 확인은 문서가 없다).
 */
export const REQUIRED_AGREEMENTS = [
  {
    name: 'termsOfService',
    reagreeType: 'TERMS_OF_SERVICE',
    documentType: 'TERMS_OF_SERVICE',
    label: '이용약관 동의',
  },
  {
    name: 'privacyCollection',
    reagreeType: 'PRIVACY_COLLECTION',
    documentType: 'PRIVACY_POLICY',
    label: '개인정보 수집·이용 동의',
  },
  {
    name: 'ageOver14',
    reagreeType: 'AGE_OVER_14',
    documentType: null,
    label: '만 14세 이상이에요',
  },
];

/** 재동의 요청에 보내는 항목: requiresTermsAgreement가 true면 셋 다 보낸다(openapi TermsReagreeRequest) */
export const REAGREE_TYPES = REQUIRED_AGREEMENTS.map(({ reagreeType }) => reagreeType);

export const AGREEMENT_REQUIRED_MESSAGE = '필수 항목에 모두 동의해 주세요';

/** 가입 폼(react-hook-form) 필드 이름: agreements.termsOfService 등(가입 요청 agreements와 같은 모양) */
export function agreementFieldName(name) {
  return `agreements.${name}`;
}

/** 가입 폼 기본값: 모두 동의 전 */
export function emptyAgreements() {
  return Object.fromEntries(REQUIRED_AGREEMENTS.map(({ name }) => [name, false]));
}

// 재동의를 미룰 때 안내(open-issues #56: 지금은 서버가 막지 않아 안내만 한다)
export const TERMS_DEFER_NOTICE = '동의하지 않으면 서비스 이용이 제한될 수 있어요.';
