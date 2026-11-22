import { Badge } from '../../../shared/ui';
import AmountRows from '../../payment/components/AmountRows';
import { DEDUCTION_PROPOSAL_STATUS, DEPOSIT_STATUS } from '../../payment/constants';

/** 증빙 사진 줄. 누르면 원본을 새 탭으로 연다 */
function PhotoRow({ urls, label }) {
  if (urls.length === 0) {
    return null;
  }
  return (
    <div className="flex flex-col gap-1">
      <span className="text-xs text-ink-muted">{label}</span>
      <div className="flex flex-wrap gap-1">
        {urls.map((url, index) => (
          <a
            key={url}
            href={url}
            target="_blank"
            rel="noreferrer"
            aria-label={`${label} ${index + 1} 크게 보기`}
            className="block size-14 overflow-hidden rounded-[10px] bg-placeholder focus-visible:outline-2 focus-visible:outline-primary"
          >
            <img src={url} alt="" loading="lazy" className="size-full object-cover" />
          </a>
        ))}
      </div>
    </div>
  );
}

/** 협의 중(HELD)인 최신 차감 제안(deposit.js getProposalView) */
function ProposalBox({ proposal }) {
  const status =
    proposal.state === 'CLOSED'
      ? { label: '자동 수락 대기', tone: 'amber' }
      : DEDUCTION_PROPOSAL_STATUS[proposal.statusCode];
  return (
    <section
      aria-label="차감 제안"
      className="mt-2.5 flex flex-col gap-2 rounded-lg bg-white p-3 text-caption"
    >
      <div className="flex items-center justify-between gap-2">
        <p className="font-semibold">차감 제안 · {proposal.gradeLabel}</p>
        <Badge tone={status?.tone}>{status?.label ?? proposal.statusCode}</Badge>
      </div>
      <p className="text-price font-bold tabular-nums">{proposal.amountText}</p>
      <p className="break-words whitespace-pre-line">{proposal.reason}</p>
      {proposal.proposedAtText && (
        <p className="text-xs text-ink-muted">{proposal.proposedAtText} 제안</p>
      )}
      <PhotoRow urls={proposal.evidenceImageUrls} label="손상 사진" />
      <PhotoRow urls={proposal.receiptImageUrls} label="견적서·영수증" />
      {proposal.deadlineText && (
        <p className="font-semibold text-accent-strong">{proposal.deadlineText}</p>
      )}
    </section>
  );
}

/** 대여 상세 보증금 카드(REQ-025·049·023). view는 deposit.js getDepositView */
export default function DepositCard({ view }) {
  const status = DEPOSIT_STATUS[view.status];
  const isDanger = view.status === 'HELD' || view.status === 'REFUND_FAILED';
  return (
    <section
      aria-label="보증금"
      className={`rounded-xl px-3.5 py-3 ${isDanger ? 'bg-surface' : 'bg-accent-soft'}`}
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-caption font-semibold text-accent-strong">{view.title}</p>
        <Badge tone={status?.tone} className="shrink-0">
          {status?.label ?? view.status}
        </Badge>
      </div>
      <AmountRows rows={view.rows} />
      {view.notes.length > 0 && (
        <ul className="mt-1.5 flex flex-col gap-0.5 text-caption text-ink-muted">
          {view.notes.map((note) => (
            <li key={note}>{note}</li>
          ))}
        </ul>
      )}
      {view.proposal && <ProposalBox proposal={view.proposal} />}
    </section>
  );
}
