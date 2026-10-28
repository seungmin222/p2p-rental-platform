/** 목록이 비었을 때. action은 버튼 등 선택 요소. */
export default function EmptyState({ title, description, action }) {
  return (
    <div className="flex flex-col items-center px-4 py-16 text-center">
      <p className="text-base font-semibold text-gray-900">{title}</p>
      {description && <p className="mt-1 text-sm text-gray-500">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
