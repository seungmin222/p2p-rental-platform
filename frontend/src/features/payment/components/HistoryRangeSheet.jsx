import { useState } from 'react';

import { todayInSeoul } from '../../../shared/lib/date';
import { BottomSheet, Button, TextField } from '../../../shared/ui';
import { PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS } from '../constants';
import { validateHistoryRange } from '../paymentHistory';

/**
 * 결제 내역 기간 시트. 열린 동안만 렌더해 열 때마다 지금 필터로 시작한다.
 * 둘 다 비우면 서버 기본(최근 3개월). from > to(to가 비면 오늘)는 적용을 막는다.
 */
export default function HistoryRangeSheet({ open, onClose, range, onApply }) {
  const [from, setFrom] = useState(range.from ?? '');
  const [to, setTo] = useState(range.to ?? '');
  const today = todayInSeoul();
  const error = validateHistoryRange({ from, to }, today);

  return (
    <BottomSheet
      open={open}
      onClose={onClose}
      title="기간 변경"
      footer={
        <>
          <Button variant="secondary" fullWidth onClick={() => onApply({ from: null, to: null })}>
            최근 {PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS}개월로
          </Button>
          <Button
            fullWidth
            disabled={error != null}
            onClick={() => onApply({ from: from || null, to: to || null })}
          >
            적용
          </Button>
        </>
      }
    >
      <p className="text-caption text-ink-muted">
        결제한 날짜로 찾아요. 시작일을 비우면 종료일의 {PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS}개월
        전부터, 종료일을 비우면 오늘까지예요.
      </p>
      <div className="mt-4 flex flex-col gap-3">
        <TextField
          label="시작일"
          type="date"
          value={from}
          max={to || today}
          onChange={(event) => setFrom(event.target.value)}
        />
        <TextField
          label="종료일"
          type="date"
          value={to}
          min={from || undefined}
          onChange={(event) => setTo(event.target.value)}
          error={error ?? undefined}
        />
      </div>
    </BottomSheet>
  );
}
