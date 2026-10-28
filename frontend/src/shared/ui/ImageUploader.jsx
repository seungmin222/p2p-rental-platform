import { useEffect, useId, useRef, useState } from 'react';

import Badge from './Badge';
import CloseIcon from './CloseIcon';
import { addImages, describeRejections, makeCover, moveImage, removeImage } from './imageList';

/**
 * 새 이미지는 effect에서 objectURL을 만들고 cleanup에서 해제한다. value에 URL을 넣지 않으므로
 * StrictMode 이중 실행·삭제·부모의 value 교체·언마운트 어느 경우에도 해제되지 않은 URL이 남지 않는다.
 * src는 상태 대신 DOM에 직접 넣어 리렌더를 한 번 줄인다(React는 src prop이 undefined로 같으면 건드리지 않음).
 */
function Thumbnail({ image, alt }) {
  const imgRef = useRef(null);

  useEffect(() => {
    if (!image.file) {
      return undefined;
    }
    const url = URL.createObjectURL(image.file);
    imgRef.current.src = url;
    return () => URL.revokeObjectURL(url);
  }, [image.file]);

  return (
    <img
      ref={imgRef}
      src={image.file ? undefined : image.url}
      alt={alt}
      className="size-full object-cover"
    />
  );
}

function ChevronIcon({ direction }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4"
    >
      <path d={direction === 'prev' ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
    </svg>
  );
}

function CameraIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinejoin="round"
      className="size-6"
    >
      <path d="M4 8h3l2-3h6l2 3h3v11H4z" />
      <circle cx="12" cy="13" r="3.5" />
    </svg>
  );
}

const ARROW_BUTTON =
  'flex size-11 items-center justify-center rounded-full text-ink-muted hover:bg-surface focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:text-ink-disabled disabled:hover:bg-transparent';

// 순서를 바꾸거나 지우면 누른 버튼이 사라지거나 비활성이 될 수 있어, 다음으로 포커스할 후보를 순서대로 둔다.
const FOCUS_FALLBACK = ['prev', 'cover', 'next', 'remove'];

/**
 * 사진 선택·미리보기·순서 변경. 업로드 API는 호출하지 않는다(제출 시 부모가 value로 요청을 만든다).
 *
 * - value 항목: 기존 이미지 `{ id: 서버 id, url }`, 새 이미지 `{ id: 'local-n', file }`.
 *   물품 수정(item.md: 유지할 기존 id + 새 파일 + 순서)에 그대로 쓸 수 있다. 순서 = 배열 순서, 첫 장이 대표.
 * - 정책 수치(maxCount·maxFileSize·accept)는 기본값 없이 호출부가 넘긴다(ITEM_IMAGES·INSPECTION_IMAGES).
 * - 최소 장수(1장)는 막지 않는다. 제출 시 폼 검증이 맡는다.
 * - 거부된 파일만 빼고 나머지는 넣는다. 사유는 컴포넌트 안에 표시하고(모달·바텀시트 안에서도 보이게)
 *   onError(rejected)도 부른다. rejected = [{ file, reason }], reason은 IMAGE_REJECT_REASON.
 *
 * @param {object} props
 * @param {{id: string | number, url?: string, file?: File}[]} props.value
 * @param {(value: object[]) => void} props.onChange
 * @param {number} props.maxCount
 * @param {number} props.maxFileSize 바이트, 정확히 이 값까지 허용
 * @param {string[]} props.accept MIME 목록(예: ['image/jpeg', 'image/png'])
 * @param {(rejected: {file: File, reason: string}[]) => void} [props.onError]
 * @param {string} props.label
 * @param {string} [props.hint]
 */
export default function ImageUploader({
  value,
  onChange,
  maxCount,
  maxFileSize,
  accept,
  onError,
  label,
  hint,
}) {
  const labelId = useId();
  const hintId = useId();
  const inputRef = useRef(null);
  const addButtonRef = useRef(null);
  const listRef = useRef(null);
  const pendingFocus = useRef(null);
  const [messages, setMessages] = useState([]);
  const isFull = value.length >= maxCount;

  // 매 렌더 뒤에 확인한다. 조작한 때의 value가 그대로면 부모가 onChange를 반영하지 않은 것이므로 버린다
  // (남겨 두면 나중의 다른 변경 때 엉뚱하게 포커스가 옮겨짐).
  useEffect(() => {
    const target = pendingFocus.current;
    pendingFocus.current = null;
    if (!target || target.from === value) {
      return;
    }
    const item =
      target.id != null &&
      listRef.current?.querySelector(`[data-image-id="${CSS.escape(String(target.id))}"]`);
    if (item) {
      for (const action of [target.action, ...FOCUS_FALLBACK]) {
        const button = item.querySelector(`[data-action="${action}"]`);
        if (button && !button.disabled) {
          button.focus();
          return;
        }
      }
    }
    addButtonRef.current?.focus();
  });

  function handleFiles(event) {
    const files = Array.from(event.target.files ?? []);
    // 같은 파일을 다시 고를 수 있게 비운다(값이 같으면 change가 안 뜸)
    event.target.value = '';
    if (files.length === 0) {
      return;
    }
    const limits = { maxCount, maxFileSize, accept };
    const result = addImages(value, files, limits);
    if (result.images !== value) {
      // 가득 차면 "사진 추가"가 비활성이 돼 포커스를 잃으므로 마지막에 추가한 사진으로 옮긴다
      if (result.images.length >= maxCount) {
        pendingFocus.current = { from: value, id: result.images.at(-1).id, action: 'prev' };
      }
      onChange(result.images);
    }
    setMessages(describeRejections(result.rejected, limits));
    if (result.rejected.length > 0) {
      onError?.(result.rejected);
    }
  }

  function handleMove(id, offset) {
    pendingFocus.current = { from: value, id, action: offset < 0 ? 'prev' : 'next' };
    onChange(moveImage(value, id, offset));
  }

  function handleCover(id) {
    pendingFocus.current = { from: value, id, action: 'next' };
    onChange(makeCover(value, id));
  }

  function handleRemove(id) {
    const index = value.findIndex((image) => image.id === id);
    const neighbor = value[index + 1] ?? value[index - 1];
    pendingFocus.current = { from: value, id: neighbor?.id, action: 'remove' };
    setMessages([]);
    onChange(removeImage(value, id));
  }

  return (
    <div
      role="group"
      aria-labelledby={labelId}
      aria-describedby={hint ? hintId : undefined}
      className="flex flex-col gap-2"
    >
      <span id={labelId} className="text-caption font-semibold">
        {label}
      </span>
      {hint && (
        <span id={hintId} className="text-xs text-ink-muted">
          {hint}
        </span>
      )}

      <input
        ref={inputRef}
        type="file"
        multiple
        accept={accept.join(',')}
        hidden
        onChange={handleFiles}
      />

      {/* 칸 폭은 화면이 아니라 놓인 자리(바텀시트 등) 기준. 아래 줄 버튼 3개(44px × 3)가 들어가는 폭을 지킨다 */}
      <div className="@container">
        <ul
          ref={listRef}
          className="grid grid-cols-2 gap-x-2 gap-y-1 @md:grid-cols-3 @2xl:grid-cols-4"
        >
          <li>
            <button
              ref={addButtonRef}
              type="button"
              disabled={isFull}
              onClick={() => inputRef.current?.click()}
              className="flex aspect-square w-full flex-col items-center justify-center gap-1 rounded-[10px] border border-line bg-white text-ink-muted hover:bg-surface focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:bg-surface disabled:text-ink-disabled"
            >
              <CameraIcon />
              <span className="text-xs">사진 추가</span>
              <span className="text-xs">
                {value.length}/{maxCount}
              </span>
            </button>
          </li>
          {value.map((image, index) => {
            const n = index + 1;
            const isCover = index === 0;
            return (
              <li key={image.id} data-image-id={image.id}>
                <div className="relative aspect-square overflow-hidden rounded-[10px] bg-placeholder">
                  <Thumbnail image={image} alt={`${n}번째 사진`} />
                  {isCover && (
                    <Badge tone="ink" className="absolute bottom-1.5 left-1.5">
                      대표
                    </Badge>
                  )}
                  <button
                    type="button"
                    data-action="remove"
                    aria-label={`${n}번째 사진 삭제`}
                    onClick={() => handleRemove(image.id)}
                    className="absolute top-0 right-0 flex size-11 items-center justify-center focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                  >
                    <span className="flex size-6 items-center justify-center rounded-full bg-ink/60 text-white">
                      <CloseIcon className="size-3.5" />
                    </span>
                  </button>
                </div>
                <div className="flex items-center justify-between">
                  <button
                    type="button"
                    data-action="prev"
                    aria-label={`${n}번째 사진 앞으로`}
                    disabled={isCover}
                    onClick={() => handleMove(image.id, -1)}
                    className={ARROW_BUTTON}
                  >
                    <ChevronIcon direction="prev" />
                  </button>
                  {!isCover && (
                    <button
                      type="button"
                      data-action="cover"
                      aria-label={`${n}번째 사진 대표로`}
                      onClick={() => handleCover(image.id)}
                      className="flex h-11 items-center rounded-full px-2 text-xs font-semibold text-ink-muted hover:bg-surface focus-visible:outline-2 focus-visible:outline-primary"
                    >
                      대표로
                    </button>
                  )}
                  <button
                    type="button"
                    data-action="next"
                    aria-label={`${n}번째 사진 뒤로`}
                    disabled={index === value.length - 1}
                    onClick={() => handleMove(image.id, 1)}
                    className={ARROW_BUTTON}
                  >
                    <ChevronIcon direction="next" />
                  </button>
                </div>
              </li>
            );
          })}
        </ul>
      </div>

      <div aria-live="polite" className="flex flex-col gap-0.5 text-xs text-danger">
        {messages.map((message) => (
          <p key={message}>{message}</p>
        ))}
      </div>
    </div>
  );
}
