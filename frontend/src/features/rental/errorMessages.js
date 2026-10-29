// 대여 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다(HTTP 메시지 문자열로 분기하지 않는다).
// 키는 docs/openapi.json에서 대여 화면이 부르는 API(대여 API, 물품 상세, 물품별 예약 현황) 응답 예시의 코드 전부.
// RENTAL_OWNER_ONLY는 대여 API, ITEM_NOT_OWNER는 물품별 예약 현황의 제공자 전용 403.

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
};

// 화면이 보던 대여가 이미 바뀐 경우. 문구를 보여 주고 상세·inspections를 다시 불러온다(queries.js).
const STALE_CODES = [
  'RENTAL_INVALID_TRANSITION',
  'COMMON_CONCURRENT_UPDATE',
  'RENTAL_CANCEL_AGREEMENT_CLOSED',
];

/** code에 맞는 문구. 모르는 code면 ApiError.message(CLIENT_* 포함 이미 한국어)를 쓴다. */
export function getErrorMessage(error) {
  return (
    MESSAGES[error?.code] ??
    error?.message ??
    '일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요'
  );
}

export function isStaleStateError(error) {
  return STALE_CODES.includes(error?.code);
}

/**
 * COMMON_INVALID_INPUT의 details[]를 react-hook-form setError로 필드에 붙인다.
 * fieldMap은 서버 field → 폼 필드 이름(예: { reasonText: 'reasonText', checklist: 'checklist.NO_EXTERIOR_DAMAGE' }).
 * 붙인 첫 필드 이름을 돌려주고, 하나도 못 붙였으면 null. 포커스는 옮기지 않는다(useSubmitErrorHandler가 제출 중 비활성이 풀린 뒤 옮김).
 */
export function applyFieldErrors(error, setError, fieldMap) {
  if (error?.code !== 'COMMON_INVALID_INPUT') {
    return null;
  }
  let firstField = null;
  for (const { field, reason } of error.details ?? []) {
    const name = fieldMap[field];
    if (name) {
      setError(name, { type: 'server', message: reason });
      firstField ??= name;
    }
  }
  return firstField;
}
