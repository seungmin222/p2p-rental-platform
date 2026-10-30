import { useMemo, useState } from 'react';

import { addMonths, monthOf, todayInSeoul } from '../../../shared/lib/date';
import { BottomSheet, Button, Calendar, Chip, TextField } from '../../../shared/ui';
import { RENTAL_PERIOD } from '../../rental/constants';
import { DEFAULT_ITEM_SORT, ITEM_SORT } from '../constants';
import { buildFilterDaysInRange } from '../filterCalendar';
import { validatePriceRange } from '../searchParams';

// 필터의 기간 선택은 예약된 날이 없고 지난 날짜만 막힌다
const PERIOD_STATUS_LABELS = {
  AVAILABLE: { label: '선택 가능' },
  UNAVAILABLE: { label: '지난 날짜' },
};
// 오늘로부터 열두 달 앞까지 넘겨 볼 수 있다
const MAX_MONTHS_AHEAD = 11;

const SECTION_TITLE = 'mb-2 text-body font-semibold';

/** 시트가 열릴 때마다 새로 그려져 입력(초안)이 현재 조건에서 다시 시작한다 */
function FilterForm({ filters, onApply, onClose }) {
  const today = todayInSeoul();
  const thisMonth = monthOf(today);
  const [sort, setSort] = useState(filters.sort);
  const [isAvailableOnly, setIsAvailableOnly] = useState(filters.availableOnly);
  const [minText, setMinText] = useState(filters.minPrice == null ? '' : String(filters.minPrice));
  const [maxText, setMaxText] = useState(filters.maxPrice == null ? '' : String(filters.maxPrice));
  const [period, setPeriod] = useState(
    filters.startDate ? { startDate: filters.startDate, endDate: filters.endDate } : null,
  );
  const [month, setMonth] = useState(period ? monthOf(period.startDate) : thisMonth);
  const maxMonth = addMonths(thisMonth, MAX_MONTHS_AHEAD);
  // 달을 넘긴 기간을 고를 수 있게 넘겨 볼 수 있는 달을 모두 넘긴다(Calendar는 받은 days 전체로 범위를 검사한다)
  const days = useMemo(
    () => buildFilterDaysInRange(thisMonth, maxMonth, today),
    [thisMonth, maxMonth, today],
  );

  const price = validatePriceRange(minText, maxText);
  // 시작일만 고른 상태는 적용할 수 없다(서버는 시작·종료를 함께 받는다)
  const isPeriodIncomplete = period != null && period.endDate == null;
  const canApply = price.isValid && !isPeriodIncomplete;

  function handleReset() {
    setSort(DEFAULT_ITEM_SORT);
    setIsAvailableOnly(false);
    setMinText('');
    setMaxText('');
    setPeriod(null);
  }

  function handleApply() {
    onApply({
      sort,
      availableOnly: isAvailableOnly,
      minPrice: price.minPrice,
      maxPrice: price.maxPrice,
      startDate: period?.startDate ?? null,
      endDate: period?.endDate ?? null,
    });
    onClose();
  }

  return (
    // 좌우 8px 안쪽 여백: Calendar 머리의 월 이동 버튼이 -mr-2로 밀려 나가 가로 스크롤이 생기지 않게
    <div className="-mx-2 flex max-h-[calc(85dvh-6rem)] flex-col gap-6 overflow-y-auto px-2 pt-2 pb-1">
      <section>
        <h3 className={SECTION_TITLE}>정렬</h3>
        <div className="flex flex-wrap gap-x-2 gap-y-2.5" role="group" aria-label="정렬">
          {Object.entries(ITEM_SORT).map(([value, { label }]) => (
            <Chip key={value} selected={sort === value} onClick={() => setSort(value)}>
              {label}
            </Chip>
          ))}
        </div>
      </section>

      <section>
        <h3 className={SECTION_TITLE}>일 대여료</h3>
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <TextField
              label="최소"
              inputMode="numeric"
              placeholder="0"
              value={minText}
              onChange={(event) => setMinText(event.target.value)}
              error={price.minError ?? undefined}
            />
          </div>
          <span aria-hidden="true" className="mt-9 text-ink-muted">
            ~
          </span>
          <div className="min-w-0 flex-1">
            <TextField
              label="최대"
              inputMode="numeric"
              placeholder="제한 없음"
              value={maxText}
              onChange={(event) => setMaxText(event.target.value)}
              error={price.maxError ?? undefined}
            />
          </div>
        </div>
      </section>

      <section>
        <div className="flex items-center justify-between">
          <h3 className={SECTION_TITLE}>대여 희망 기간</h3>
          {period && (
            <Button variant="secondary" size="sm" onClick={() => setPeriod(null)}>
              기간 지우기
            </Button>
          )}
        </div>
        <Calendar
          month={month}
          days={days}
          value={period}
          onChange={setPeriod}
          onMonthChange={setMonth}
          minDays={1}
          maxDays={RENTAL_PERIOD.MAX_DAYS}
          statusLabels={PERIOD_STATUS_LABELS}
          minMonth={thisMonth}
          maxMonth={maxMonth}
        />
      </section>

      <section>
        <h3 className={SECTION_TITLE}>대여 가능 여부</h3>
        <Chip selected={isAvailableOnly} onClick={() => setIsAvailableOnly((value) => !value)}>
          대여 가능한 물품만
        </Chip>
      </section>

      <div className="sticky bottom-0 flex gap-2 bg-white pt-2">
        <Button variant="secondary" onClick={handleReset} className="shrink-0">
          초기화
        </Button>
        <Button onClick={handleApply} disabled={!canApply} className="flex-1">
          적용
        </Button>
      </div>
    </div>
  );
}

/** 정렬·가격·기간·대여 가능만을 한 곳에 모은 시트. 적용하면 onApply(조건 일부)로 URL 쿼리를 바꾼다 */
export default function SearchFilterSheet({ open, onClose, filters, onApply }) {
  return (
    <BottomSheet open={open} onClose={onClose} title="필터">
      <FilterForm filters={filters} onApply={onApply} onClose={onClose} />
    </BottomSheet>
  );
}
