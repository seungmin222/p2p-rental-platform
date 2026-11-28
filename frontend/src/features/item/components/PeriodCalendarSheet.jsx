import { useMemo, useState } from 'react';

import { addMonths, monthOf, todayInSeoul } from '../../../shared/lib/date';
import { BottomSheet, Button, Calendar } from '../../../shared/ui';
import { buildFilterDaysInRange } from '../filterCalendar';

// 가능 기간 선택은 예약이 없고 지난 날짜만 막힌다(검색 필터와 같은 범례)
const PERIOD_STATUS_LABELS = {
  AVAILABLE: { label: '선택 가능' },
  UNAVAILABLE: { label: '지난 날짜' },
};
// 오늘로부터 열두 달 앞까지 넘겨 볼 수 있다(검색 필터 SearchFilterSheet와 같은 값)
const MAX_MONTHS_AHEAD = 11;

function minOf(a, b) {
  return a < b ? a : b;
}

function maxOf(a, b) {
  return a > b ? a : b;
}

/** 시트가 열릴 때마다 새로 그려져 초안이 그 줄의 지금 값에서 다시 시작한다 */
function PeriodPicker({ period, onCancel, onConfirm }) {
  const today = todayInSeoul();
  const thisMonth = monthOf(today);
  const initial = period.startDate && period.endDate ? period : null;
  const [value, setValue] = useState(initial);
  const [month, setMonth] = useState(initial ? monthOf(initial.startDate) : thisMonth);
  // 수정 화면의 이미 지난·먼 기간도 보이도록 그 달까지 넓힌다(지난 날짜는 고를 수 없다)
  const minMonth = initial ? minOf(thisMonth, monthOf(initial.startDate)) : thisMonth;
  const maxMonth = maxOf(
    addMonths(thisMonth, MAX_MONTHS_AHEAD),
    initial ? monthOf(initial.endDate) : thisMonth,
  );
  // Calendar는 받은 days 전체로 범위를 검사하므로 넘겨 볼 수 있는 달을 모두 넘긴다
  const days = useMemo(
    () => buildFilterDaysInRange(minMonth, maxMonth, today),
    [minMonth, maxMonth, today],
  );

  return (
    <>
      {/* 좌우 8px 안쪽 여백: Calendar 머리의 월 이동 버튼이 -mr-2로 밀려 나가 가로 스크롤이 생기지 않게 */}
      <div className="-mx-2 px-2 pt-2">
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
          statusLabels={PERIOD_STATUS_LABELS}
          minMonth={minMonth}
          maxMonth={maxMonth}
        />
      </div>
      <div className="mt-5 flex gap-2">
        <Button variant="secondary" onClick={onCancel} className="shrink-0">
          취소
        </Button>
        <Button
          className="flex-1"
          disabled={!value?.endDate}
          onClick={() => onConfirm({ startDate: value.startDate, endDate: value.endDate })}
        >
          확인
        </Button>
      </div>
    </>
  );
}

/**
 * 가능 기간 한 줄의 기간을 고르는 시트. 확인을 눌러야 onConfirm으로 반영하고,
 * 닫기·취소는 초안(시작일만 고른 상태 포함)을 버린다.
 */
export default function PeriodCalendarSheet({ open, title, period, onClose, onConfirm }) {
  return (
    <BottomSheet open={open} onClose={onClose} title={title}>
      {open && <PeriodPicker period={period} onCancel={onClose} onConfirm={onConfirm} />}
    </BottomSheet>
  );
}
