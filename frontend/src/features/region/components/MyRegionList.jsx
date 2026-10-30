import { useState } from 'react';

import {
  Badge,
  Button,
  ConfirmModal,
  EmptyState,
  ErrorState,
  Skeleton,
  toast,
} from '../../../shared/ui';
import { getErrorMessage } from '../errorMessages';
import { getDeleteNotice, getVerifyActionLabel } from '../myRegions';
import {
  useDeleteRegion,
  useRegionMutating,
  useSetPrimaryRegion,
  useVerifyRegion,
} from '../queries';
import { getVerifyStatus } from '../regionStatus';

function showError(error) {
  toast.show({ message: getErrorMessage(error), tone: 'danger' });
}

/** 지금 진행 중인 뮤테이션이 이 동네 것인지(버튼 스피너용). variables에는 id만 있다 */
function isPendingFor(mutation, memberRegionId) {
  return mutation.isPending && mutation.variables?.memberRegionId === memberRegionId;
}

function RegionRow({ region, isLocked, verify, setPrimary, onVerify, onSetPrimary, onDelete }) {
  const status = getVerifyStatus(region);
  return (
    <li className="flex flex-col gap-3 py-4">
      <div className="flex flex-col gap-1">
        <p className="flex flex-wrap items-center gap-2">
          <span className="text-heading">{region.regionName}</span>
          {region.isPrimary && <Badge tone="ink">대표</Badge>}
          <Badge tone={status.tone}>{status.label}</Badge>
        </p>
        <p className="text-caption text-ink-muted">{region.fullName}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={isLocked}
          isLoading={isPendingFor(verify, region.memberRegionId)}
          onClick={() => onVerify(region)}
        >
          {getVerifyActionLabel(region)}
        </Button>
        {!region.isPrimary && (
          <Button
            variant="secondary"
            size="sm"
            disabled={isLocked}
            isLoading={isPendingFor(setPrimary, region.memberRegionId)}
            onClick={() => onSetPrimary(region)}
          >
            대표로
          </Button>
        )}
        <Button variant="danger" size="sm" disabled={isLocked} onClick={() => onDelete(region)}>
          삭제
        </Button>
      </div>
    </li>
  );
}

/**
 * 내 동네 목록(서버 순서 그대로, 대표 먼저)과 줄마다 인증·대표 변경·삭제.
 * 동네 뮤테이션이 하나라도 진행 중이면(추가 포함) 모든 줄의 버튼을 막는다: 대표 변경 직후 목록이 갱신되기 전에
 * 다른 버튼을 눌러 지난 목록 값(삭제의 isPrimary 등)으로 요청하지 않게.
 */
export default function MyRegionList({ query }) {
  const verify = useVerifyRegion();
  const setPrimary = useSetPrimaryRegion();
  const remove = useDeleteRegion();
  const isMutating = useRegionMutating();
  const [deleteTarget, setDeleteTarget] = useState(null);

  if (query.isPending) {
    return (
      <div role="status" aria-label="내 동네 불러오는 중" className="flex flex-col gap-3 py-4">
        <Skeleton className="h-20 w-full rounded-xl" />
        <Skeleton className="h-20 w-full rounded-xl" />
      </div>
    );
  }
  if (query.isError) {
    return <ErrorState message={getErrorMessage(query.error)} onRetry={() => query.refetch()} />;
  }

  const regions = query.data;
  if (regions.length === 0) {
    return (
      <EmptyState title="아직 동네가 없어요" description="아래에서 동네를 찾아 추가해 주세요" />
    );
  }

  // 위치 권한 거부도 버튼을 막지 않는다: 브라우저 설정에서 다시 켤 수 있어 누를 때마다 다시 시도하고 토스트로 안내한다
  function handleVerify(region) {
    verify.mutate(
      { memberRegionId: region.memberRegionId },
      {
        onSuccess: () => toast.show({ message: `${region.regionName} 인증을 완료했어요` }),
        onError: showError,
      },
    );
  }

  function handleSetPrimary(region) {
    setPrimary.mutate(
      { memberRegionId: region.memberRegionId },
      {
        onSuccess: () => toast.show({ message: `${region.regionName}을 대표 동네로 정했어요` }),
        onError: showError,
      },
    );
  }

  function handleDelete() {
    const target = deleteTarget;
    remove.mutate(
      { memberRegionId: target.memberRegionId, isPrimary: target.isPrimary },
      {
        onSuccess: () => toast.show({ message: `${target.regionName}을 삭제했어요` }),
        onError: showError,
        onSettled: () => setDeleteTarget(null),
      },
    );
  }

  return (
    <>
      <ul aria-label="내 동네 목록" className="divide-y divide-divider">
        {regions.map((region) => (
          <RegionRow
            key={region.memberRegionId}
            region={region}
            isLocked={isMutating}
            verify={verify}
            setPrimary={setPrimary}
            onVerify={handleVerify}
            onSetPrimary={handleSetPrimary}
            onDelete={setDeleteTarget}
          />
        ))}
      </ul>
      {deleteTarget && (
        <ConfirmModal
          open
          title={`${deleteTarget.regionName}을 삭제할까요?`}
          confirmLabel="삭제"
          danger
          isLoading={remove.isPending}
          onClose={() => setDeleteTarget(null)}
          onConfirm={handleDelete}
        >
          <p className="text-body text-ink-muted">{getDeleteNotice(regions, deleteTarget)}</p>
        </ConfirmModal>
      )}
    </>
  );
}
