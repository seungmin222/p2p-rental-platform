import { useState } from 'react';

import { countCodePoints } from '../../../shared/lib/validators';
import { CHAT_MESSAGE_MAX } from '../constants';
import { validateMessage } from '../pendingMessages';

// 입력창은 내용에 맞춰 이 줄 수까지 늘어난다(UI 값)
const MAX_ROWS = 4;

function SendIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      className="size-6"
    >
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  );
}

/**
 * 메시지 입력(SCR-10 하단). Enter로 보내고 Shift+Enter는 줄바꿈(한글 조합 중 Enter는 무시).
 * 공백만이면 보내지 않고, chat.message.max(코드 포인트)를 넘으면 글자 수를 빨갛게 보이고 막는다.
 * disabledReason이 있으면(상대 탈퇴) 입력을 막고 그 문구를 보인다.
 */
export default function ChatComposer({ onSend, disabledReason }) {
  const [content, setContent] = useState('');
  const problem = validateMessage(content, CHAT_MESSAGE_MAX);
  const isDisabled = Boolean(disabledReason);
  const rows = Math.min(MAX_ROWS, content.split('\n').length);

  function submit() {
    if (isDisabled || problem) {
      return;
    }
    onSend(content);
    setContent('');
  }

  function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className="border-t border-line bg-white px-4 pt-2 pb-[calc(0.5rem+env(safe-area-inset-bottom))]"
    >
      {problem === 'tooLong' && (
        <p role="alert" className="pb-1 text-right text-xs text-danger">
          {countCodePoints(content).toLocaleString('ko-KR')}/
          {CHAT_MESSAGE_MAX.toLocaleString('ko-KR')}자 — 줄여서 보내 주세요
        </p>
      )}
      <div className="flex items-end gap-2">
        <label htmlFor="chat-message" className="sr-only">
          메시지
        </label>
        <textarea
          id="chat-message"
          value={content}
          onChange={(event) => setContent(event.target.value)}
          onKeyDown={handleKeyDown}
          rows={rows}
          disabled={isDisabled}
          placeholder={disabledReason ?? '메시지 보내기'}
          className="min-h-12 flex-1 resize-none rounded-3xl border border-line bg-surface px-4 py-3 text-body placeholder:text-ink-subtle focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed"
        />
        <button
          type="submit"
          aria-label="보내기"
          disabled={isDisabled || problem !== null}
          className="flex size-12 shrink-0 items-center justify-center rounded-full bg-primary text-white hover:bg-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:bg-line disabled:text-ink-subtle"
        >
          <SendIcon />
        </button>
      </div>
    </form>
  );
}
