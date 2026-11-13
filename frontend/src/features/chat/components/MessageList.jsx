import { useLayoutEffect, useRef } from 'react';

import { Button, InfiniteListFooter } from '../../../shared/ui';
import { PENDING_STATUS } from '../constants';

// 맨 아래에서 이만큼 안이면 "맨 아래를 보는 중"으로 본다(새 메시지가 오면 따라 내려간다)
const BOTTOM_THRESHOLD_PX = 80;

function CheckIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-4 shrink-0"
    >
      <path d="M5 12l5 5 9-10" />
    </svg>
  );
}

// 시안 말풍선: 상대는 흰 면(왼쪽 위 모서리 4px), 나는 주 색상(오른쪽 위 4px)
function Bubble({ isMine, dimmed = false, children }) {
  return (
    <p
      className={`max-w-[75%] px-4 py-2.5 text-body break-words whitespace-pre-wrap ${
        isMine
          ? 'rounded-[16px_4px_16px_16px] bg-primary text-white'
          : 'rounded-[4px_16px_16px_16px] border border-line bg-white text-ink'
      } ${dimmed ? 'opacity-60' : ''}`}
    >
      {children}
    </p>
  );
}

function MessageRow({ row }) {
  const { message, isMine, isRead, showTime, time } = row;
  const meta = showTime || isRead;
  return (
    <li className={`flex items-end gap-1.5 ${isMine ? 'flex-row-reverse' : ''}`}>
      <Bubble isMine={isMine}>{message.content}</Bubble>
      {meta && (
        <span className="mb-0.5 shrink-0 text-xs text-ink-muted">
          {isRead && <span className="mr-1">읽음</span>}
          {showTime && time}
        </span>
      )}
    </li>
  );
}

function PendingRow({ item, onRetry, onRemove }) {
  const failed = item.status === PENDING_STATUS.FAILED;
  return (
    <li className="flex flex-col items-end gap-1">
      <div className="flex w-full flex-row-reverse items-end gap-1.5">
        <Bubble isMine dimmed={!failed}>
          {item.content}
        </Bubble>
        {!failed && <span className="mb-0.5 shrink-0 text-xs text-ink-muted">보내는 중</span>}
      </div>
      {failed && (
        <div role="alert" className="flex items-center gap-2">
          <span className="text-xs text-danger">{item.errorMessage ?? '보내지 못했어요'}</span>
          <Button variant="secondary" size="sm" onClick={() => onRetry(item)}>
            다시 보내기
          </Button>
          <Button variant="secondary" size="sm" onClick={() => onRemove(item)}>
            삭제
          </Button>
        </div>
      )}
    </li>
  );
}

function Row({ row }) {
  if (row.kind === 'date') {
    return (
      <li className="py-1 text-center text-xs text-ink-muted">
        <span>{row.label}</span>
      </li>
    );
  }
  if (row.kind === 'system') {
    return (
      <li className="flex justify-center">
        <p className="inline-flex max-w-[90%] items-center gap-1.5 rounded-full bg-primary-soft px-4 py-2 text-caption font-semibold text-primary">
          <CheckIcon />
          <span className="break-words whitespace-pre-wrap">{row.message.content}</span>
        </p>
      </li>
    );
  }
  return <MessageRow row={row} />;
}

/**
 * 말풍선 목록(스크롤 영역). 위 끝에 닿으면 이전 페이지를 부르고(InfiniteListFooter), 불러온 뒤에도 보던 자리를 지킨다.
 * 처음과 내가 보낼 때는 맨 아래로, 새 메시지는 맨 아래를 보고 있을 때만 따라 내려간다.
 */
export default function MessageList({ query, rows, pending, onRetry, onRemove, children }) {
  const scrollRef = useRef(null);
  const isAtBottomRef = useRef(true);
  const previousRef = useRef({ oldestKey: null, newestKey: null, scrollHeight: 0 });

  const oldestKey = rows.find((row) => row.kind !== 'date')?.key ?? null;
  const lastPending = pending.at(-1);
  const newestKey = lastPending ? `p-${lastPending.clientMessageId}` : (rows.at(-1)?.key ?? null);

  useLayoutEffect(() => {
    const element = scrollRef.current;
    if (!element) {
      return;
    }
    const previous = previousRef.current;
    if (previous.newestKey === null) {
      element.scrollTop = element.scrollHeight;
    } else if (oldestKey !== previous.oldestKey && newestKey === previous.newestKey) {
      // 이전 페이지가 위에 붙었다: 늘어난 높이만큼 내려 보던 메시지를 그 자리에
      element.scrollTop += element.scrollHeight - previous.scrollHeight;
    } else if (
      newestKey !== previous.newestKey &&
      (isAtBottomRef.current || newestKey?.startsWith('p-'))
    ) {
      element.scrollTop = element.scrollHeight;
    }
    previousRef.current = { oldestKey, newestKey, scrollHeight: element.scrollHeight };
  });

  function handleScroll(event) {
    const element = event.currentTarget;
    isAtBottomRef.current =
      element.scrollHeight - element.scrollTop - element.clientHeight < BOTTOM_THRESHOLD_PX;
  }

  return (
    <div
      ref={scrollRef}
      onScroll={handleScroll}
      className="min-h-0 flex-1 overflow-y-auto bg-surface px-4"
    >
      <InfiniteListFooter query={query} />
      {children}
      <ol aria-label="메시지" className="flex flex-col gap-3 py-4">
        {rows.map((row) => (
          <Row key={row.key} row={row} />
        ))}
        {pending.map((item) => (
          <PendingRow
            key={item.clientMessageId}
            item={item}
            onRetry={onRetry}
            onRemove={onRemove}
          />
        ))}
      </ol>
    </div>
  );
}
