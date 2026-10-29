import { useInfiniteScroll } from '../hooks/useInfiniteScroll';
import Button from './Button';
import Spinner from './Spinner';

/**
 * 무한 스크롤 목록의 끝. query는 useInfiniteQuery 결과.
 * 끝에 닿으면 다음 페이지를 부르고(useInfiniteScroll), 불러오는 중엔 스피너, 실패하면 목록은 그대로 두고 "다시 시도".
 */
export default function InfiniteListFooter({ query }) {
  const { hasNextPage, isFetching, isFetchingNextPage, isFetchNextPageError, fetchNextPage } =
    query;
  const sentinelRef = useInfiniteScroll({
    hasNextPage,
    isFetching,
    isFetchNextPageError,
    fetchNextPage,
  });

  if (!hasNextPage) {
    return null;
  }
  return (
    <div ref={sentinelRef} className="flex min-h-16 flex-col items-center justify-center py-3">
      {isFetchingNextPage && <Spinner label="더 불러오는 중" />}
      {!isFetchingNextPage && isFetchNextPageError && (
        <div role="alert" className="flex flex-col items-center gap-2">
          <p className="text-caption text-ink-muted">목록을 더 불러오지 못했어요</p>
          <Button variant="secondary" size="sm" onClick={() => fetchNextPage()}>
            다시 시도
          </Button>
        </div>
      )}
    </div>
  );
}
