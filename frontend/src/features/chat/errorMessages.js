// 채팅 화면의 오류 문구는 이 파일 한 곳에서 code로 고른다.
// 키는 docs/openapi.json 채팅 API 응답 예시의 코드와 docs/api.md "STOMP 메시지" /user/queue/errors 코드.
import { createErrorMessageGetter } from '../../shared/lib/formErrors';

const MESSAGES = {
  COMMON_INVALID_INPUT: '메시지를 보내지 못했어요',
  AUTH_UNAUTHORIZED: '로그인이 필요해요',
  ITEM_NOT_FOUND: '삭제됐거나 찾을 수 없는 물품이에요',
  CHAT_OWN_ITEM: '내 물품에는 채팅할 수 없어요',
  CHAT_ROOM_NOT_FOUND: '채팅방을 찾을 수 없어요',
  CHAT_NOT_PARTICIPANT: '참여하지 않은 채팅방이에요',
};

export const getErrorMessage = createErrorMessageGetter(MESSAGES);
