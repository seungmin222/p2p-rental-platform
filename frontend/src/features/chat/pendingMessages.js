// 보내는 중 임시 메시지(화면 전용 상태, 서버 데이터 아님). useReducer로 쓴다.
// 서버는 clientMessageId를 저장하지 않으므로 방 토픽으로 돌아온 메시지의 clientMessageId로만 짝을 맞춘다.
import { PENDING_STATUS } from './constants';

export const PENDING_ACTION = {
  ADD: 'add',
  CONFIRM: 'confirm', // 서버 메시지가 왔다 → 임시 메시지 제거
  FAIL: 'fail',
  FAIL_ALL_SENDING: 'failAllSending', // 연결이 끊겼다 → 확인 못 한 것은 실패
  RETRY: 'retry',
  REMOVE: 'remove',
};

/** { clientMessageId, content, status, errorMessage } 배열 */
export function pendingReducer(state, action) {
  switch (action.type) {
    case PENDING_ACTION.ADD:
      return [
        ...state,
        {
          clientMessageId: action.clientMessageId,
          content: action.content,
          status: action.failed ? PENDING_STATUS.FAILED : PENDING_STATUS.SENDING,
          errorMessage: action.errorMessage ?? null,
        },
      ];
    case PENDING_ACTION.CONFIRM:
    case PENDING_ACTION.REMOVE:
      return state.some((p) => p.clientMessageId === action.clientMessageId)
        ? state.filter((p) => p.clientMessageId !== action.clientMessageId)
        : state;
    case PENDING_ACTION.FAIL:
      return state.map((p) =>
        p.clientMessageId === action.clientMessageId
          ? { ...p, status: PENDING_STATUS.FAILED, errorMessage: action.errorMessage ?? null }
          : p,
      );
    case PENDING_ACTION.FAIL_ALL_SENDING:
      return state.some((p) => p.status === PENDING_STATUS.SENDING)
        ? state.map((p) =>
            p.status === PENDING_STATUS.SENDING
              ? { ...p, status: PENDING_STATUS.FAILED, errorMessage: action.errorMessage ?? null }
              : p,
          )
        : state;
    case PENDING_ACTION.RETRY:
      return state.map((p) =>
        p.clientMessageId === action.clientMessageId
          ? { ...p, status: PENDING_STATUS.SENDING, errorMessage: null }
          : p,
      );
    default:
      return state;
  }
}

/** 공백만이면 보내지 않는다(서버 isBlank). 글자 수는 코드 포인트 */
export function validateMessage(content, max) {
  if (content.trim() === '') {
    return 'empty';
  }
  return [...content].length > max ? 'tooLong' : null;
}
