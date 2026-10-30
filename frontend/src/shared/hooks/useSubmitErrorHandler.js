import { useEffect, useRef, useState } from 'react';

import { applyCodeFieldError, applyFieldErrors } from '../lib/formErrors';

/**
 * 폼 공통 제출 실패 처리. 돌려준 함수를 mutate의 onError로 쓴다.
 * - details[](COMMON_INVALID_INPUT)는 fieldMap으로, codeFields에 있는 code(예: 409 중복)는 그 필드로 바로 오류를 붙인다.
 * - 포커스는 isPending이 풀려 필드가 다시 활성화된 뒤(effect)에 첫 오류 필드로 옮긴다.
 *   제출 중에는 필드가 disabled라 setError의 shouldFocus로는 포커스가 가지 않아서.
 * - 필드에 붙이지 못한 오류는 root.server(폼 아래쪽 문구), 문구는 getMessage(도메인 errorMessages.js).
 */
export function useSubmitErrorHandler(
  { setError, setFocus },
  isPending,
  { fieldMap = {}, codeFields = {}, getMessage },
) {
  // 요청마다 새 객체라 같은 필드가 다시 틀려도 다시 포커스한다
  const [focusRequest, setFocusRequest] = useState(null);
  const handledRequest = useRef(null);

  useEffect(() => {
    if (!isPending && focusRequest && focusRequest !== handledRequest.current) {
      handledRequest.current = focusRequest;
      setFocus(focusRequest.name);
    }
  }, [isPending, focusRequest, setFocus]);

  return (error) => {
    const firstField =
      applyFieldErrors(error, setError, fieldMap) ??
      applyCodeFieldError(error, setError, codeFields, getMessage);
    if (firstField) {
      setFocusRequest({ name: firstField });
    } else {
      setError('root.server', { message: getMessage(error) });
    }
  };
}
