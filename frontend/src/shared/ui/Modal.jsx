import { useId } from 'react';

import CloseIcon from './CloseIcon';
import { useDialog } from './useDialog';

/**
 * 가운데 모달. dismissible이 false면 Esc·배경 클릭·닫기 버튼으로 닫히지 않는다(처리 중 등).
 * 닫기는 항상 부모가 open을 false로 바꿔서 한다.
 */
export default function Modal({ open, onClose, title, children, footer, dismissible = true }) {
  const titleId = useId();
  const dialogProps = useDialog({ open, onClose, dismissible });

  return (
    <dialog
      {...dialogProps}
      aria-labelledby={titleId}
      className="m-auto w-[calc(100%-2rem)] max-w-md rounded-2xl bg-white p-0 text-ink shadow-xl backdrop:bg-black/50"
    >
      {open && (
        <div className="p-5">
          <div className="flex items-start justify-between gap-2">
            <h2 id={titleId} className="pt-2 text-heading">
              {title}
            </h2>
            {dismissible && (
              <button
                type="button"
                aria-label="닫기"
                onClick={onClose}
                className="-mt-1 -mr-2 flex size-11 shrink-0 items-center justify-center rounded-full text-ink-muted hover:bg-surface focus-visible:outline-2 focus-visible:outline-primary"
              >
                <CloseIcon />
              </button>
            )}
          </div>
          <div className="mt-2 text-body text-ink">{children}</div>
          {footer && <div className="mt-5 flex justify-end gap-2">{footer}</div>}
        </div>
      )}
    </dialog>
  );
}
