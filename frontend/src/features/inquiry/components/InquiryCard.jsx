import { Link } from 'react-router';

import { Badge } from '../../../shared/ui';
import { formatDateTime } from '../../payment/paymentHistory';
import { INQUIRY_STATUS, INQUIRY_TYPE } from '../constants';

/**
 * 내 문의 한 줄(InquiryResponse). 종류·처리 상태 배지, 내용(3줄까지), 접수 일시, 연결한 대여 링크.
 * 응답에 물품 정보가 없어 대여는 번호로만 보인다. 관리자 답변(REQ-057)은 아직 없다.
 */
export default function InquiryCard({ inquiry }) {
  const type = INQUIRY_TYPE[inquiry.type];
  const status = INQUIRY_STATUS[inquiry.status];
  return (
    <article className="flex flex-col gap-1.5 py-3.5">
      <div className="flex items-center justify-between gap-2">
        {type && <Badge tone={type.tone}>{type.label}</Badge>}
        {status && (
          <Badge tone={status.tone} className="shrink-0">
            {status.label}
          </Badge>
        )}
      </div>
      <p className="line-clamp-3 text-body break-words whitespace-pre-wrap">{inquiry.content}</p>
      <div className="flex items-center justify-between gap-2 text-xs text-ink-muted">
        <span>{formatDateTime(inquiry.createdAt)} 접수</span>
        {inquiry.rentalId != null && (
          <Link
            to={`/rentals/${inquiry.rentalId}`}
            className="inline-flex min-h-11 items-center underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary"
          >
            대여 #{inquiry.rentalId} 보기
          </Link>
        )}
      </div>
    </article>
  );
}
