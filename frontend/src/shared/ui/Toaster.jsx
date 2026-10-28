import { useToastStore } from './toastStore';

const TONES = {
  default: 'bg-gray-900 text-white',
  danger: 'bg-danger-600 text-white',
};

/**
 * App에 한 번만 둔다. 하단 탭(약 3.5rem)·하단 고정 바(4.5rem) 위에 뜨도록 6rem + safe-area만큼 띄운다.
 * 열린 모달(top layer)보다 아래에 그려지므로 모달 안의 오류는 모달 안에 표시한다.
 */
export default function Toaster() {
  const toasts = useToastStore((state) => state.toasts);

  return (
    <div
      aria-live="polite"
      className="pointer-events-none fixed inset-x-0 bottom-[calc(6rem+env(safe-area-inset-bottom))] z-50 flex flex-col items-center gap-2 px-4"
    >
      {toasts.map((t) => (
        <p
          key={t.id}
          className={`max-w-sm rounded-lg px-4 py-3 text-sm shadow-lg ${TONES[t.tone] ?? TONES.default}`}
        >
          {t.message}
        </p>
      ))}
    </div>
  );
}
