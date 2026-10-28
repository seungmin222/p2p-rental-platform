// 모달·바텀시트가 여러 개 겹쳐 열려도 마지막 하나가 닫힐 때만 스크롤을 푼다.
let lockCount = 0;
let previousOverflow = '';

export function lockBodyScroll() {
  if (lockCount === 0) {
    previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
  }
  lockCount += 1;
}

export function unlockBodyScroll() {
  if (lockCount === 0) {
    return;
  }
  lockCount -= 1;
  if (lockCount === 0) {
    document.body.style.overflow = previousOverflow;
  }
}
