import { formatDailyPrice, formatPrice } from '../../../shared/lib/format';
import { PAYMENT_ENABLED } from '../constants';

function Row({ label, children, className = '' }) {
  return (
    <div className={`flex items-center justify-between gap-3 ${className}`}>
      <span>{label}</span>
      <span className="text-right">{children}</span>
    </div>
  );
}

/**
 * 금액 요약 카드(시안 Booking.html). rentalDays가 없으면(기간 선택 전) 일 대여료와 보증금만 보인다.
 * 결제가 꺼져 있으면 실제 결제가 없으므로 합계 이름을 "합계"로 둔다.
 */
export default function AmountSummary({
  pricePerDay,
  rentalDays,
  rentalFee,
  deposit,
  totalAmount,
}) {
  const hasPeriod = rentalDays != null;
  return (
    <section
      aria-label="금액"
      className="flex flex-col gap-2 rounded-xl bg-surface p-3.5 text-body tabular-nums"
    >
      {hasPeriod ? (
        <Row label={`대여료 ${formatPrice(pricePerDay)} × ${rentalDays}일`}>
          {formatPrice(rentalFee)}
        </Row>
      ) : (
        <Row label="일 대여료">{formatDailyPrice(pricePerDay)}</Row>
      )}
      <Row label="보증금">
        <span className="font-semibold text-accent-strong">
          {deposit === 0 ? '보증금 없음' : formatPrice(deposit)}
        </span>
      </Row>
      {hasPeriod && (
        <>
          <div aria-hidden="true" className="my-0.5 h-px bg-line" />
          <Row
            label={PAYMENT_ENABLED && totalAmount > 0 ? '총 결제 금액' : '합계'}
            className="text-price"
          >
            {formatPrice(totalAmount)}
          </Row>
        </>
      )}
    </section>
  );
}
