import { useEffect, useState } from 'react';

/**
 * 목록 마지막의 sentinel이 보이면 다음 페이지를 부른다(IntersectionObserver). 돌려준 callback ref를 sentinel에 단다.
 * 상태가 바뀔 때마다 observer를 새로 만들어, 다음 페이지를 받은 뒤에도 sentinel이 계속 보이면 이어서 부른다
 * (observer는 만들 때 현재 교차 상태로 한 번 알린다). 다음 페이지가 실패하면 멈추고 "다시 시도"를 기다린다.
 * 가져오는 중(isFetching: 다음 페이지뿐 아니라 무효화 뒤 재조회 포함)에는 observer를 만들지 않는다 —
 * 재조회 중에 fetchNextPage를 부르면 재조회가 취소되고 이전 페이지에 새 페이지가 이어 붙는다.
 * 스크롤 영역(시트 등) 안이어도 화면에 실제로 보이는지로 판단하므로 root를 따로 넘기지 않는다.
 */
export function useInfiniteScroll({
  hasNextPage,
  isFetching,
  isFetchNextPageError,
  fetchNextPage,
}) {
  const [sentinel, setSentinel] = useState(null);

  useEffect(() => {
    if (!sentinel || !hasNextPage || isFetching || isFetchNextPageError) {
      return undefined;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          fetchNextPage();
        }
      },
      // 끝에 닿기 조금 전에 부른다
      { rootMargin: '0px 0px 200px 0px' },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [sentinel, hasNextPage, isFetching, isFetchNextPageError, fetchNextPage]);

  return setSentinel;
}
