/** 하단 탭(모바일)과 상단 헤더(PC)가 같은 메뉴를 쓴다. 알림은 아직 "준비 중" 화면으로 연결된다. 채팅은 안 읽은 수 배지(badge) */
export const NAV_ITEMS = [
  { to: '/', label: '홈', icon: 'home', end: true },
  { to: '/search', label: '검색', icon: 'search' },
  { to: '/chats', label: '채팅', icon: 'chat', badge: 'chat' },
  { to: '/notifications', label: '알림', icon: 'bell' },
  { to: '/me', label: '나의 대여', icon: 'list' },
];
