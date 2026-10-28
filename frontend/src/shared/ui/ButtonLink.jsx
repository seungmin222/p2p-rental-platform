import { Link } from 'react-router';

import { buttonClassName } from './buttonClassName';

/** 이동이 목적이면 버튼 모양이어도 링크로 둔다(새 탭 열기·주소 복사가 되도록). */
export default function ButtonLink({ variant, size, fullWidth, className, ...rest }) {
  return <Link className={buttonClassName({ variant, size, fullWidth, className })} {...rest} />;
}
