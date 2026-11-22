// 대여 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다(HTTP 메시지 문자열로 분기하지 않는다).
// 키는 docs/openapi.json에서 대여 화면이 부르는 API(대여 API, 물품 상세, 물품별 예약 현황) 응답 예시의 코드 전부.
// RENTAL_OWNER_ONLY는 대여 API, ITEM_NOT_OWNER는 물품별 예약 현황의 제공자 전용 403.
// 필드 오류 붙이기(applyFieldErrors)·제출 실패 처리(useSubmitErrorHandler)는 shared에 있다.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';
import { LOST_REPORT_AFTER_DAYS } from './constants';

const STALE_MESSAGE = '상태가 바뀌었어요. 다시 불러왔어요';

const MESSAGES = {
  COMMON_INVALID_INPUT: '입력한 내용을 확인해 주세요',
  COMMON_CONCURRENT_UPDATE: STALE_MESSAGE,
  COMMON_UNSUPPORTED_MEDIA_TYPE: '요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  ITEM_NOT_FOUND: '물품을 찾을 수 없어요',
  ITEM_NOT_OWNER: '내 물품만 볼 수 있어요',
  REGION_NOT_VERIFIED: '동네 인증을 하면 예약할 수 있어요',
  IMAGE_COUNT_INVALID: '사진 장수를 확인해 주세요',
  IMAGE_INVALID_TYPE: 'JPG, PNG 사진만 올릴 수 있어요',
  IMAGE_SIZE_EXCEEDED: '사진 용량이 너무 커요',
  RENTAL_INVALID_PERIOD: '예약할 수 없는 기간이에요. 기간을 다시 골라 주세요',
  RENTAL_OUT_OF_AVAILABLE_PERIOD: '대여 가능 기간을 벗어났어요. 기간을 다시 골라 주세요',
  RENTAL_OWN_ITEM: '내 물품은 예약할 수 없어요',
  RENTAL_PERIOD_CONFLICT: '이미 예약된 기간이에요. 다른 기간을 골라 주세요',
  RENTAL_LOCK_TIMEOUT: '예약이 몰려 처리하지 못했어요. 잠시 후 다시 시도해 주세요',
  RENTAL_NOT_FOUND: '대여 정보를 볼 수 없어요',
  RENTAL_NOT_PARTICIPANT: '대여 정보를 볼 수 없어요',
  RENTAL_OWNER_ONLY: '제공자만 할 수 있어요',
  RENTAL_INVALID_TRANSITION: STALE_MESSAGE,
  RENTAL_HANDOVER_TOO_EARLY: '대여 시작일부터 인계할 수 있어요',
  RENTAL_CANCEL_NOT_ALLOWED:
    '대여 시작일부터는 바로 취소할 수 없어요. 상대에게 취소를 요청해 주세요',
  RENTAL_CANCEL_AGREEMENT_TOO_EARLY: '대여 시작일 전에는 바로 취소할 수 있어요',
  RENTAL_CANCEL_AGREEMENT_IN_PROGRESS: '이미 진행 중인 취소 요청이 있어요',
  RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE: '내가 보낸 요청에는 응답할 수 없어요',
  RENTAL_CANCEL_AGREEMENT_NOT_FOUND: '취소 요청을 찾을 수 없어요',
  RENTAL_CANCEL_AGREEMENT_CLOSED: STALE_MESSAGE,
  RENTAL_RENTER_ONLY: '대여자만 할 수 있어요',
  // 대여 연장(REQ-038). 기간 오류(PERIOD_CONFLICT·OUT_OF_AVAILABLE_PERIOD)는 연장 시트가 maxEndDate로 따로 안내한다
  RENTAL_EXTENSION_OVERDUE: '반납 예정일이 지나 연장할 수 없어요. 먼저 반납해 주세요',
  RENTAL_EXTENSION_LIMIT_EXCEEDED: '연장 횟수나 한 번에 늘릴 수 있는 일수를 넘었어요',
  RENTAL_EXTENSION_IN_PROGRESS: '결제를 기다리는 연장이 있어요. 대여 상세에서 결제해 주세요',
  RENTAL_EXTENSION_PERIOD_CONFLICT: '그 기간에 다른 예약이 있어요',
  RENTAL_EXTENSION_NOT_FOUND: '연장 요청을 찾을 수 없어요',
  // 미반납 신고(REQ-018)
  RENTAL_LOST_REPORT_TOO_EARLY: `반납 예정일로부터 ${LOST_REPORT_AFTER_DAYS}일이 지나야 미반납 신고를 할 수 있어요`,
  // 보증금 보류·차감(REQ-049). DEDUCTION_EXCEEDS_CAP은 차감 시트가 details maxAmount로 따로 안내한다
  DEPOSIT_NOT_HELD: STALE_MESSAGE,
  DEDUCTION_PROPOSAL_IN_PROGRESS: '대여자의 응답을 기다리는 제안이 있어요',
  DEDUCTION_GRADE_NOT_ALLOWED: '선택할 수 없는 손상 등급이에요',
  DEDUCTION_EXCEEDS_DEPOSIT: '남은 보증금보다 많이 차감할 수 없어요',
  DEDUCTION_EXCEEDS_CAP: '이 등급의 차감 상한을 넘었어요',
  DEDUCTION_EVIDENCE_REQUIRED: '증빙 사진을 올려 주세요',
  DEDUCTION_PROPOSAL_NOT_FOUND: STALE_MESSAGE,
  DEDUCTION_PROPOSAL_CLOSED: '응답 기한이 지나 자동으로 수락돼요. 다시 불러왔어요',
};

// 화면이 보던 대여가 이미 바뀐 경우. 문구를 보여 주고 상세·inspections를 다시 불러온다(queries.js).
const STALE_CODES = [
  'RENTAL_INVALID_TRANSITION',
  'COMMON_CONCURRENT_UPDATE',
  'RENTAL_CANCEL_AGREEMENT_CLOSED',
  // 보증금: 보류가 끝났거나(DEPOSIT_NOT_HELD) 제안이 바뀌었거나(IN_PROGRESS·NOT_FOUND) 기한이 지남(CLOSED, #43)
  'DEPOSIT_NOT_HELD',
  'DEDUCTION_PROPOSAL_IN_PROGRESS',
  'DEDUCTION_PROPOSAL_NOT_FOUND',
  'DEDUCTION_PROPOSAL_CLOSED',
  'RENTAL_LOST_REPORT_TOO_EARLY',
];

/** code에 맞는 문구. 모르는 code면 ApiError.message(CLIENT_* 포함 이미 한국어)를 쓴다. */
export const getErrorMessage = createErrorMessageGetter(MESSAGES);

export function isStaleStateError(error) {
  return STALE_CODES.includes(error?.code);
}

// 연장 요청이 거절됐는데 보던 상세가 낡은 경우(결제 대기 연장이 생김·종료일이 지남). 상세를 다시 불러와 버튼을 바꾼다
const EXTENSION_REFETCH_CODES = ['RENTAL_EXTENSION_IN_PROGRESS', 'RENTAL_EXTENSION_OVERDUE'];

export function isExtensionRefetchError(error) {
  return EXTENSION_REFETCH_CODES.includes(error?.code);
}

/** 차감 상한 초과(400 DEDUCTION_EXCEEDS_CAP)의 details maxAmount(숫자). 없으면 null */
export function readMaxDeductionAmount(error) {
  if (error?.code !== 'DEDUCTION_EXCEEDS_CAP') {
    return null;
  }
  const detail = (error.details ?? []).find((d) => d.field === 'maxAmount');
  const value = Number(detail?.reason);
  return Number.isSafeInteger(value) ? value : null;
}
