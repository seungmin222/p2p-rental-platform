// 문의·분쟁 API 호출(REQ-063). 요청·응답 필드는 docs/openapi.json 그대로(인터셉터가 data만 꺼내 돌려준다).
import { apiClient } from '../../shared/api';

/** POST /api/inquiries { type, rentalId?, content } → InquiryResponse */
export function createInquiry(body) {
  return apiClient.post('/inquiries', body);
}

/** GET /api/inquiries?page= → PageResponse<InquiryResponse>. size는 보내지 않는다(inquiry.page 고정), 최신 접수 순 */
export function fetchMyInquiries(page) {
  return apiClient.get('/inquiries', { params: { page } });
}
