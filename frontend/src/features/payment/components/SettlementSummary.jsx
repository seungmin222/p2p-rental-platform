import { formatPrice } from '../../../shared/lib/format';
import { SETTLEMENT_PAYOUT_DELAY_BUSINESS_DAYS } from '../constants';

/** 정산 합계(SettlementListResponse). 보류(HELD)는 지급 예정에 들어가지 않는다 */
export default function SettlementSummary({ totals }) {
  const cells = [
    { label: '지급 예정', value: totals.scheduledTotal },
    { label: '보류', value: totals.heldTotal },
    { label: '지급 완료', value: totals.paidTotal },
  ];
  return (
    <section aria-label="정산 합계" className="mt-4 rounded-xl bg-surface p-4">
      <dl className="grid grid-cols-3 gap-2 text-center">
        {cells.map((cell) => (
          <div key={cell.label} className="flex flex-col gap-0.5">
            <dt className="text-caption text-ink-muted">{cell.label}</dt>
            <dd className="font-semibold tabular-nums">{formatPrice(cell.value)}</dd>
          </div>
        ))}
      </dl>
      {totals.heldTotal > 0 && (
        <p className="mt-3 text-xs text-ink-muted">
          보류 금액은 분쟁이 끝나면 지급 예정으로 돌아가요
        </p>
      )}
      <p className="mt-1 text-xs text-ink-muted">
        거래 완료(수수료 있는 취소)일로부터 영업일 {SETTLEMENT_PAYOUT_DELAY_BUSINESS_DAYS}일 뒤(주말
        제외)에 지급돼요
      </p>
    </section>
  );
}
