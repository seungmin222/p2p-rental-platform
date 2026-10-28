// tone은 features/rental/constants.js RENTAL_STATUS.tone과 1:1
const TONES = {
  gray: 'bg-gray-100 text-gray-700',
  blue: 'bg-blue-50 text-blue-700',
  green: 'bg-green-50 text-green-700',
  red: 'bg-red-50 text-red-700',
  amber: 'bg-amber-50 text-amber-800',
};

export default function Badge({ tone = 'gray', className = '', children }) {
  return (
    <span
      className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-semibold ${TONES[tone]} ${className}`}
    >
      {children}
    </span>
  );
}
