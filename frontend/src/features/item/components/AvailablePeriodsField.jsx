import { Controller } from 'react-hook-form';

import { Button, TextField } from '../../../shared/ui';
import { EMPTY_PERIOD, validatePeriods } from '../itemForm';

/**
 * 대여 가능 기간 행(시작일·종료일) 추가·삭제. 값은 `YYYY-MM-DD` 문자열 배열이고 줄 수에 정책 상한이 없다.
 * 공용 Calendar는 대여 기준(최대 일수·문구)이라 쓰지 않는다.
 * today를 넘기면(등록) 시작일 ≥ 오늘을 검사하고, 수정은 넘기지 않아 이미 지난 기간도 그대로 둘 수 있다.
 */
export default function AvailablePeriodsField({ control, today, disabled }) {
  return (
    <Controller
      name="availablePeriods"
      control={control}
      rules={{ validate: (periods) => validatePeriods(periods, { today }) }}
      render={({ field, fieldState }) => (
        <fieldset
          ref={field.ref}
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
          {field.value.map((period, index) => (
            <div
              key={index}
              role="group"
              aria-label={`기간 ${index + 1}`}
              className="flex flex-col gap-2"
            >
              <div className="grid grid-cols-2 gap-2">
                <TextField
                  type="date"
                  label="시작일"
                  required
                  min={today}
                  value={period.startDate}
                  onChange={(event) =>
                    field.onChange(
                      field.value.map((row, i) =>
                        i === index ? { ...row, startDate: event.target.value } : row,
                      ),
                    )
                  }
                />
                <TextField
                  type="date"
                  label="종료일"
                  required
                  min={period.startDate || today}
                  value={period.endDate}
                  onChange={(event) =>
                    field.onChange(
                      field.value.map((row, i) =>
                        i === index ? { ...row, endDate: event.target.value } : row,
                      ),
                    )
                  }
                />
              </div>
              {field.value.length > 1 && (
                <Button
                  variant="secondary"
                  size="sm"
                  className="self-end"
                  aria-label={`기간 ${index + 1} 삭제`}
                  onClick={() => field.onChange(field.value.filter((_, i) => i !== index))}
                >
                  삭제
                </Button>
              )}
            </div>
          ))}
          <Button
            variant="outline"
            size="sm"
            onClick={() => field.onChange([...field.value, { ...EMPTY_PERIOD }])}
          >
            기간 추가
          </Button>
          {fieldState.error?.message && (
            <p role="alert" className="text-xs text-danger">
              {fieldState.error.message}
            </p>
          )}
        </fieldset>
      )}
    />
  );
}
