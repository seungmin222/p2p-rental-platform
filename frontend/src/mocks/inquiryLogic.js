// 문의·분쟁 목(REQ-063)의 계산. 순수 함수라 테스트는 inquiryLogic.test.js.
// 검사 순서는 백엔드 InquiryService와 같다: 입력(type·내용 공백·길이) 400 → 분쟁이면 rentalId 필수 400 →
// 대여 404 → 당사자 403 → (분쟁) 결제 대기 409 → 열린 분쟁 409.

// policies.md inquiry.content-max
export const INQUIRY_CONTENT_MAX = 1000;

const TYPES = ['GENERAL', 'DISPUTE'];

/** 요청 본문 검사. 틀리면 details[], 맞으면 null */
export function checkInquiryBody(body) {
  if (!body || typeof body !== 'object') {
    return [{ field: 'body', reason: '요청 본문이 올바르지 않습니다.' }];
  }
  if (!TYPES.includes(body.type)) {
    return [{ field: 'type', reason: '문의 종류가 올바르지 않습니다.' }];
  }
  if (typeof body.content !== 'string' || !body.content.trim()) {
    return [{ field: 'content', reason: '공백일 수 없습니다' }];
  }
  if ([...body.content].length > INQUIRY_CONTENT_MAX) {
    return [{ field: 'content', reason: `${INQUIRY_CONTENT_MAX}자 이하로 입력해 주세요.` }];
  }
  if (body.rentalId != null && !(Number.isSafeInteger(body.rentalId) && body.rentalId > 0)) {
    return [{ field: 'rentalId', reason: '형식이 올바르지 않습니다.' }];
  }
  if (body.type === 'DISPUTE' && body.rentalId == null) {
    return [{ field: 'rentalId', reason: '분쟁은 대여를 선택해야 합니다.' }];
  }
  return null;
}

/** 이 회원·대여에 RESOLVED가 아닌 분쟁이 있나(상대방 분쟁은 따로) */
function hasOpenDispute(inquiries, memberId, rentalId) {
  return inquiries.some(
    (inquiry) =>
      inquiry.type === 'DISPUTE' &&
      inquiry.memberId === memberId &&
      inquiry.rentalId === rentalId &&
      inquiry.status !== 'RESOLVED',
  );
}

/** 분쟁 접수 가능 여부(대여 찾기·당사자 검사 뒤). 막히면 code, 아니면 null */
export function checkDispute(rental, inquiries, memberId) {
  if (rental.status === 'PENDING_PAYMENT') {
    return 'INQUIRY_DISPUTE_NOT_ALLOWED';
  }
  if (hasOpenDispute(inquiries, memberId, rental.rentalId)) {
    return 'INQUIRY_DISPUTE_ALREADY_OPEN';
  }
  return null;
}

/** 저장할 문의(memberId 포함, 응답에는 빼고 보낸다) */
export function buildInquiry(body, { inquiryId, memberId, createdAt }) {
  return {
    inquiryId,
    memberId,
    rentalId: body.rentalId ?? null,
    type: body.type,
    status: 'RECEIVED',
    content: body.content,
    createdAt,
  };
}

/** 저장 형태 → InquiryResponse */
export function toInquiryResponse({ inquiryId, rentalId, type, status, content, createdAt }) {
  return { inquiryId, rentalId, type, status, content, createdAt };
}

/** 내 문의, 최신 접수 순(같은 시각이면 id 큰 것부터) */
export function listMyInquiries(inquiries, memberId) {
  return inquiries
    .filter((inquiry) => inquiry.memberId === memberId)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.inquiryId - a.inquiryId)
    .map(toInquiryResponse);
}

/**
 * 열린(RESOLVED가 아닌) 분쟁이 있는 대여 id. 정산 목은 이 대여의 지급 예정 정산을 보류(HELD)로 본다
 * (state-machines §3: 접수 시 SCHEDULED → HELD, 정산 생성 전이면 처음부터 HELD, PAID는 그대로).
 * 접수한 회원과 상관없이 대여 기준이다(대여자가 접수해도 제공자 정산이 보류된다).
 */
export function openDisputeRentalIds(inquiries) {
  return new Set(
    inquiries
      .filter((inquiry) => inquiry.type === 'DISPUTE' && inquiry.status !== 'RESOLVED')
      .map((inquiry) => inquiry.rentalId),
  );
}
