import { Link } from 'react-router';

import { Badge, Button, Skeleton } from '../../../shared/ui';
import { getVerifyStatus } from '../../region/regionStatus';
import { getErrorMessage } from '../errorMessages';
import { useMe } from '../queries';

const LINK =
  'inline-flex min-h-11 items-center text-caption font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary';

function Avatar({ url }) {
  if (url) {
    return (
      <img src={url} alt="" className="size-14 shrink-0 rounded-full bg-placeholder object-cover" />
    );
  }
  return (
    <div
      aria-hidden="true"
      className="flex size-14 shrink-0 items-center justify-center rounded-full bg-placeholder text-ink-faint"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        className="size-7"
      >
        <circle cx="12" cy="8.5" r="3.5" />
        <path d="M5 20c1.2-3.4 3.8-5 7-5s5.8 1.6 7 5" strokeLinecap="round" />
      </svg>
    </div>
  );
}

/** /me 상단 프로필 요약(GET /api/members/me): 사진·닉네임·대표 동네·인증 상태. 동네가 없으면 동네 설정 링크 */
export default function ProfileSummary() {
  const query = useMe();

  if (query.isPending) {
    return (
      <div className="flex items-center gap-3 py-4" role="status" aria-label="프로필 불러오는 중">
        <Skeleton className="size-14 rounded-full" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-5 w-24" />
          <Skeleton className="h-4 w-36" />
        </div>
      </div>
    );
  }

  if (query.isError) {
    return (
      <div role="alert" className="flex items-center justify-between gap-3 py-4">
        <p className="text-caption text-ink-muted">{getErrorMessage(query.error)}</p>
        <Button variant="secondary" size="sm" onClick={() => query.refetch()}>
          다시 시도
        </Button>
      </div>
    );
  }

  const me = query.data;
  const region = me.primaryRegion;
  const status = region ? getVerifyStatus(region) : null;

  return (
    <section aria-label="내 프로필" className="flex items-center gap-3 py-4">
      <Avatar url={me.profileImageUrl} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="truncate text-heading">{me.nickname}</p>
        {region ? (
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-caption text-ink-muted">
            <span>{region.regionName}</span>
            <Badge tone={status.tone}>{status.label}</Badge>
          </p>
        ) : (
          <p className="flex flex-wrap items-center gap-x-2 text-caption text-ink-muted">
            <span>아직 동네가 없어요</span>
            <Link to="/regions" className={LINK}>
              동네 설정하기
            </Link>
          </p>
        )}
      </div>
    </section>
  );
}
