/**
 * 뒤로 가기를 앱 안에서 할 수 있는지. React Router가 history.state에 넣는 idx(기록 위치)로 판단한다.
 * idx 0 = 앱에 처음 들어온 화면(직접 URL·새 탭). replace(탭 전환 등)는 idx를 유지해 첫 화면이면 그대로 0이다.
 * location.key는 replace하면 'default'에서 새 키로 바뀌어 첫 화면인지 알 수 없다.
 * idx가 없으면(라우터 밖에서 쌓인 기록) 앱 밖으로 나가지 않게 첫 화면으로 본다.
 */
export function canGoBackInApp(historyState) {
  return typeof historyState?.idx === 'number' && historyState.idx > 0;
}
