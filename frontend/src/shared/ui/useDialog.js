import { useEffect, useRef } from 'react';

import { lockBodyScroll, unlockBodyScroll } from './scrollLock';

/**
 * 네이티브 <dialog>를 open prop으로 제어한다. 포커스 가두기·Esc·top layer는 브라우저에 맡기고,
 * 여기서는 열고 닫기, 스크롤 잠금, 포커스 복귀, 닫기 요청을 onClose로 넘기는 일만 한다.
 * 반환값을 <dialog {...dialogProps}>에 그대로 펼친다.
 */
export function useDialog({ open, onClose, dismissible }) {
  const ref = useRef(null);
  // effect가 직접 닫은 경우의 close 이벤트는 무시하려고(StrictMode에서 닫고 바로 다시 연다)
  const closingByEffectRef = useRef(false);

  useEffect(() => {
    const dialog = ref.current;
    if (!open || !dialog) {
      return undefined;
    }
    const previousFocus = document.activeElement;
    // StrictMode 재실행에서도 이미 열린 dialog에 showModal을 다시 부르지 않는다(InvalidStateError)
    if (!dialog.open) {
      dialog.showModal();
    }
    lockBodyScroll();
    return () => {
      unlockBodyScroll();
      if (dialog.open) {
        closingByEffectRef.current = true;
        dialog.close();
      }
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus();
      }
    };
  }, [open]);

  function handleCancel(event) {
    // Esc: 브라우저가 닫지 않게 막고 부모 상태로 닫는다
    event.preventDefault();
    if (dismissible) {
      onClose();
    }
  }

  function handleClose() {
    if (closingByEffectRef.current) {
      closingByEffectRef.current = false;
      return;
    }
    // 브라우저가 cancel을 무시하고 닫은 경우(Esc 연타 등)
    if (dismissible) {
      onClose();
    } else {
      ref.current?.showModal();
    }
  }

  function handleClick(event) {
    // dialog 자신이 클릭 대상이면 배경(::backdrop)을 누른 것. 내용은 안쪽 래퍼가 받는다.
    if (event.target === event.currentTarget && dismissible) {
      onClose();
    }
  }

  return { ref, onCancel: handleCancel, onClose: handleClose, onClick: handleClick };
}
