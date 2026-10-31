// 폼 제출 실패를 문구·필드 오류로 바꾸는 공용 함수. 문구 표(code → 문구)는 각 도메인의 features/*/errorMessages.js에 둔다.

const DEFAULT_MESSAGE = '일시적인 오류가 발생했어요. 잠시 후 다시 시도해 주세요';

/**
 * code → 문구 표로 문구 함수를 만든다. 모르는 code면 ApiError.message(CLIENT_* 포함 이미 한국어)를 쓴다.
 * HTTP 메시지 문자열로 분기하지 않는다(frontend/CLAUDE.md "API 연동 규칙").
 */
export function createErrorMessageGetter(messages) {
  return (error) => messages[error?.code] ?? error?.message ?? DEFAULT_MESSAGE;
}

/**
 * COMMON_INVALID_INPUT의 details[]를 react-hook-form setError로 필드에 붙인다.
 * fieldMap은 서버 field → 폼 필드 이름(예: { reasonText: 'reasonText', checklist: 'checklist.NO_EXTERIOR_DAMAGE' }).
 * 정확히 맞는 키가 없으면 상위 필드로 찾는다(availablePeriods[0].startDate → availablePeriods).
 * 붙인 첫 필드 이름을 돌려주고, 하나도 못 붙였으면 null. 포커스는 옮기지 않는다(useSubmitErrorHandler가 제출 중 비활성이 풀린 뒤 옮김).
 */
export function applyFieldErrors(error, setError, fieldMap) {
  if (error?.code !== 'COMMON_INVALID_INPUT') {
    return null;
  }
  let firstField = null;
  for (const { field, reason } of error.details ?? []) {
    const name = fieldMap[field] ?? fieldMap[String(field).split(/[.[]/)[0]];
    if (name) {
      setError(name, { type: 'server', message: reason });
      firstField ??= name;
    }
  }
  return firstField;
}

/**
 * 특정 code를 한 필드의 오류로 붙인다(예: 409 MEMBER_EMAIL_DUPLICATED → email). codeFields는 code → 폼 필드 이름.
 * 붙였으면 필드 이름, 아니면 null.
 */
export function applyCodeFieldError(error, setError, codeFields, getMessage) {
  const name = codeFields[error?.code];
  if (!name) {
    return null;
  }
  setError(name, { type: 'server', message: getMessage(error) });
  return name;
}
