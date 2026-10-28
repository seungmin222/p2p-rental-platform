const SIZES = {
  sm: 'size-4 border-2',
  md: 'size-6 border-2',
  lg: 'size-10 border-4',
};

/** label이 있으면 스크린 리더에 읽히는 로딩 표시, 없으면 장식(버튼 안 등). */
export default function Spinner({ size = 'md', label, className = '' }) {
  const circle = (
    <span
      aria-hidden="true"
      className={`inline-block animate-spin rounded-full border-current border-t-transparent ${SIZES[size]} ${className}`}
    />
  );
  if (!label) {
    return circle;
  }
  return (
    <span role="status" className="inline-flex items-center">
      {circle}
      <span className="sr-only">{label}</span>
    </span>
  );
}
