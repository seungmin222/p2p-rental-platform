// 물품 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다.
// 키는 docs/openapi.json 물품 API 응답 예시의 코드 + 위치 실패(LocationError, 프론트 전용 코드).
import { createErrorMessageGetter } from '../../shared/lib/formErrors';
import { LOCATION_ERROR } from '../region/geolocation';
import { ITEM_IMAGE_MAX_MB, ITEM_IMAGES } from './constants';

const MESSAGES = {
  COMMON_INVALID_INPUT: '요청을 처리하지 못했어요. 다시 시도해 주세요',
  COMMON_UNSUPPORTED_MEDIA_TYPE: '요청을 처리하지 못했어요. 다시 시도해 주세요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  ITEM_NOT_FOUND: '삭제됐거나 찾을 수 없는 물품이에요',
  ITEM_NOT_OWNER: '내 물품만 수정하거나 삭제할 수 있어요',
  ITEM_HAS_ACTIVE_RENTAL: '진행 중인 대여가 있어 삭제할 수 없어요',
  ITEM_PERIOD_CONFLICT: '예약이 있는 날짜는 대여 가능 기간에서 뺄 수 없어요',
  REGION_NOT_VERIFIED: '동네 인증 후 등록할 수 있어요',
  IMAGE_COUNT_INVALID: `사진은 ${ITEM_IMAGES.MIN_COUNT}~${ITEM_IMAGES.MAX_COUNT}장 올려 주세요`,
  IMAGE_INVALID_TYPE: 'jpg 또는 png 사진만 올릴 수 있어요',
  IMAGE_SIZE_EXCEEDED: `사진 용량이 너무 커요. 장당 ${ITEM_IMAGE_MAX_MB}MB 이하로 올려 주세요`,
  RENTAL_LOCK_TIMEOUT: '요청이 몰려 처리하지 못했어요. 잠시 후 다시 시도해 주세요',
  [LOCATION_ERROR.DENIED]: '위치 권한이 없어요. 브라우저 설정에서 위치를 허용해 주세요',
  [LOCATION_ERROR.UNAVAILABLE]: '현재 위치를 확인할 수 없어요. 잠시 후 다시 시도해 주세요',
  [LOCATION_ERROR.TIMEOUT]: '위치를 확인하는 데 시간이 오래 걸려요. 다시 시도해 주세요',
  [LOCATION_ERROR.UNSUPPORTED]: '이 브라우저에서는 현재 위치를 쓸 수 없어요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);

/** 삭제 완료 토스트. result는 삭제 뮤테이션 결과(이미 지워진 물품이면 alreadyDeleted) */
export function getDeletedMessage(result) {
  return result?.alreadyDeleted ? '이미 삭제된 물품이에요' : '물품을 삭제했어요';
}

/** details 한 건씩(충돌 기간·진행 중인 대여). 없으면 빈 배열 */
export function getErrorReasons(error) {
  return (error?.details ?? []).map((detail) => detail.reason).filter(Boolean);
}
