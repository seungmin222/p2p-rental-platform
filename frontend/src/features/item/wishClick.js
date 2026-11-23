import { toLoginPath } from '../../app/loginPath';

/**
 * 상세 하트(REQ-022) 누름 처리. 비회원이면 지금 주소로 돌아오게 로그인 화면으로 보내고,
 * 요청 중이면 무시하고(추가·해제가 엇갈려 도착하면 최종 상태가 꼬인다), 아니면 반대 상태로 요청한다.
 */
export function handleDetailWishClick({ isLoggedIn, isPending, from, item, navigate, mutate }) {
  if (!isLoggedIn) {
    navigate(toLoginPath(from));
    return;
  }
  if (isPending) {
    return;
  }
  mutate({ itemId: item.itemId, wished: !item.isWished });
}
