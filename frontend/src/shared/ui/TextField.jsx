import { useId, useState } from 'react';

// 시안 Main.html 입력: 높이 48px, 좌우 14px, Line 테두리, 모서리 8px, 15/400
const FIELD =
  'w-full rounded-lg border bg-white px-3.5 text-body text-ink outline-none placeholder:text-ink-subtle focus:ring-1 disabled:cursor-not-allowed disabled:bg-surface disabled:text-ink-subtle';
const FIELD_NORMAL = 'border-line focus:border-primary focus:ring-primary';
const FIELD_ERROR = 'border-danger focus:border-danger focus:ring-danger';

/**
 * 라벨·안내·오류·글자 수가 붙은 입력. multiline이면 textarea.
 *
 * - React 19라 `ref`를 일반 prop으로 받아 입력 요소에 넘긴다(react-hook-form `register`를 붙일 수 있게).
 * - 필수 표시는 `aria-required`로만 한다. 네이티브 `required`는 브라우저 검증 말풍선이 떠서 폼 검증(JS)과
 *   겹치므로 쓰지 않는다.
 * - maxLength가 있으면 "12/200"을 표시한다. value를 넘기면 그 길이, 아니면 입력 이벤트로 센다
 *   (비제어 입력을 코드로 바꾸면 갱신되지 않으므로 그때는 value를 넘긴다). 길이는 브라우저 maxLength와
 *   같은 UTF-16 단위.
 *
 * @param {object} props
 * @param {string} props.label
 * @param {string} [props.hint] 입력 아래 안내(예: 권장 보증금 범위)
 * @param {string} [props.error] 있으면 테두리를 danger로 바꾸고 문구를 표시한다
 * @param {boolean} [props.required]
 * @param {boolean} [props.multiline]
 * @param {number} [props.maxLength]
 */
export default function TextField({
  ref,
  id,
  label,
  hint,
  error,
  required = false,
  multiline = false,
  rows = 4,
  maxLength,
  value,
  defaultValue,
  onChange,
  className = '',
  ...rest
}) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const hintId = `${inputId}-hint`;
  const errorId = `${inputId}-error`;
  const countId = `${inputId}-count`;
  const [typedLength, setTypedLength] = useState(() => String(defaultValue ?? '').length);
  const length = value != null ? String(value).length : typedLength;

  function handleChange(event) {
    if (value == null) {
      setTypedLength(event.target.value.length);
    }
    onChange?.(event);
  }

  const describedBy =
    [hint && hintId, error && errorId, maxLength != null && countId].filter(Boolean).join(' ') ||
    undefined;

  const fieldProps = {
    ref,
    id: inputId,
    value,
    defaultValue,
    onChange: handleChange,
    maxLength,
    'aria-invalid': error ? true : undefined,
    'aria-required': required || undefined,
    'aria-describedby': describedBy,
    className: `${FIELD} ${error ? FIELD_ERROR : FIELD_NORMAL} ${
      multiline ? 'min-h-24 resize-y py-3' : 'h-12'
    }`,
    ...rest,
  };

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={inputId} className="text-caption font-semibold">
        {label}
        {required && (
          <span aria-hidden="true" className="text-danger">
            {' '}
            *
          </span>
        )}
      </label>
      {multiline ? <textarea rows={rows} {...fieldProps} /> : <input {...fieldProps} />}
      {(hint || error || maxLength != null) && (
        <div className="flex items-start justify-between gap-3 text-xs">
          <div className="flex flex-col gap-0.5">
            {hint && (
              <span id={hintId} className="text-ink-muted">
                {hint}
              </span>
            )}
            {error && (
              <span id={errorId} className="text-danger">
                {error}
              </span>
            )}
          </div>
          {maxLength != null && (
            <span id={countId} className="ml-auto shrink-0 text-ink-subtle tabular-nums">
              {length}/{maxLength}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
