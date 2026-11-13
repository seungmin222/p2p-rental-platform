import { useParams } from 'react-router';

import BackButton from '../app/layout/BackButton';
import ChatComposer from '../features/chat/components/ChatComposer';
import ChatItemBar from '../features/chat/components/ChatItemBar';
import MessageList from '../features/chat/components/MessageList';
import { getErrorMessage } from '../features/chat/errorMessages';
import { buildMessageRows, getCounterpartName, getMyMemberId } from '../features/chat/messageView';
import { useChatRoom } from '../features/chat/queries';
import { useChatRoomSession } from '../features/chat/useChatRoomSession';
import { parseItemId } from '../features/item/itemId';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { flattenPages } from '../shared/lib/listParams';
import { isInitialLoadError } from '../shared/lib/queryState';
import { ButtonLink, ErrorState, Skeleton, Spinner } from '../shared/ui';
import { STOMP_STATUS } from '../shared/ws';

// 화면 전체 높이 칼럼(PC는 상단 헤더 64px을 뺀다). 본문 좌우 여백(px-4)을 상쇄해 꽉 채운다
const COLUMN =
  '-mx-4 flex h-dvh flex-col xl:mx-auto xl:h-[calc(100dvh-4rem)] xl:w-full xl:max-w-2xl xl:border-x xl:border-line';

function RoomHeader({ room }) {
  const name = room ? getCounterpartName(room.counterpart) : null;
  return (
    <header className="flex h-14 shrink-0 items-center gap-1 border-b border-divider bg-white pr-4 pl-1">
      <BackButton />
      {room ? (
        <h1 className="flex min-w-0 flex-1 items-baseline gap-2">
          <span
            className={`truncate text-lg font-bold ${room.counterpart.isWithdrawn ? 'text-ink-muted' : ''}`}
          >
            {name}
          </span>
          {room.counterpart.regionName && (
            <span className="shrink-0 text-caption text-ink-muted">
              {room.counterpart.regionName}
            </span>
          )}
        </h1>
      ) : (
        <Skeleton className="h-5 w-32" />
      )}
    </header>
  );
}

function ConnectionNotice({ status }) {
  if (status === STOMP_STATUS.CONNECTED || status === STOMP_STATUS.IDLE) {
    return null;
  }
  return (
    <p
      role="status"
      className="sticky top-0 z-10 -mx-4 flex items-center justify-center gap-2 bg-accent-soft py-2 text-xs text-accent-strong"
    >
      <Spinner size="sm" />
      {status === STOMP_STATUS.CONNECTING
        ? '연결하는 중이에요'
        : '연결이 끊겨 다시 연결하는 중이에요'}
    </p>
  );
}

function RoomBody({ roomId, room }) {
  const { messages, pending, status, send, retry, remove } = useChatRoomSession(roomId, room);
  useRefetchErrorToast([messages]);

  const myMemberId = getMyMemberId(room);
  const ordered = (flattenPages(messages.data) ?? []).toReversed();
  const rows = buildMessageRows(ordered, {
    myMemberId,
    counterpartLastReadMessageId: room.counterpartLastReadMessageId,
  });

  let list;
  if (isInitialLoadError(messages)) {
    list = (
      <div className="flex-1 bg-surface">
        <ErrorState message={getErrorMessage(messages.error)} onRetry={messages.refetch} />
      </div>
    );
  } else if (!messages.data) {
    list = (
      <div className="flex flex-1 items-center justify-center bg-surface">
        <Spinner label="메시지를 불러오는 중" />
      </div>
    );
  } else {
    list = (
      <MessageList query={messages} rows={rows} pending={pending} onRetry={retry} onRemove={remove}>
        <ConnectionNotice status={status} />
      </MessageList>
    );
  }

  return (
    <>
      <ChatItemBar room={room} isRenter={myMemberId === room.renterId} />
      {list}
      <ChatComposer
        onSend={send}
        disabledReason={
          room.counterpart.isWithdrawn ? '탈퇴한 사용자에게는 메시지를 보낼 수 없어요' : null
        }
      />
    </>
  );
}

/**
 * SCR-10 채팅방(REQ-026–029). 상단: 상대 · 물품 요약과 최근 대여 상태, 가운데: 말풍선(위로 스크롤하면 이전 내역), 아래: 입력.
 * 사진(050)·약속(051)은 3순위라 그리지 않는다.
 */
export default function ChatRoomPage() {
  const { roomId: roomIdParam } = useParams();
  // id 형식 검사는 물품과 같다(양의 안전한 정수)
  const roomId = parseItemId(roomIdParam);
  const room = useChatRoom(roomId);
  useRefetchErrorToast([room]);

  let body;
  if (roomId == null || isInitialLoadError(room)) {
    const code = room.error?.code;
    const notFound =
      roomId == null || code === 'CHAT_ROOM_NOT_FOUND' || code === 'CHAT_NOT_PARTICIPANT';
    body = (
      <div className="flex flex-1 flex-col items-center justify-center bg-surface">
        <ErrorState
          title={notFound ? '채팅방을 찾을 수 없어요' : undefined}
          message={notFound ? undefined : getErrorMessage(room.error)}
          onRetry={notFound ? undefined : room.refetch}
        />
        {notFound && (
          <ButtonLink to="/chats" variant="secondary">
            채팅 목록으로
          </ButtonLink>
        )}
      </div>
    );
  } else if (!room.data) {
    body = (
      <div className="flex flex-1 items-center justify-center bg-surface">
        <Spinner label="불러오는 중" />
      </div>
    );
  } else {
    body = <RoomBody roomId={roomId} room={room.data} />;
  }

  return (
    <div className={COLUMN}>
      <RoomHeader room={room.data} />
      {body}
    </div>
  );
}
