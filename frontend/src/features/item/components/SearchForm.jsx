import { useState } from 'react';

import { Button } from '../../../shared/ui';

/**
 * 검색어 입력 + 필터 버튼. 제출(엔터·검색 버튼)할 때만 키워드를 올린다(onSubmit, 앞뒤 공백 제거).
 * keyword가 바뀌면(뒤로 가기 등) 입력칸 값만 맞춘다. 다시 그리면 제출 뒤 포커스가 입력칸에서 빠진다.
 */
export default function SearchForm({ keyword, filterCount, onSubmit, onOpenFilter }) {
  const [draft, setDraft] = useState(keyword);
  const [syncedKeyword, setSyncedKeyword] = useState(keyword);
  if (keyword !== syncedKeyword) {
    setSyncedKeyword(keyword);
    setDraft(keyword);
  }

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = draft.trim();
    // 키워드가 그대로여도(공백만 더한 경우) 입력칸은 지운 값으로
    setDraft(trimmed);
    onSubmit(trimmed);
  }

  return (
    <form role="search" onSubmit={handleSubmit} className="flex flex-col gap-2 py-3">
      <div className="flex gap-2">
        <input
          type="search"
          aria-label="검색어"
          placeholder="빌리고 싶은 물품을 검색해 보세요"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          enterKeyHint="search"
          autoComplete="off"
          className="h-12 min-w-0 flex-1 rounded-lg border border-line bg-white px-3.5 text-body text-ink outline-none placeholder:text-ink-subtle focus:border-primary focus:ring-1 focus:ring-primary"
        />
        <Button type="submit" className="shrink-0">
          검색
        </Button>
      </div>
      <div className="flex">
        <Button variant="secondary" size="sm" onClick={onOpenFilter}>
          {filterCount > 0 ? `필터 ${filterCount}` : '필터'}
        </Button>
      </div>
    </form>
  );
}
