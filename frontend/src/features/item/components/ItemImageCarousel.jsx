import { useState } from 'react';

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
 * 상세 상단 이미지(가로 스와이프 + "n / N"). 이미지가 없으면 자리 면. 본문 폭을 넘어 화면 끝까지 채운다(-mx-4).
 * 스크롤 위치로 몇 번째인지 센다. children은 그 위에 얹는 요소(뒤로 가기 버튼).
 */
export default function ItemImageCarousel({ images, name, children }) {
  const [index, setIndex] = useState(0);

  function handleScroll(event) {
    const { scrollLeft, clientWidth } = event.currentTarget;
    setIndex(clientWidth > 0 ? Math.round(scrollLeft / clientWidth) : 0);
  }

  return (
    <div className="relative -mx-4 aspect-[390/236] overflow-hidden bg-placeholder">
      {images.length === 0 ? (
        <ImagePlaceholder />
      ) : (
        <div
          role="group"
          aria-label={`${name} 사진`}
          tabIndex={0}
          onScroll={handleScroll}
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
