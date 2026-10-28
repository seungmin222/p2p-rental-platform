import { HttpResponse } from 'msw';

/** 팀 공통 래퍼로 감싼 성공 응답. 필드는 docs/openapi.json의 응답 형태를 그대로 따른다. */
export function ok(data = null, init) {
  return HttpResponse.json({ success: true, data, error: null }, init);
}

/** 팀 공통 래퍼로 감싼 실패 응답. code는 백엔드 ErrorCode 이름을 쓴다. */
export function fail(status, code, message, details = []) {
  return HttpResponse.json(
    { success: false, data: null, error: { code, message, details } },
    { status },
  );
}
