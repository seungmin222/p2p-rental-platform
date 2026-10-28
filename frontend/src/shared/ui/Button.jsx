import { buttonClassName } from './buttonClassName';
import Spinner from './Spinner';

/** isLoading 동안 비활성 + aria-busy, 라벨은 그대로 두고 스피너만 붙인다. */
export default function Button({
  variant,
  size,
  fullWidth,
  isLoading = false,
  disabled = false,
  type = 'button',
  className,
  children,
  ...rest
}) {
  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      className={buttonClassName({ variant, size, fullWidth, className })}
      {...rest}
    >
      {isLoading && <Spinner size="sm" />}
      {children}
    </button>
  );
}
