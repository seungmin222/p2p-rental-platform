import { useId } from 'react';

import CloseIcon from './CloseIcon';
import { useDialog } from './useDialog';

/** 모바일은 화면 하단에 붙고, PC(xl)는 가운데 모달 모양. 드래그로 닫기는 하지 않는다. */
export default function BottomSheet({
  open,
  onClose,
  title,
  children,
  footer,
  dismissible = true,
}) {
  const titleId = useId();
  const dialogProps = useDialog({ open, onClose, dismissible });

  return (
    <dialog
      {...dialogProps}
      aria-labelledby={titleId}
      className="mx-0 mt-auto mb-0 max-h-[85dvh] w-full max-w-full rounded-t-2xl bg-white p-0 text-gray-900 shadow-xl backdrop:bg-black/50 xl:m-auto xl:w-full xl:max-w-md xl:rounded-2xl"
    >
      {open && (
        <div className="px-5 pt-3 pb-[max(1.25rem,env(safe-area-inset-bottom))]">
          <div className="flex items-center justify-between gap-2">
            <h2 id={titleId} className="text-lg font-bold">
              {title}
            </h2>
            {dismissible && (
              <button
                type="button"
                aria-label="닫기"
                onClick={onClose}
                className="-mr-2 flex size-11 shrink-0 items-center justify-center rounded-full text-gray-500 hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-primary-600"
              >
                <CloseIcon />
              </button>
            )}
          </div>
          <div className="mt-2 text-sm text-gray-700">{children}</div>
          {footer && <div className="mt-5 flex gap-2">{footer}</div>}
        </div>
      )}
    </dialog>
  );
}
