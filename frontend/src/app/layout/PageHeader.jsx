import BackButton from './BackButton';

/** 뒤로 가기 + 제목 + 우측 액션. 앱에 처음 들어온 화면(이전 기록 없음)이면 홈으로 간다. */
export default function PageHeader({ title, action }) {
  return (
    <header className="sticky top-0 z-10 -mx-4 flex h-14 items-center gap-1 border-b border-divider bg-white pr-4 pl-1">
      <BackButton />
      <h1 className="min-w-0 flex-1 truncate text-lg font-bold">{title}</h1>
      {action && <div className="flex shrink-0 items-center">{action}</div>}
    </header>
  );
}
