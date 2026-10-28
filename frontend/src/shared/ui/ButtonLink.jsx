import { Link } from 'react-router';

import { buttonClassName } from './buttonClassName';

/**
 * 이동이 목적이면 버튼 모양이어도 링크로 둔다(새 탭 열기·주소 복사가 되도록).
 * 링크에는 disabled 속성이 없으므로 비활성은 aria-disabled + 탭 순서 제외 + 클릭(Enter 포함) 막기로 처리한다.
 */
export default function ButtonLink({
  variant,
  size,
  fullWidth,
  disabled = false,
  className,
  onClick,
  ...rest
}) {
  function handleClick(event) {
    if (disabled) {
      event.preventDefault();
      return;
    }
    onClick?.(event);
  }

  return (
    <Link
      className={buttonClassName({ variant, size, fullWidth, className })}
      aria-disabled={disabled || undefined}
      tabIndex={disabled ? -1 : undefined}
      onClick={handleClick}
      {...rest}
    />
  );
}
