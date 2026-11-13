import { Link } from 'react-router';

import { formatElapsed } from '../../../shared/lib/format';
import { Thumbnail } from '../../../shared/ui';
import { getCounterpartName } from '../messageView';
import ChatAvatar from './ChatAvatar';
import { CountBadge } from './UnreadBadge';

/**
 * SCR-09 목록 한 줄: 상대 사진 · 닉네임 · 동네 · 경과 시간 / 마지막 메시지(한 줄) · 안 읽은 수 · 물품 썸네일.
 * 메시지가 없는 방은 "대화를 시작해 보세요", 삭제된 물품은 썸네일 대신 빈 자리 + "삭제된 물품".
 */
export default function ChatRoomListItem({ room }) {
  const { counterpart, item } = room;
  const meta = [
    counterpart.regionName,
    formatElapsed(room.lastMessageAt),
    item.isDeleted ? '삭제된 물품' : null,
  ].filter(Boolean);

  return (
    <Link
      to={`/chats/${room.roomId}`}
      className="flex items-center gap-3 py-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
    >
      <ChatAvatar url={counterpart.profileImageUrl} />
      <div className="min-w-0 flex-1">
        <p className="flex items-baseline gap-1.5">
          <span
            className={`truncate text-body font-semibold ${counterpart.isWithdrawn ? 'text-ink-muted' : ''}`}
          >
            {getCounterpartName(counterpart)}
          </span>
          {meta.length > 0 && (
            <span className="shrink-0 text-xs text-ink-muted">{meta.join(' · ')}</span>
          )}
        </p>
        <p className="mt-0.5 flex items-center gap-2">
          <span
            className={`min-w-0 flex-1 truncate text-caption ${room.lastMessagePreview ? 'text-ink' : 'text-ink-muted'}`}
          >
            {room.lastMessagePreview ?? '대화를 시작해 보세요'}
          </span>
          <CountBadge count={room.unreadCount} className="shrink-0" />
          {room.unreadCount > 0 && (
            <span className="sr-only">안 읽은 메시지 {room.unreadCount}개</span>
          )}
        </p>
      </div>
      <Thumbnail url={item.isDeleted ? null : item.thumbnailUrl} className="size-12" />
    </Link>
  );
}
