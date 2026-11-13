import ChatRoomListItem from '../features/chat/components/ChatRoomListItem';
import { getErrorMessage } from '../features/chat/errorMessages';
import { useChatRooms } from '../features/chat/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { isInitialLoadError } from '../shared/lib/queryState';
import { EmptyState, ErrorState, Skeleton } from '../shared/ui';

function ListSkeleton() {
  return (
    <ul aria-busy="true" aria-label="불러오는 중" className="divide-y divide-divider">
      {Array.from({ length: 5 }, (_, index) => (
        <li key={index} className="flex items-center gap-3 py-4">
          <Skeleton className="size-12 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-1/3" />
            <Skeleton className="h-3 w-2/3" />
          </div>
          <Skeleton className="size-12 rounded-[10px]" />
        </li>
      ))}
    </ul>
  );
}

/**
 * SCR-09 채팅 목록(REQ-028·029). 페이지 없이 전체, 최근 메시지 순.
 * 상대가 보낸 메시지는 /user/queue/chat-rooms 이벤트로 목록이 바로 바뀐다(features/chat/queries.js useChatRoomUpdates).
 */
export default function ChatListPage() {
  const query = useChatRooms();
  useRefetchErrorToast([query]);

  let content;
  if (isInitialLoadError(query)) {
    content = <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />;
  } else if (!query.data) {
    content = <ListSkeleton />;
  } else if (query.data.length === 0) {
    content = (
      <EmptyState
        title="아직 채팅이 없어요"
        description="물품 상세에서 채팅하기를 눌러 대화를 시작해 보세요"
      />
    );
  } else {
    content = (
      <ul className="divide-y divide-divider">
        {query.data.map((room) => (
          <li key={room.roomId}>
            <ChatRoomListItem room={room} />
          </li>
        ))}
      </ul>
    );
  }

  return (
    <div className="mx-auto w-full max-w-2xl">
      <h1 className="flex h-14 items-center text-lg font-bold">채팅</h1>
      {content}
    </div>
  );
}
