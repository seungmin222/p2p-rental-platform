import { useState } from 'react';
import { Controller } from 'react-hook-form';

import { Button } from '../../../shared/ui';
import { EMPTY_PERIOD, validatePeriods } from '../itemForm';
import PeriodCalendarSheet from './PeriodCalendarSheet';

function formatDot(date) {
  return date.replaceAll('-', '.');
}

// 해를 넘기는 기간이 있어 연도까지 보인다
function periodText({ startDate, endDate }) {
  return startDate && endDate ? `${formatDot(startDate)} ~ ${formatDot(endDate)}` : null;
}

function PeriodRows({ fieldRef, periods, onChange, error, disabled }) {
  // 시트로 고치는 줄 번호. null이면 닫힘
  const [editingIndex, setEditingIndex] = useState(null);

  return (
    <fieldset
      ref={fieldRef}
      tabIndex={-1}
      disabled={disabled}
      className="flex flex-col gap-3 outline-none"
    >
      <legend className="pb-1.5 text-caption font-semibold">
        대여 가능 기간
        <span aria-hidden="true" className="text-danger">
          {' '}
          *
        </span>
      </legend>
      {periods.map((period, index) => {
        const text = periodText(period);
        return (
          <div
            key={index}
            role="group"
            aria-label={`기간 ${index + 1}`}
            className="flex items-center gap-2"
          >
            <button
              type="button"
              onClick={() => setEditingIndex(index)}
              aria-label={`기간 ${index + 1}: ${text ?? '선택 안 함'}, 기간 선택`}
              className={`flex h-12 min-w-0 flex-1 items-center rounded-lg border bg-white px-3.5 text-left text-body outline-none focus-visible:border-primary focus-visible:ring-1 focus-visible:ring-primary disabled:cursor-not-allowed disabled:bg-surface disabled:text-ink-subtle ${
                error && !text ? 'border-danger' : 'border-line'
              } ${text ? 'text-ink' : 'text-ink-subtle'}`}
            >
              <span className="truncate">{text ?? '시작일 ~ 종료일 선택'}</span>
            </button>
            {periods.length > 1 && (
              <Button
                variant="secondary"
                size="sm"
                className="shrink-0"
                aria-label={`기간 ${index + 1} 삭제`}
                onClick={() => onChange(periods.filter((_, i) => i !== index))}
              >
                삭제
              </Button>
            )}
          </div>
        );
      })}
      <Button
        variant="outline"
        size="sm"
        onClick={() => onChange([...periods, { ...EMPTY_PERIOD }])}
      >
        기간 추가
      </Button>
      {error?.message && (
        <p role="alert" className="text-xs text-danger">
          {error.message}
        </p>
      )}
      {editingIndex != null && periods[editingIndex] && (
        <PeriodCalendarSheet
          open
          title={`기간 ${editingIndex + 1} 선택`}
          period={periods[editingIndex]}
          onClose={() => setEditingIndex(null)}
          onConfirm={(next) => {
            onChange(periods.map((row, i) => (i === editingIndex ? next : row)));
            setEditingIndex(null);
          }}
        />
      )}
    </fieldset>
  );
}

/**
 * 대여 가능 기간 행 추가·삭제. 값은 `{startDate, endDate}`(`YYYY-MM-DD` 문자열) 배열이고 줄 수에 정책 상한이 없다.
 * 줄마다 버튼 → 시트 안 공용 Calendar(최대 일수 없음, 오늘 이전은 고를 수 없음)로 범위를 고른다.
 * today를 넘기면(등록) 시작일 ≥ 오늘을 검사하고, 수정은 넘기지 않아 이미 지난 기간도 그대로 둘 수 있다.
 * 줄끼리 겹침은 validatePeriods의 폼 오류로만 알린다.
 */
export default function AvailablePeriodsField({ control, today, disabled }) {
  return (
    <Controller
      name="availablePeriods"
      control={control}
      rules={{ validate: (periods) => validatePeriods(periods, { today }) }}
      render={({ field, fieldState }) => (
        <PeriodRows
          fieldRef={field.ref}
          periods={field.value}
          onChange={field.onChange}
          error={fieldState.error}
          disabled={disabled}
        />
      )}
    />
  );
}
