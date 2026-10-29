import { isInitialLoadError } from '../../../shared/lib/queryState';
import { Badge, ErrorState, Skeleton } from '../../../shared/ui';
import { INSPECTION_RESULT, RETURN_CHECKLIST_ITEM } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { findInspection, formatShortDate } from '../rentalData';

/** 사진을 누르면 원본을 새 탭으로 연다 */
function Photo({ url, label, className }) {
  return (
    <a
      href={url}
      target="_blank"
      rel="noreferrer"
      aria-label={`${label} 크게 보기`}
      className={`block overflow-hidden rounded-[10px] bg-placeholder focus-visible:outline-2 focus-visible:outline-primary ${className}`}
    >
      <img src={url} alt="" loading="lazy" className="size-full object-cover" />
    </a>
  );
}

/** 대표 사진 한 장 + 캡션 + 나머지 사진 줄(시안: 인계·반납 사진 나란히) */
function PhotoColumn({ record, title, emptyText }) {
  if (!record) {
    return (
      <div className="flex flex-col gap-1">
        <div className="flex h-27 items-center justify-center rounded-[10px] bg-surface text-caption text-ink-subtle">
          {emptyText}
        </div>
        <span className="text-center text-xs text-ink-muted">{title}</span>
      </div>
    );
  }
  const [cover, ...rest] = record.imageUrls;
  return (
    <div className="flex flex-col gap-1">
      {cover ? (
        <Photo url={cover} label={`${title} 1`} className="h-27" />
      ) : (
        <div className="h-27 rounded-[10px] bg-placeholder" />
      )}
      <span className="text-center text-xs text-ink-muted">
        {title} · {formatShortDate(record.createdAt)}
      </span>
      {rest.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {rest.map((url, index) => (
            <Photo key={url} url={url} label={`${title} ${index + 2}`} className="size-11" />
          ))}
        </div>
      )}
    </div>
  );
}

function Checklist({ checklist }) {
  return (
    <ul className="flex flex-col text-caption">
      {Object.entries(RETURN_CHECKLIST_ITEM).map(([key, { question }]) => {
        const yes = checklist[key] === true;
        return (
          <li key={key} className="flex justify-between gap-3 py-0.5">
            <span>{question}</span>
            {yes ? <span>예</span> : <b className="text-danger">아니오</b>}
          </li>
        );
      })}
    </ul>
  );
}

function Note({ label, children }) {
  return (
    <p className="text-caption">
      <span className="font-semibold">{label}</span> · {children}
    </p>
  );
}

/**
 * 인계·반납 기록(GET /inspections). 반납 체크리스트의 '아니오'는 Danger로 강조하고,
 * 제공자가 대리 접수한 반납(writerRole OWNER)은 배지로 표시한다. 판정이 있으면 결과·내용·사진.
 */
export default function InspectionRecords({ query }) {
  if (query.isPending) {
    return (
      <div className="grid grid-cols-2 gap-2">
        <Skeleton className="h-27 rounded-[10px]" />
        <Skeleton className="h-27 rounded-[10px]" />
      </div>
    );
  }
  if (isInitialLoadError(query)) {
    return (
      <ErrorState
        title="기록을 불러오지 못했어요"
        message={getErrorMessage(query.error)}
        onRetry={query.refetch}
      />
    );
  }
  const handover = findInspection(query.data, 'HANDOVER');
  const ret = findInspection(query.data, 'RETURN');

  return (
    <section aria-label="인계·반납 기록" className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-2">
        <PhotoColumn record={handover} title="인계 사진" emptyText="인계 전" />
        <PhotoColumn record={ret} title="반납 사진" emptyText="반납 전" />
      </div>

      {handover?.note && <Note label="인계 메모">{handover.note}</Note>}

      {ret && (
        <div className="flex flex-col gap-2">
          {ret.writerRole === 'OWNER' && (
            <Badge tone="amber" className="self-start">
              제공자가 대신 접수
            </Badge>
          )}
          {ret.checklist && <Checklist checklist={ret.checklist} />}
          {ret.note && <Note label="반납 메모">{ret.note}</Note>}
        </div>
      )}

      {ret?.result && (
        <div className="flex flex-col gap-2 rounded-xl bg-surface p-3.5">
          <p className="text-caption font-semibold">
            상태 확인 결과 ·{' '}
            <span className={ret.result === 'ABNORMAL' ? 'text-danger' : 'text-primary'}>
              {INSPECTION_RESULT[ret.result]?.label ?? ret.result}
            </span>
          </p>
          {ret.resultNote && <p className="text-caption">{ret.resultNote}</p>}
          {ret.resultImageUrls.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {ret.resultImageUrls.map((url, index) => (
                <Photo key={url} url={url} label={`판정 사진 ${index + 1}`} className="size-16" />
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  );
}
