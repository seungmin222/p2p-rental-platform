/**
 * 화면 하단 고정 바(상세의 관심·가격·예약하기 등). 하단 탭을 숨긴 라우트(handle.hideTabBar)에서 쓴다.
 * 같은 높이의 빈 자리를 본문 끝에 두어 마지막 내용이 바에 가려지지 않게 한다.
 */
export default function BottomActionBar({ children }) {
  return (
    <>
      <div aria-hidden="true" className="h-[calc(4.5rem+env(safe-area-inset-bottom))]" />
      <div className="fixed inset-x-0 bottom-0 z-20 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)]">
        <div className="mx-auto flex min-h-[4.5rem] max-w-5xl items-center gap-3 px-4">
          {children}
        </div>
      </div>
    </>
  );
}
