import { useNow } from '../../../shared/hooks/useNow';
import { Badge, Spinner } from '../../../shared/ui';
import {
  AI_COMPARE_NOTICE,
  AI_VIEW,
  getAiCompareHeadline,
  getAiCompareView,
  getAiNoneMessage,
} from '../aiCompare';
import { useRentalInspections } from '../queries';
import { findInspection } from '../rentalData';

// "비교 준비 중" 시간창이 끝나는 것을 다시 그리기 위한 주기
const NOW_INTERVAL_MS = 30_000;

function Headline({ view }) {
  const isPending = view.kind === AI_VIEW.WAITING || view.kind === AI_VIEW.PROCESSING;
  return (
    <p className="flex items-center gap-1.5 text-caption font-semibold">
      {isPending && <Spinner size="sm" />}
      {getAiCompareHeadline(view)}
    </p>
  );
}

function ChangeTypes({ changeTypes }) {
  if (changeTypes.length === 0) {
    return null;
  }
  return (
    <ul className="flex flex-wrap gap-1" aria-label="AI가 찾은 변화 유형">
      {changeTypes.map(({ code, label }) => (
        <li key={code}>
          <Badge tone="amber">{label}</Badge>
        </li>
      ))}
    </ul>
  );
}

/** 사진별 위치 메모. 사진(반납 사진 n번)을 누르면 원본을 새 탭으로 */
function Regions({ regions }) {
  if (regions.length === 0) {
    return null;
  }
  return (
    <ul className="flex flex-col gap-1.5" aria-label="AI가 짚은 위치">
      {regions.map((region, index) => (
        <li key={`${region.imageIndex}-${index}`} className="flex items-center gap-2">
          {region.imageUrl ? (
            <a
              href={region.imageUrl}
              target="_blank"
              rel="noreferrer"
              aria-label={`반납 사진 ${region.imageIndex + 1} 크게 보기`}
              className="block size-11 shrink-0 overflow-hidden rounded-[10px] bg-placeholder focus-visible:outline-2 focus-visible:outline-primary"
            >
              <img src={region.imageUrl} alt="" loading="lazy" className="size-full object-cover" />
            </a>
          ) : null}
          <span className="text-caption">
            {region.imageUrl && (
              <span className="text-ink-muted">반납 사진 {region.imageIndex + 1} · </span>
            )}
            {region.note}
          </span>
        </li>
      ))}
    </ul>
  );
}

function Body({ view, compact, hint }) {
  return (
    <div className="flex flex-col gap-2">
      <Headline view={view} />
      {view.kind === AI_VIEW.DONE && (
        <>
          <ChangeTypes changeTypes={view.changeTypes} />
          {view.description && <p className="text-caption">{view.description}</p>}
          {!compact && <Regions regions={view.regions} />}
        </>
      )}
      {view.kind === AI_VIEW.NONE && (
        <p className="text-caption text-ink-muted">{getAiNoneMessage(view.reason)}</p>
      )}
      {hint && <p className="text-caption text-ink-muted">{hint}</p>}
      <p className="text-xs text-ink-muted">{AI_COMPARE_NOTICE}</p>
    </div>
  );
}

/**
 * 대여 상세 인계·반납 기록 안의 AI 상태 비교(REQ-040). 대여자·제공자 모두 본다.
 * 결과를 기다리는 동안의 재조회는 useRentalInspections가 한다.
 */
export default function AiCompareCard({ returnInspection }) {
  const now = useNow(NOW_INTERVAL_MS);
  const view = getAiCompareView(returnInspection, now);
  if (!view) {
    return null;
  }
  return (
    <section aria-label="AI 상태 비교" className="rounded-xl border border-line p-3.5">
      <p className="mb-2 text-xs font-semibold text-ink-muted">AI 상태 비교 · 참고용</p>
      <Body view={view} />
    </section>
  );
}

/**
 * 제공자의 상태 확인·차감 제안 화면 맨 위 요약(근거로 보기). 같은 inspections 캐시를 읽고,
 * 결과를 기다리지 않아도 판정할 수 있다고 알린다(ai.md "대기 강제 금지").
 */
export function AiCompareSummary({ rentalId }) {
  const now = useNow(NOW_INTERVAL_MS);
  const query = useRentalInspections(rentalId, { enabled: true });
  const view = getAiCompareView(findInspection(query.data, 'RETURN'), now);
  if (!view) {
    return null;
  }
  const isPending = view.kind === AI_VIEW.WAITING || view.kind === AI_VIEW.PROCESSING;
  return (
    <section aria-label="AI 상태 비교" className="rounded-xl bg-surface p-3.5">
      <Body
        view={view}
        compact
        hint={
          isPending
            ? '결과를 기다리지 않아도 상태를 확인할 수 있어요.'
            : view.kind === AI_VIEW.DONE && view.regions.length > 0
              ? '사진별 위치는 대여 상세의 반납 기록에서 볼 수 있어요.'
              : null
        }
      />
    </section>
  );
}
