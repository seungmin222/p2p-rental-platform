import { useMemo, useState } from 'react';

import { monthOf, todayInSeoul } from '../../../shared/lib/date';
import { BottomSheet, Button, Calendar } from '../../../shared/ui';
import { PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS } from '../constants';
import { buildHistoryDays } from '../paymentHistory';

// 결제 내역 기간은 지난 날짜를 고른다. 오늘 이후만 막힌다
const RANGE_STATUS_LABELS = {
  AVAILABLE: { label: '선택 가능' },
  UNAVAILABLE: { label: '선택할 수 없는 날' },
};

// 지금 조건 → 캘린더 값. 종료일만 있는 조건(주소로만 만들 수 있음)은 고른 것 없이 시작한다
function toCalendarValue({ from, to }) {
  return from ? { startDate: from, endDate: to ?? null } : null;
}

/**
 * 결제 내역 기간 시트. 열린 동안만 렌더해 열 때마다 지금 필터로 시작한다.
 * 공용 Calendar로 고른다(오늘 이후 선택 불가, 최대 일수 없음). 시작·종료를 모두 고르면 그 범위,
 * 시작일만 고르면 시작일 ~ 오늘(to를 비워 보낸다), "최근 3개월로"는 서버 기본 기간.
 */
export default function HistoryRangeSheet({ open, onClose, range, onApply }) {
  const today = todayInSeoul();
  const thisMonth = monthOf(today);
  const [value, setValue] = useState(() => toCalendarValue(range));
  const [month, setMonth] = useState(value ? monthOf(value.startDate) : thisMonth);
  // 과거 쪽 한계가 없어 보고 있는 달(또는 고른 시작일의 달)부터 이번 달까지 넘긴다
  const fromMonth = value && monthOf(value.startDate) < month ? monthOf(value.startDate) : month;
  const days = useMemo(() => buildHistoryDays(fromMonth, today), [fromMonth, today]);

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
            disabled={value == null}
            onClick={() => onApply({ from: value.startDate, to: value.endDate ?? null })}
          >
            적용
          </Button>
        </>
      }
    >
      <p className="text-caption text-ink-muted">
        결제한 날짜로 찾아요. 시작일만 고르면 오늘까지 찾아요.
      </p>
      {/* 좌우 8px 안쪽 여백: Calendar 머리의 월 이동 버튼이 -mr-2로 밀려 나가 가로 스크롤이 생기지 않게 */}
      <div className="-mx-2 mt-3 px-2">
        <Calendar
          month={month}
          days={days}
          value={value}
          onChange={setValue}
          onMonthChange={setMonth}
          minDays={1}
          maxDays={null}
          hint="시작일과 종료일을 차례로 눌러 주세요"
          endLabel="종료일"
          statusLabels={RANGE_STATUS_LABELS}
          maxMonth={thisMonth}
        />
      </div>
    </BottomSheet>
  );
}
