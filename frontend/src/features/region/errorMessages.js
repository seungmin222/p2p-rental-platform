// 동네 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다.
// 키는 docs/openapi.json 동네 API 응답 예시의 코드 + 위치 실패(LocationError, 프론트 전용 코드).
import { createErrorMessageGetter } from '../../shared/lib/formErrors';
import { LOCATION_ERROR } from './geolocation';

// 권한 거부는 검색으로 추가할 수 있다고 함께 안내한다(member.md REQ-004 예외: 인증 안 된 상태로 추가 가능)
export const LOCATION_DENIED_MESSAGE =
  '위치 권한이 없어 인증할 수 없어요. 검색으로 동네를 추가할 수 있어요';

const MESSAGES = {
  COMMON_INVALID_INPUT: '요청을 처리하지 못했어요. 다시 시도해 주세요',
  COMMON_NOT_FOUND: '이미 삭제된 동네예요. 목록을 다시 불러왔어요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  REGION_LIMIT_EXCEEDED: '더 추가할 수 없어요. 기존 동네를 삭제한 뒤 추가해 주세요',
  REGION_ALREADY_ADDED: '이미 추가한 동네예요',
  REGION_VERIFY_MISMATCH: '지금 위치가 이 동네가 아니에요',
  [LOCATION_ERROR.DENIED]: LOCATION_DENIED_MESSAGE,
  [LOCATION_ERROR.UNAVAILABLE]: '현재 위치를 확인할 수 없어요. 잠시 후 다시 시도해 주세요',
  [LOCATION_ERROR.TIMEOUT]: '위치를 확인하는 데 시간이 오래 걸려요. 다시 시도해 주세요',
  [LOCATION_ERROR.UNSUPPORTED]: '이 브라우저에서는 위치를 쓸 수 없어요. 검색으로 찾아 주세요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);

/** by-coord 404는 "동네 없음"이 아니라 "이 위치의 동네를 못 찾음"이라 문구가 다르다 */
export function getLocateErrorMessage(error) {
  return error?.code === 'COMMON_NOT_FOUND'
    ? '이 위치의 동네를 찾지 못했어요. 검색으로 찾아 주세요'
    : getErrorMessage(error);
}
