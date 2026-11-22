// 문의 작성 화면의 순수 함수(검증·요청 만들기·주소 읽기·분쟁 가능 여부). 테스트는 inquiryForm.test.js.
import { countCodePoints } from '../../shared/lib/validators';
import { INQUIRY_CONTENT_MAX, INQUIRY_TYPE } from './constants';

export const INQUIRY_CONTENT_REQUIRED = '내용을 입력해 주세요';
export const INQUIRY_CONTENT_TOO_LONG = `${INQUIRY_CONTENT_MAX.toLocaleString('ko-KR')}자 이하로 입력해 주세요`;

/** 공백만 불가, inquiry.content-max(코드 포인트) 이하. react-hook-form validate 형태(true 또는 문구) */
export function validateInquiryContent(text) {
  if (!text || !text.trim()) {
    return INQUIRY_CONTENT_REQUIRED;
  }
  return countCodePoints(text) <= INQUIRY_CONTENT_MAX || INQUIRY_CONTENT_TOO_LONG;
}

/** 양의 안전한 정수만 id로 본다 */
function readPositiveId(value) {
  if (value == null || !/^\d+$/.test(value)) {
    return null;
  }
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

/**
 * 작성 화면 주소 `?rentalId=&type=`. 틀린 값은 없는 것으로(rentalId null, type GENERAL).
 * 분쟁은 대여가 있어야 하므로 rentalId가 없으면 GENERAL로 시작한다.
 */
export function readInquiryParams(searchParams) {
  const rentalId = readPositiveId(searchParams.get('rentalId'));
  const type = searchParams.get('type');
  return {
    rentalId,
    type: type === 'DISPUTE' && rentalId != null ? 'DISPUTE' : 'GENERAL',
  };
}

/** 작성 화면 주소. 대여 상세에서 들어갈 때 쓴다 */
export function getInquiryWritePath({ rentalId, type } = {}) {
  const params = new URLSearchParams();
  if (rentalId != null) {
    params.set('rentalId', String(rentalId));
  }
  if (type && type !== 'GENERAL') {
    params.set('type', type);
  }
  const query = params.toString();
  return query ? `/me/inquiries/new?${query}` : '/me/inquiries/new';
}

/**
 * 분쟁을 고를 수 없는 이유(없으면 null). 서버가 최종 검사하므로 UI 보조다.
 * - NO_RENTAL: 연결한 대여가 없다(분쟁은 rentalId 필수)
 * - PENDING_PAYMENT: 결제 대기 대여(409 INQUIRY_DISPUTE_NOT_ALLOWED)
 * 열린 분쟁 여부는 대여 상세 응답에 없어 여기서 보지 않는다(409 INQUIRY_DISPUTE_ALREADY_OPEN으로 안내).
 */
export function getDisputeBlockReason(rentalId, rental) {
  if (rentalId == null) {
    return 'NO_RENTAL';
  }
  if (rental?.status === 'PENDING_PAYMENT') {
    return 'PENDING_PAYMENT';
  }
  return null;
}

export const DISPUTE_BLOCK_TEXT = {
  NO_RENTAL: '분쟁은 대여 상세의 "문의·분쟁 접수"에서 대여를 골라 접수해요',
  PENDING_PAYMENT: '결제 전 대여는 분쟁을 접수할 수 없어요',
};

/** 폼 값 → InquiryCreateRequest. 내용은 다듬지 않고 원문을 보낸다(서버도 trim하지 않는다) */
export function toInquiryRequest({ type, content }, rentalId) {
  const body = { type: INQUIRY_TYPE[type] ? type : 'GENERAL', content };
  if (rentalId != null) {
    body.rentalId = rentalId;
  }
  return body;
}
