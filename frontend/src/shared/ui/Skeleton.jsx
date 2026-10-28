/** 목록 로딩 자리. 크기·모양은 className으로(예: "h-24 w-24 rounded-xl"). */
export default function Skeleton({ className = '' }) {
  return (
    <div aria-hidden="true" className={`animate-pulse rounded-md bg-placeholder ${className}`} />
  );
}
