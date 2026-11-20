import { Link } from 'react-router';

import { formatElapsed } from '../../../shared/lib/format';
import { Badge } from '../../../shared/ui';
import { getProfilePath } from '../../profile/profileDisplay';
import { COMPLIMENT, SATISFACTION } from '../constants';

// writerRole: 작성자가 그 대여에서 맡은 역할(RENTER면 대여자가 쓴 후기)
const WRITER_ROLE_LABEL = { RENTER: '대여자', OWNER: '제공자' };
const SATISFACTION_TONE = { BAD: 'gray', GOOD: 'green', BEST: 'green' };

/** 받은 후기 한 줄(ReceivedReviewResponse). 탈퇴한 작성자(writer.isWithdrawn)는 "탈퇴한 사용자" */
export default function ReceivedReviewItem({ review }) {
  const { writer } = review;
  const isWithdrawn = Boolean(writer.isWithdrawn);
  const nickname = isWithdrawn ? '탈퇴한 사용자' : writer.nickname;
  // 작성자 닉네임 → 그 사람의 프로필. 탈퇴한 작성자는 링크 없음
  const profilePath = getProfilePath(writer);
  return (
    <article className="flex gap-3 py-3.5">
      {!isWithdrawn && writer.profileImageUrl ? (
        <img
          src={writer.profileImageUrl}
          alt=""
          loading="lazy"
          className="size-10 shrink-0 rounded-full object-cover"
        />
      ) : (
        <span aria-hidden="true" className="size-10 shrink-0 rounded-full bg-surface" />
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex items-center gap-2">
          {profilePath ? (
            <Link
              to={profilePath}
              className="truncate font-semibold underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
            >
              {nickname}
            </Link>
          ) : (
            <span className={`truncate font-semibold ${isWithdrawn ? 'text-ink-muted' : ''}`}>
              {nickname}
            </span>
          )}
          <span className="shrink-0 text-xs text-ink-muted">
            {WRITER_ROLE_LABEL[review.writerRole] ?? ''} · {formatElapsed(review.createdAt)}
          </span>
          <Badge tone={SATISFACTION_TONE[review.satisfaction]} className="ml-auto shrink-0">
            {SATISFACTION[review.satisfaction]?.label ?? review.satisfaction}
          </Badge>
        </div>
        {review.compliments.length > 0 && (
          <ul className="flex flex-wrap gap-1.5" aria-label="칭찬">
            {review.compliments.map((code) => (
              <li
                key={code}
                className="rounded-full bg-primary-soft px-2.5 py-0.5 text-xs text-primary"
              >
                {COMPLIMENT[code]?.label ?? code}
              </li>
            ))}
          </ul>
        )}
        {review.content && (
          <p className="text-body break-words whitespace-pre-line">{review.content}</p>
        )}
      </div>
    </article>
  );
}
