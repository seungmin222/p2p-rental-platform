/** 서버 응답이 없을 때(네트워크 끊김, CORS·DNS 실패 등) 쓰는 프론트 전용 코드 */
export const CLIENT_NETWORK_ERROR = 'CLIENT_NETWORK_ERROR';
/** 요청 timeout 초과 */
export const CLIENT_TIMEOUT = 'CLIENT_TIMEOUT';
/** 응답은 왔지만 공통 래퍼 { success, data, error }가 아님(프록시 502, 백엔드 미기동 등) */
export const CLIENT_INVALID_RESPONSE = 'CLIENT_INVALID_RESPONSE';

/**
 * 모든 API 실패를 이 형태로 정규화한다. 분기는 code로 한다(message 문자열로 분기하지 않는다).
 * status는 HTTP 상태 코드이며 응답이 없으면 0이다.
 */
export class ApiError extends Error {
  constructor({ code, message, details = [], status = 0 }) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.details = details;
    this.status = status;
  }
}

/** 공통 래퍼가 아닌 응답의 오류. 실패 응답과 2xx 응답 검사가 같이 쓴다. */
export function createInvalidResponseError(status) {
  return new ApiError({
    code: CLIENT_INVALID_RESPONSE,
    message: '일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.',
    status,
  });
}

export function toApiError(error) {
  if (error instanceof ApiError) {
    return error;
  }
  const response = error.response;
  if (!response) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new ApiError({
        code: CLIENT_TIMEOUT,
        message: '응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.',
      });
    }
    return new ApiError({ code: CLIENT_NETWORK_ERROR, message: '네트워크 연결을 확인해 주세요.' });
  }
  const body = response.data;
  if (body && typeof body === 'object' && body.error && body.error.code) {
    return new ApiError({
      code: body.error.code,
      message: body.error.message,
      details: body.error.details ?? [],
      status: response.status,
    });
  }
  return createInvalidResponseError(response.status);
}
