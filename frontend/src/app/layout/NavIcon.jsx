// 라이브러리 없이 인라인 SVG(시안 Main.html 하단 탭, 22px·선 1.6). 라벨이 함께 보이므로 장식(aria-hidden).
// 선택 탭은 채운 아이콘. 시안에는 홈의 채운 모양만 있어 나머지는 같은 외곽을 채우고,
// 채울 수 없는 검색은 선을 굵게, 나의 대여는 사각형을 채우고 줄을 흰색으로 뺀다.
function Shape({ name, active }) {
  const fill = active ? 'currentColor' : 'none';
  switch (name) {
    case 'home':
      return <path d="M4 11l8-7 8 7v9h-5v-6h-6v6H4z" fill={fill} />;
    case 'search':
      return (
        <g strokeWidth={active ? 2.2 : 1.6}>
          <circle cx="11" cy="11" r="6.5" />
          <path d="M16 16l4.5 4.5" />
        </g>
      );
    case 'chat':
      return <path d="M4 5h16v11H9l-5 4z" fill={fill} />;
    case 'bell':
      return (
        <>
          <path d="M6 16V11a6 6 0 0112 0v5l2 2H4z" fill={fill} />
          <path d="M10 21h4" />
        </>
      );
    case 'list':
      return (
        <>
          <rect x="4" y="4" width="16" height="16" rx="3" fill={fill} />
          <path d="M8 9h8M8 13h8M8 17h5" stroke={active ? 'white' : 'currentColor'} />
        </>
      );
    default:
      return null;
  }
}

export default function NavIcon({ name, active }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-[22px]"
    >
      <Shape name={name} active={active} />
    </svg>
  );
}
