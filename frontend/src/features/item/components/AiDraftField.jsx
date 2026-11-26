import { useRef, useState } from 'react';
import { useWatch } from 'react-hook-form';

import { Button } from '../../../shared/ui';
import { getDraftErrorNotice, getDraftFiles, getDraftFills, getDraftNotice } from '../aiDraft';
import { AI_DRAFT_TIMEOUT_SECONDS } from '../constants';
import { useDraftItem } from '../queries';

const NOTICE_TONE = {
  surface: 'bg-surface',
  accent: 'bg-accent-soft',
};

/** 결과 안내 상자(design-system §4.5) */
export function AiDraftNotice({ notice }) {
  return (
    <div role="status" className={`rounded-xl px-3.5 py-3 ${NOTICE_TONE[notice.tone]}`}>
      {notice.messages.map((message) => (
        <p key={message} className="text-caption">
          {message}
        </p>
      ))}
    </div>
  );
}

/**
 * SCR-06 등록의 "AI로 채우기"(REQ-067). 사진을 보내 받은 초안으로 사용자가 고치지 않은 칸만 채운다.
 * 요청 중에도 입력·등록은 막지 않는다. 등록에 성공해 화면을 떠나면 mutate 콜백이 불리지 않아 응답은 버려진다.
 *
 * @param {object} props
 * @param {object} props.control
 * @param {object} props.initialValues 폼 처음 값(사용자가 고친 칸 판단)
 * @param {(name?: string) => any} props.getValues
 * @param {(name: string, value: any, options?: object) => void} props.setValue
 * @param {(name: string) => { invalid: boolean }} props.getFieldState
 * @param {boolean} [props.disabled]
 */
export default function AiDraftField({
  control,
  initialValues,
  getValues,
  setValue,
  getFieldState,
  disabled,
}) {
  const images = useWatch({ control, name: 'images' });
  const files = getDraftFiles(images);
  const mutation = useDraftItem();
  const [notice, setNotice] = useState(null);
  // AI가 마지막으로 넣은 값. 다시 누르면 이 값 그대로인 칸은 새 값으로 바꾼다
  const aiFilledRef = useRef({});

  function applyDraft(draft) {
    const fills = getDraftFills(draft, {
      current: getValues(),
      initial: initialValues,
      aiFilled: aiFilledRef.current,
    });
    for (const { name, value } of fills) {
      // 제출 검증에 걸려 있던 칸이면 오류도 다시 본다
      setValue(name, value, { shouldValidate: getFieldState(name).invalid });
      aiFilledRef.current[name] = value;
    }
    setNotice(getDraftNotice(draft));
  }

  function handleClick() {
    setNotice(null);
    mutation.mutate(files, {
      onSuccess: applyDraft,
      onError: (error) => setNotice(getDraftErrorNotice(error)),
    });
  }

  return (
    <div className="flex flex-col gap-2">
      <Button
        variant="secondary"
        size="sm"
        className="self-start"
        isLoading={mutation.isPending}
        disabled={disabled || files.length === 0}
        onClick={handleClick}
      >
        AI로 채우기
      </Button>
      {mutation.isPending ? (
        <p role="status" className="text-caption text-ink-muted">
          사진을 보고 초안을 만들고 있어요. 최대 {AI_DRAFT_TIMEOUT_SECONDS}초 걸릴 수 있어요
        </p>
      ) : (
        notice && <AiDraftNotice notice={notice} />
      )}
    </div>
  );
}
