// tone은 features/rental/constants.js RENTAL_STATUS.tone과 1:1. 색은 디자인 시스템의 대여 상태 배지.
const TONES = {
  gray: 'bg-surface text-ink-muted',
  blue: 'bg-info-soft text-info',
  green: 'bg-primary-soft text-primary',
  red: 'bg-danger-soft text-danger',
  amber: 'bg-accent-soft text-accent-strong',
  ink: 'bg-ink text-white',
};

export default function Badge({ tone = 'gray', className = '', children }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${TONES[tone]} ${className}`}
    >
      {children}
    </span>
  );
}
