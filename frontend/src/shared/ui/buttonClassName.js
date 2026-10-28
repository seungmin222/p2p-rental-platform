const BASE =
  'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-600 disabled:cursor-not-allowed disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50';

const VARIANTS = {
  primary: 'bg-primary-600 text-white hover:bg-primary-700',
  secondary: 'border border-gray-300 bg-white text-gray-900 hover:bg-gray-50',
  ghost: 'text-gray-700 hover:bg-gray-100',
  danger: 'bg-danger-600 text-white hover:bg-danger-700',
};

// 터치 영역 44px 이상(min-h-11)
const SIZES = {
  md: 'min-h-11 px-4 text-sm',
  lg: 'min-h-12 px-5 text-base',
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
