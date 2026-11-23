import { useLocation, useNavigate } from 'react-router';

import { useAuthStore } from '../../../shared/api';
import { toast } from '../../../shared/ui';
import { getErrorMessage } from '../errorMessages';
import { useToggleWish } from '../queries';
import { handleDetailWishClick } from '../wishClick';
import WishButton from './WishButton';

/**
 * 상세 하단 바의 관심 하트(REQ-022). 하트는 상세 캐시 isWished를 따르고 누르면 바로 바뀐다(낙관적, 실패하면 되돌린 뒤 알림).
 * 비회원·요청 중 처리는 handleDetailWishClick. 관심 수는 시안대로 상세에 그리지 않는다.
 */
export default function DetailWishButton({ item }) {
  const navigate = useNavigate();
  const location = useLocation();
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);
  const toggleWish = useToggleWish();

  function handleClick() {
    handleDetailWishClick({
      isLoggedIn,
      isPending: toggleWish.isPending,
      from: location.pathname + location.search,
      item,
      navigate,
      mutate: (variables) =>
        toggleWish.mutate(variables, {
          onError: (error) => toast.show({ message: getErrorMessage(error), tone: 'danger' }),
        }),
    });
  }

  return (
    <WishButton
      label="관심 목록에 담기"
      isWished={item.isWished}
      isPending={toggleWish.isPending}
      onClick={handleClick}
      className="mr-2"
    />
  );
}
