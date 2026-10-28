/**
 * 화면 하단 고정 바(상세의 관심·가격·예약하기 등). 하단 탭을 숨긴 라우트(handle.hideTabBar)에서 쓴다.
 * 같은 높이의 빈 자리를 본문 끝에 두어 마지막 내용이 바에 가려지지 않게 한다.
 * 높이: 버튼 48px + 위 12px + 아래 16px = 76px(4.75rem)
 */
export default function BottomActionBar({ children }) {
  return (
    <>
      <div aria-hidden="true" className="h-[calc(4.75rem+env(safe-area-inset-bottom))]" />
      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-white pb-[env(safe-area-inset-bottom)]">
        <div className="mx-auto flex max-w-5xl items-center gap-2.5 px-4 pt-3 pb-4">{children}</div>
      </div>
    </>
  );
}
