// 디자인 시스템(docs/design/mockups/Main.html) 버튼 5가지: 채운 주 색상·주 색상 외곽선·회색 면·위험 외곽선·비활성
const BASE =
  'inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:border-transparent disabled:bg-line disabled:text-ink-subtle aria-disabled:pointer-events-none aria-disabled:border-transparent aria-disabled:bg-line aria-disabled:text-ink-subtle';

const VARIANTS = {
  primary: 'bg-primary text-white hover:bg-primary-hover', // 예약하기
  outline: 'border border-primary bg-white text-primary hover:bg-primary-soft', // 채팅하기
  secondary: 'bg-surface text-ink hover:bg-line', // 취소
  danger: 'border border-danger bg-white text-danger hover:bg-danger-soft', // 이상 있음
};

// md가 시안 기본(48px). sm은 헤더처럼 좁은 곳용이며 터치 영역 44px(min-h-11)은 지킨다.
const SIZES = {
  sm: 'min-h-11 px-4 text-sm',
  md: 'min-h-12 px-[22px] text-body',
};

/** Button과 ButtonLink가 같은 모양을 쓰도록 클래스만 분리 */
export function buttonClassName({
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  className = '',
} = {}) {
  return [BASE, VARIANTS[variant], SIZES[size], fullWidth ? 'w-full' : '', className]
    .filter(Boolean)
    .join(' ');
}
