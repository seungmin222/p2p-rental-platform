// 시안 RentalDetail.html 스텝바: 지난 단계 Primary 점, 현재 단계 흰 점 + 3px 테두리, 남은 단계 Line 점.
const DOT = {
  done: 'bg-primary',
  current: 'border-[3px] border-primary bg-white',
  upcoming: 'bg-line',
};

function labelClassName(step) {
  if (step.state !== 'current') {
    return '';
  }
  return step.danger ? 'font-bold text-danger' : 'font-bold text-primary';
}

/** steps는 getRentalSteps 결과. OVERDUE의 현재 단계(danger)는 Danger 색. 점·선은 장식이고 단계는 목록으로 읽힌다. */
export default function RentalStepBar({ steps }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div aria-hidden="true" className="flex items-center">
        {steps.map((step, index) => {
          const next = steps[index + 1];
          const dot = step.danger ? 'border-[3px] border-danger bg-white' : DOT[step.state];
          return (
            <span key={step.label} className="contents">
              <span className={`size-3 shrink-0 rounded-full ${dot}`} />
              {next && (
                <span
                  className={`h-0.5 flex-1 ${next.state === 'upcoming' ? 'bg-line' : 'bg-primary'}`}
                />
              )}
            </span>
          );
        })}
      </div>
      <ol aria-label="진행 단계" className="flex justify-between text-xs text-ink-muted">
        {steps.map((step) => (
          <li
            key={step.label}
            aria-current={step.state === 'current' ? 'step' : undefined}
            className={labelClassName(step)}
          >
            {step.label}
          </li>
        ))}
      </ol>
    </div>
  );
}
