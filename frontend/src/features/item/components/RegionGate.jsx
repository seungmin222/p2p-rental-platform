import { ButtonLink, EmptyState, ErrorState, Skeleton } from '../../../shared/ui';

/**
 * 홈·검색에서 동네를 정하지 못했을 때의 화면(useFeedRegion의 status가 ready가 아닐 때).
 * 동네가 없으면 안내만 하고 자동으로 옮기지 않는다 — 자동 이동하면 /regions에서 뒤로 가기가 다시 홈으로 와 루프가 된다.
 */
export default function RegionGate({ region }) {
  if (region.status === 'loading') {
    return (
      <div aria-busy="true" aria-label="불러오는 중" className="flex flex-col gap-3 py-4">
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-26 w-full" />
        <Skeleton className="h-26 w-full" />
      </div>
    );
  }
  if (region.status === 'error') {
    return <ErrorState message="내 동네를 불러오지 못했어요" onRetry={region.retry} />;
  }
  return (
    <EmptyState
      title="동네를 설정하면 이웃의 물품을 볼 수 있어요"
      description="사는 동네를 고르면 가까운 이웃이 빌려주는 물품이 보여요"
      action={<ButtonLink to="/regions">동네 설정하기</ButtonLink>}
    />
  );
}
