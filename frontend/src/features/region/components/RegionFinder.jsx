import { useEffect, useState } from 'react';

import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { Button, Spinner, TextField } from '../../../shared/ui';
import { REGION_SEARCH_DEBOUNCE_MS } from '../constants';
import { createCoordsSlot } from '../coordsSlot';
import { getErrorMessage, getLocateErrorMessage } from '../errorMessages';
import { useLocateRegion, useRegionSearch } from '../queries';

const NOTICE = 'rounded-xl bg-surface px-4 py-3 text-caption text-ink-muted';

/** 결과 한 줄: fullName + 선택 버튼 */
function RegionResultRow({ region, selectState, isBlocked, onSelect }) {
  return (
    <li className="flex items-center justify-between gap-3 py-2">
      <span className="min-w-0 text-body break-keep">{region.fullName}</span>
      {/* 눈에 보이는 라벨을 그대로 담는다(음성 명령으로 "추가"를 말해도 눌리게) */}
      <Button
        variant={selectState.disabled ? 'secondary' : 'outline'}
        size="sm"
        className="shrink-0"
        aria-label={`${region.name} ${selectState.label}`}
        disabled={selectState.disabled || isBlocked}
        onClick={onSelect}
      >
        {selectState.label}
      </Button>
    </li>
  );
}

function SearchResults({ keyword, query, getSelectState, isBlocked, onSelect }) {
  if (keyword === '') {
    return null;
  }
  if (query.isPending) {
    return (
      <div className="flex justify-center py-6 text-ink-muted">
        <Spinner label="동네 찾는 중" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <div role="alert" className="flex items-center justify-between gap-3 py-3">
        <p className="text-caption text-ink-muted">{getErrorMessage(query.error)}</p>
        <Button variant="secondary" size="sm" onClick={() => query.refetch()}>
          다시 시도
        </Button>
      </div>
    );
  }
  if (query.data.length === 0) {
    return (
      <p className="py-6 text-center text-caption text-ink-muted">
        ‘{keyword}’에 해당하는 동네가 없어요
      </p>
    );
  }
  return (
    <ul aria-label="검색 결과" className="divide-y divide-divider">
      {query.data.map((region) => (
        <RegionResultRow
          key={region.regionId}
          region={region}
          selectState={getSelectState(region)}
          isBlocked={isBlocked}
          onSelect={() => onSelect(region)}
        />
      ))}
    </ul>
  );
}

/**
 * 동네 검색 + 현재 위치로 찾기(로그인·비회원 공용). 무엇을 할지는 호출부가 정한다.
 *
 * 좌표 규칙(queries.js): 로그인일 때만 by-coord에 쓴 좌표를 좌표 칸(coordsSlot.js)에 두고, 찾은 동네를 고르면
 * onSelect에 takeCoords(꺼내면서 비움)를 넘긴다. 찾기 호출마다 토큰을 받아 늦게 온 응답은 좌표도 결과 표시도 버린다.
 * 비우는 때: 새 검색 입력, 검색 결과에서 고를 때, 찾은 결과로 추가를 시도한 직후(성공·실패 무관), 언마운트.
 * 비회원은 좌표를 받지도 않는다.
 *
 * @param {object} props
 * @param {(region) => { label: string, disabled: boolean }} props.getSelectState 결과 줄 버튼
 * @param {(region, { takeCoords?: () => object | null }) => Promise | void} props.onSelect
 * @param {boolean} [props.isGuest] 비회원: 좌표를 받지 않고 위치 권한 거부 문구가 "고르기"용
 * @param {string | null} [props.lockedNotice] 있으면 안내를 보여 주고 고르기·위치 찾기를 막는다(최대 개수)
 * @param {boolean} [props.isBusy] 다른 동작 중이면 고르기를 막는다
 * @param {string} [props.locateHint] 찾은 동네 아래 안내(로그인: 추가하면 바로 인증)
 */
export default function RegionFinder({
  getSelectState,
  onSelect,
  isGuest = false,
  lockedNotice = null,
  isBusy = false,
  locateHint,
}) {
  const [keyword, setKeyword] = useState('');
  const searchKeyword = useDebouncedValue(keyword, REGION_SEARCH_DEBOUNCE_MS).trim();
  const search = useRegionSearch(searchKeyword);
  const locate = useLocateRegion();
  // 좌표 칸 객체만 state로 둔다(좌표는 객체 클로저 안이라 state·Devtools에 보이지 않음)
  const [slot] = useState(createCoordsSlot);
  // 결과를 보여 줄 찾기 호출의 토큰. 비우면 올라가 이전 호출 결과가 가려진다
  const [currentToken, setCurrentToken] = useState(0);
  const isLocked = lockedNotice !== null;
  const isCurrentCall = locate.variables?.token === currentToken;
  const located = locate.isSuccess && isCurrentCall ? locate.data : null;
  const locateError = locate.isError && isCurrentCall ? locate.error : null;

  useEffect(() => () => slot.clear(), [slot]);

  function clearSlot() {
    setCurrentToken(slot.clear());
  }

  function handleKeywordChange(event) {
    setKeyword(event.target.value);
    clearSlot();
  }

  function handleLocate() {
    const token = slot.begin();
    setCurrentToken(token);
    locate.mutate({
      token,
      onLocated: isGuest
        ? undefined
        : (coords) => {
            slot.put(token, coords);
          },
    });
  }

  function handleSelectSearched(region) {
    clearSlot();
    onSelect(region, {});
  }

  async function handleSelectLocated(region) {
    // 찾은 뒤 목록이 바뀌었을 수 있어 누를 때 다시 판단한다
    if (getSelectState(region).disabled) {
      clearSlot();
      return;
    }
    try {
      await onSelect(region, isGuest ? {} : { takeCoords: () => slot.take() });
    } finally {
      // 추가를 시도한 직후에는 성공·실패와 무관하게 비운다(호출부가 꺼내지 않았어도)
      slot.take();
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {isLocked && <p className={NOTICE}>{lockedNotice}</p>}

      <div className="flex flex-col gap-2">
        <Button
          variant="outline"
          fullWidth
          isLoading={locate.isPending}
          disabled={isLocked}
          onClick={handleLocate}
        >
          현재 위치로 찾기
        </Button>
        {/* 권한 거부도 버튼을 막지 않는다: 브라우저 설정에서 다시 켤 수 있어 누를 때마다 다시 묻는다 */}
        {locateError && (
          <p role="alert" className="text-caption text-danger">
            {getLocateErrorMessage(locateError, { isGuest })}
          </p>
        )}
        {located && (
          <div className="rounded-xl border border-line px-4 py-1">
            <ul aria-label="현재 위치의 동네">
              <RegionResultRow
                region={located}
                selectState={getSelectState(located)}
                isBlocked={isBusy || isLocked}
                onSelect={() => handleSelectLocated(located)}
              />
            </ul>
            {locateHint && !getSelectState(located).disabled && (
              <p className="pb-2 text-xs text-ink-muted">{locateHint}</p>
            )}
          </div>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <TextField
          label="동네 검색"
          type="search"
          enterKeyHint="search"
          autoComplete="off"
          placeholder="동 이름으로 검색 (예: 송도)"
          value={keyword}
          onChange={handleKeywordChange}
        />
        <SearchResults
          keyword={searchKeyword}
          query={search}
          getSelectState={getSelectState}
          isBlocked={isBusy || isLocked}
          onSelect={handleSelectSearched}
        />
      </div>
    </div>
  );
}
