import Button from './Button';

/** 조회 실패. message는 보통 ApiError.message, onRetry는 refetch. */
export default function ErrorState({ title = '불러오지 못했어요', message, onRetry }) {
  return (
    <div role="alert" className="flex flex-col items-center px-4 py-16 text-center">
      <p className="text-heading">{title}</p>
      {message && <p className="mt-1 text-caption text-ink-muted">{message}</p>}
      {onRetry && (
        <Button variant="secondary" className="mt-4" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  );
}
