import { formatElapsed } from '../../../shared/lib/format';
import { DANGER_NOTIFICATION_TYPES } from '../constants';

function BellIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-5"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M6 16V11a6 6 0 0112 0v5l2 2H4z" />
      <path d="M10 21h4" />
    </svg>
  );
}

/**
 * SCR-11 목록 한 줄: 종류 아이콘(연체 계열은 danger) · 문구(서버 content) · 경과 시간. 안 읽은 알림은 굵게 + 점.
 * 누르면 onSelect(읽음 처리 + 이동은 화면이 정한다). 갈 화면이 없어도 눌러서 읽음 처리할 수 있게 버튼으로 둔다.
 */
export default function NotificationListItem({ notification, onSelect }) {
  const isDanger = DANGER_NOTIFICATION_TYPES.has(notification.type);
  return (
    <button
      type="button"
      onClick={() => onSelect(notification)}
      className="flex w-full items-start gap-3 py-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <span
        className={`flex size-10 shrink-0 items-center justify-center rounded-full ${
          isDanger ? 'bg-danger-soft text-danger' : 'bg-surface text-ink-muted'
        }`}
      >
        <BellIcon />
      </span>
      <span className="min-w-0 flex-1">
        <span
          className={`block text-body ${notification.isRead ? 'text-ink-muted' : 'font-semibold text-ink'}`}
        >
          {notification.content}
        </span>
        <span className="mt-0.5 block text-xs text-ink-muted">
          {formatElapsed(notification.createdAt)}
        </span>
      </span>
      {!notification.isRead && (
        <>
          <span aria-hidden="true" className="mt-2 size-2 shrink-0 rounded-full bg-danger" />
          <span className="sr-only">, 안 읽음</span>
        </>
      )}
    </button>
  );
}
