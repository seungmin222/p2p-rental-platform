// 약관 API(REQ-037). 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient } from '../../shared/api';

/** GET /api/terms → TermsDocumentResponse[] { type, title, version, content(마크다운) }. 공개 API */
export function fetchTermsDocuments({ signal } = {}) {
  return apiClient.get('/terms', { signal });
}

/** POST /api/members/me/terms-agreements { types } → TermsAgreementStatusResponse { requiresTermsAgreement } */
export function agreeTerms(types) {
  return apiClient.post('/members/me/terms-agreements', { types });
}
