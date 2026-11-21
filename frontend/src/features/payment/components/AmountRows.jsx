/** 금액 줄 목록(getPaymentHistoryView·getSettlementView의 rows). tone 'danger'면 빨간 글씨 */
export default function AmountRows({ rows }) {
  return (
    <dl className="mt-1.5 flex flex-col gap-0.5 text-caption">
      {rows.map((row) => (
        <div key={row.label} className="flex justify-between gap-2">
          <dt className="text-ink-muted">{row.label}</dt>
          <dd className={`tabular-nums ${row.tone === 'danger' ? 'text-danger' : ''}`}>
            {row.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}
