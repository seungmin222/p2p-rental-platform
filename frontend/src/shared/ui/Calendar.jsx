import { useMemo, useState } from 'react';

import {
  addMonths,
  buildMonthGrid,
  formatDateRange,
  formatMonth,
  formatMonthDay,
  weekdayOf,
} from '../lib/date';
import {
  DAY_STATUS,
  rangePosition,
  SELECT_REJECT_REASON,
  selectDate,
  statusOf,
  toStatusMap,
} from './calendarSelection';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

const STATUS_TEXT = {
  [DAY_STATUS.AVAILABLE]: 'text-ink',
  [DAY_STATUS.BOOKED]: 'text-ink-faint line-through',
  [DAY_STATUS.UNAVAILABLE]: 'text-ink-disabled',
};

// 범위 띠는 칸(button) 전체에, 1일·시작일만 고른 상태는 안쪽 원에 칠한다.
const POSITION_CELL = {
  start: 'rounded-l-full bg-primary font-bold text-white',
  end: 'rounded-r-full bg-primary font-bold text-white',
  middle: 'bg-primary-soft font-semibold text-primary',
};

function positionSuffix(position, value) {
  switch (position) {
    case 'single':
      return value.endDate ? ', 시작일·반납일' : ', 시작일';
    case 'start':
      return ', 시작일';
    case 'end':
      return ', 반납일';
    default:
      return '';
  }
}

// 범위 거부 시 누른 날이 새 시작일이 된다(calendarSelection.selectDate)
const RESTART_HINT = '선택한 날부터 다시 골라 주세요';

function rejectMessage({ reason, status }, { minDays, maxDays }) {
  switch (reason) {
    case SELECT_REJECT_REASON.DAY_NOT_AVAILABLE:
      return status === DAY_STATUS.BOOKED
        ? '예약된 날짜는 선택할 수 없어요'
        : '선택할 수 없는 날짜예요';
    case SELECT_REJECT_REASON.RANGE_INCLUDES_UNAVAILABLE:
      return `중간에 예약할 수 없는 날짜가 있어요. ${RESTART_HINT}`;
    case SELECT_REJECT_REASON.TOO_SHORT:
      return `최소 ${minDays}일부터 대여할 수 있어요`;
    case SELECT_REJECT_REASON.TOO_LONG:
      return `최대 ${maxDays}일까지 대여할 수 있어요. ${RESTART_HINT}`;
    default:
      return '';
  }
}

function ChevronIcon({ direction }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4"
    >
      <path d={direction === 'prev' ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
    </svg>
  );
}

const NAV_BUTTON =
  'flex size-11 items-center justify-center rounded-full text-ink-muted hover:bg-surface focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:text-ink-disabled disabled:hover:bg-transparent';

/**
 * 대여 기간 범위 선택 캘린더(REQ-012, 시안 Booking.html). API 호출은 하지 않는다.
 *
 * - `days`는 여러 달을 합친 배열이어도 된다. 예약이 달을 넘을 수 있어(10/30~11/2) 범위 검사는 받은
 *   days 전체로 한다. 호출부(feat/fe-rental-pages)가 지나간 달의 응답을 캐시에서 합쳐 넘긴다.
 *   days에 없는 날짜는 UNAVAILABLE로 본다.
 * - `minDays`·`maxDays`는 정책 값을 호출부가 넘긴다(예약: RENTAL_PERIOD). 연장(REQ-038, 회당 7일)도
 *   같은 컴포넌트에 다른 값을 넘겨 쓸 수 있게 기본값을 두지 않는다. 안내 문구는 "대여" 기준이다.
 * - 월을 이동해도 value는 유지된다. 거부 사유는 아래 줄에 표시하고, 범위 거부(사이 예약 불가·최대 초과)는
 *   누른 날을 새 시작일로 onChange한다. 누른 날 자체가 예약 불가이거나 최소 미만이면 value를 바꾸지 않는다.
 *
 * @param {object} props
 * @param {string} props.month 보이는 달 "YYYY-MM"
 * @param {{date: string, status: 'AVAILABLE' | 'BOOKED' | 'UNAVAILABLE'}[]} props.days
 * @param {{startDate: string, endDate: string | null} | null} props.value endDate가 null이면 시작일만 고른 상태
 * @param {(value: {startDate: string, endDate: string | null}) => void} props.onChange
 * @param {(month: string) => void} props.onMonthChange
 * @param {number} props.minDays
 * @param {number} props.maxDays
 * @param {Record<string, {label: string}>} props.statusLabels 상태 코드 → 라벨(features의 RENTAL_DAY_STATUS)
 * @param {string} [props.minMonth] 이전 달 버튼 비활성 기준 "YYYY-MM"
 * @param {string} [props.maxMonth] 다음 달 버튼 비활성 기준 "YYYY-MM"
 * @param {boolean} [props.isLoading] 날짜 칸을 스켈레톤으로 바꾸고 선택을 막는다
 * @param {import('react').ReactNode} [props.title] 헤더 왼쪽(예: "기간 선택" 제목)
 */
export default function Calendar({
  month,
  days,
  value,
  onChange,
  onMonthChange,
  minDays,
  maxDays,
  statusLabels,
  minMonth,
  maxMonth,
  isLoading = false,
  title,
}) {
  const [rejection, setRejection] = useState(null);
  const statusByDate = useMemo(() => toStatusMap(days), [days]);
  const cells = useMemo(() => buildMonthGrid(month), [month]);

  function handleSelect(date) {
    const result = selectDate(value, date, { statusByDate, minDays, maxDays });
    setRejection(
      result.reason ? { reason: result.reason, status: statusOf(statusByDate, date) } : null,
    );
    if (result.value !== value) {
      onChange(result.value);
    }
  }

  let message;
  let messageClass = 'text-ink-muted';
  if (rejection) {
    message = rejectMessage(rejection, { minDays, maxDays });
    messageClass = 'text-danger';
  } else if (value?.endDate) {
    message = formatDateRange(value.startDate, value.endDate);
    messageClass = 'font-semibold text-primary';
  } else if (value?.startDate) {
    message = '반납일을 선택해 주세요';
  } else {
    message = `${minDays}~${maxDays}일까지 선택할 수 있어요`;
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <div>{title}</div>
        <div className="-mr-2 flex items-center">
          <button
            type="button"
            aria-label="이전 달"
            disabled={minMonth != null && month <= minMonth}
            onClick={() => onMonthChange(addMonths(month, -1))}
            className={NAV_BUTTON}
          >
            <ChevronIcon direction="prev" />
          </button>
          <span className="min-w-22 text-center text-sm font-semibold" aria-live="polite">
            {formatMonth(month)}
          </span>
          <button
            type="button"
            aria-label="다음 달"
            disabled={maxMonth != null && month >= maxMonth}
            onClick={() => onMonthChange(addMonths(month, 1))}
            className={NAV_BUTTON}
          >
            <ChevronIcon direction="next" />
          </button>
        </div>
      </div>

      <div
        role="group"
        aria-label={formatMonth(month)}
        aria-busy={isLoading || undefined}
        className="grid grid-cols-7 gap-y-1 text-center text-sm"
      >
        {WEEKDAYS.map((weekday) => (
          <span key={weekday} aria-hidden="true" className="pb-0.5 text-xs text-ink-subtle">
            {weekday}
          </span>
        ))}
        {cells.map((date, index) => {
          if (!date) {
            return <span key={`blank-${index}`} aria-hidden="true" />;
          }
          if (isLoading) {
            return (
              <span key={date} className="flex h-11 items-center justify-center">
                {/* Skeleton의 rounded-md와 겹치지 않게 원은 직접 그린다 */}
                <span
                  aria-hidden="true"
                  className="size-8 animate-pulse rounded-full bg-placeholder"
                />
              </span>
            );
          }
          const status = statusOf(statusByDate, date);
          const available = status === DAY_STATUS.AVAILABLE;
          const position = rangePosition(value, date);
          const statusLabel = statusLabels[status]?.label ?? status;
          const label = `${formatMonthDay(date)} ${weekdayOf(date)}요일, ${statusLabel}${positionSuffix(position, value)}`;
          return (
            <button
              key={date}
              type="button"
              aria-label={label}
              aria-pressed={position != null}
              aria-disabled={!available || undefined}
              onClick={() => handleSelect(date)}
              className={`group flex h-11 items-center justify-center focus-visible:relative focus-visible:z-10 focus-visible:outline-2 focus-visible:outline-primary ${
                available ? '' : 'cursor-not-allowed'
              } ${POSITION_CELL[position] ?? STATUS_TEXT[status]}`}
            >
              <span
                aria-hidden="true"
                className={`flex size-11 items-center justify-center rounded-full ${
                  position === 'single'
                    ? 'bg-primary font-bold text-white'
                    : position == null && available
                      ? 'group-hover:bg-surface'
                      : ''
                }`}
              >
                {Number(date.slice(8))}
              </span>
            </button>
          );
        })}
      </div>

      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-ink-muted">
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="text-ink">
            1
          </span>
          {statusLabels[DAY_STATUS.AVAILABLE]?.label}
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="text-ink-faint line-through">
            1
          </span>
          {statusLabels[DAY_STATUS.BOOKED]?.label}
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className="text-ink-disabled">
            1
          </span>
          {statusLabels[DAY_STATUS.UNAVAILABLE]?.label}
        </li>
      </ul>

      <p aria-live="polite" className={`text-caption ${messageClass}`}>
        {message}
      </p>
    </div>
  );
}
