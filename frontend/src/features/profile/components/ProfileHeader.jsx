import { Skeleton } from '../../../shared/ui';
import { formatJoinedMonth, WITHDRAWN_NICKNAME } from '../profileDisplay';

function Avatar({ url }) {
  if (url) {
    return (
      <img src={url} alt="" className="size-16 shrink-0 rounded-full bg-placeholder object-cover" />
    );
  }
  return <div aria-hidden="true" className="size-16 shrink-0 rounded-full bg-primary-soft" />;
}

export function ProfileHeaderSkeleton() {
  return (
    <div aria-busy="true" aria-label="프로필 불러오는 중" className="flex items-center gap-4 py-5">
      <Skeleton className="size-16 rounded-full" />
      <div className="flex flex-1 flex-col gap-2">
        <Skeleton className="h-5 w-28" />
        <Skeleton className="h-4 w-44" />
      </div>
    </div>
  );
}

/**
 * 프로필 머리(MemberProfileResponse): 사진·닉네임·대표 동네·가입 월·거래 횟수.
 * 탈퇴 회원은 "탈퇴한 사용자"만(상세 비공개). 신뢰 점수·칭찬 항목별 횟수는 REQ-054 때 더한다.
 */
export default function ProfileHeader({ profile }) {
  if (profile.isWithdrawn) {
    return (
      <section aria-label="프로필" className="flex items-center gap-4 py-5">
        <span aria-hidden="true" className="size-16 shrink-0 rounded-full bg-surface" />
        <div className="flex flex-col gap-1">
          <p className="text-heading text-ink-muted">{WITHDRAWN_NICKNAME}</p>
          <p className="text-caption text-ink-muted">탈퇴한 사용자의 정보는 볼 수 없어요</p>
        </div>
      </section>
    );
  }
  const meta = [profile.regionName, formatJoinedMonth(profile.joinedAt)].filter(Boolean);
  return (
    <section aria-label="프로필" className="flex items-center gap-4 py-5">
      <Avatar url={profile.profileImageUrl} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <p className="truncate text-heading">{profile.nickname}</p>
        {meta.length > 0 && <p className="text-caption text-ink-muted">{meta.join(' · ')}</p>}
        <p className="text-caption">
          거래 <b className="tabular-nums">{profile.completedRentalCount ?? 0}</b>회
        </p>
      </div>
    </section>
  );
}
