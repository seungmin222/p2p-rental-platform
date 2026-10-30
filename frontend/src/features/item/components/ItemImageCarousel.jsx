import { useRef, useState } from 'react';

/** 이미지가 없을 때의 자리(시안의 이미지 자리 면 + 아이콘) */
function ImagePlaceholder() {
  return (
    <div
      aria-hidden="true"
      className="flex size-full items-center justify-center bg-placeholder text-ink-faint"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinejoin="round"
        className="size-16"
      >
        <rect x="3" y="5" width="18" height="14" rx="2" />
        <path d="M3 16l5-5 4 4 3-3 6 6" />
      </svg>
    </div>
  );
}

/**
 * 이전·다음 버튼(마우스 사용자용, 터치 기기는 스와이프가 있어 숨긴다).
 * 끝에서 disabled 대신 aria-disabled를 쓴다 — disabled면 누르던 키보드 포커스가 빠진다.
 */
function NavButton({ direction, label, disabled, onClick }) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-disabled={disabled || undefined}
      onClick={onClick}
      className={`absolute top-1/2 ${direction === 'prev' ? 'left-2' : 'right-2'} flex size-11 -translate-y-1/2 items-center justify-center rounded-full bg-white/90 text-ink hover:bg-white focus-visible:outline-2 focus-visible:outline-primary aria-disabled:cursor-not-allowed aria-disabled:opacity-40 pointer-coarse:hidden`}
    >
      <svg
        aria-hidden="true"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="size-5"
      >
        <path d={direction === 'prev' ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
      </svg>
    </button>
  );
}

/**
 * 상세 상단 이미지(가로 스와이프 + "n / N"). 이미지가 없으면 자리 면. 본문 폭을 넘어 화면 끝까지 채운다(-mx-4).
 * 스크롤 위치로 몇 번째인지 센다. children은 그 위에 얹는 요소(뒤로 가기 버튼).
 * 키보드는 사진 영역에 포커스를 두고 좌우 화살표로 넘긴다(브라우저 기본 스크롤은 snap과 겹쳐 방향에 따라 제자리로 돌아온다).
 */
export default function ItemImageCarousel({ images, name, children }) {
  const [index, setIndex] = useState(0);
  const listRef = useRef(null);

  function handleScroll(event) {
    const { scrollLeft, clientWidth } = event.currentTarget;
    setIndex(clientWidth > 0 ? Math.round(scrollLeft / clientWidth) : 0);
  }

  function moveTo(nextIndex) {
    const list = listRef.current;
    if (list && nextIndex >= 0 && nextIndex < images.length) {
      list.scrollTo({ left: nextIndex * list.clientWidth });
    }
  }

  function handleKeyDown(event) {
    // 수정 키와 함께면 브라우저 몫이다(Alt+←·→ 뒤로·앞으로)
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) {
      return;
    }
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
      moveTo(index + (event.key === 'ArrowRight' ? 1 : -1));
    }
  }

  return (
    <div className="relative -mx-4 aspect-[390/236] overflow-hidden bg-placeholder">
      {images.length === 0 ? (
        <ImagePlaceholder />
      ) : (
        <div
          ref={listRef}
          role="group"
          aria-label={`${name} 사진`}
          tabIndex={0}
          onScroll={handleScroll}
          onKeyDown={handleKeyDown}
          className="flex size-full snap-x snap-mandatory [scrollbar-width:none] overflow-x-auto focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary [&::-webkit-scrollbar]:hidden"
        >
          {images.map((image, imageIndex) => (
            <img
              key={image.imageId}
              src={image.url}
              alt={`${name} 사진 ${imageIndex + 1}`}
              className="size-full shrink-0 snap-center object-cover"
            />
          ))}
        </div>
      )}
      {images.length > 1 && (
        <>
          <NavButton
            direction="prev"
            label="이전 사진"
            disabled={index === 0}
            onClick={() => moveTo(index - 1)}
          />
          <NavButton
            direction="next"
            label="다음 사진"
            disabled={index === images.length - 1}
            onClick={() => moveTo(index + 1)}
          />
        </>
      )}
      {images.length > 0 && (
        <span
          role="img"
          aria-label={`사진 ${index + 1} / ${images.length}`}
          className="absolute right-3 bottom-3 rounded-full bg-ink/60 px-3 py-1 text-caption font-semibold text-white tabular-nums"
        >
          {index + 1} / {images.length}
        </span>
      )}
      {children}
    </div>
  );
}
