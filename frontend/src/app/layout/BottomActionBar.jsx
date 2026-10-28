/**
 * 화면 하단 고정 바(상세의 관심·가격·예약하기 등). 하단 탭을 숨긴 라우트(handle.hideTabBar)에서
 * 페이지의 **마지막 요소**로 둔다. sticky라 높이가 달라도 본문을 가리지 않고, 본문이 짧으면
 * mt-auto로 화면 아래에 붙는다(AppLayout의 main이 세로 flex).
 * className은 안쪽 배치(기본 가로 정렬)를 바꿀 때 쓴다. 예: 안내 문구 + 버튼 세로 쌓기 "flex flex-col gap-2"
 */
export default function BottomActionBar({ className = 'flex items-center gap-2.5', children }) {
  return (
    <div className="sticky bottom-0 z-20 -mx-4 mt-auto border-t border-line bg-white pb-[env(safe-area-inset-bottom)]">
      <div className={`px-4 pt-3 pb-4 ${className}`}>{children}</div>
    </div>
  );
}
