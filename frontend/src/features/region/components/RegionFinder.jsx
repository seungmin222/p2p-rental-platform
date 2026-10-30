import { useEffect, useRef, useState } from 'react';

import { useDebouncedValue } from '../../../shared/hooks/useDebouncedValue';
import { Button, Spinner, TextField } from '../../../shared/ui';
import { REGION_SEARCH_DEBOUNCE_MS } from '../constants';
import { getErrorMessage, getLocateErrorMessage } from '../errorMessages';
import { useLocateRegion, useRegionSearch } from '../queries';

const NOTICE = 'rounded-xl bg-surface px-4 py-3 text-caption text-ink-muted';

/** 결과 한 줄: fullName + 선택 버튼 */
function RegionResultRow({ region, selectState, isBlocked, onSelect }) {
  return (
    <li className="flex items-center justify-between gap-3 py-2">
      <span className="min-w-0 text-body break-keep">{region.fullName}</span>
      <Button
        variant={selectState.disabled ? 'secondary' : 'outline'}
        size="sm"
        className="shrink-0"
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
          onSelect={() => onSelect(region, {})}
        />
      ))}
    </ul>
  );
}

/**
 * 동네 검색 + 현재 위치로 찾기(로그인·비회원 공용). 무엇을 할지는 호출부가 정한다.
 *
 * 좌표 규칙(queries.js): keepsCoords(로그인)일 때만 by-coord에 쓴 좌표를 이 컴포넌트의 ref에 두고,
 * 찾은 동네를 고르면 onSelect에 takeCoords(꺼내면서 지움)를 넘긴다. 새로 검색하거나 다시 찾거나 화면을 떠나면 지우고,
 * 찾은 동네를 고를 수 없으면(이미 추가·최대 개수) 바로 지운다. 비회원은 좌표를 받지도 않는다.
 *
 * @param {object} props
 * @param {(region) => { label: string, disabled: boolean }} props.getSelectState 결과 줄 버튼
 * @param {(region, { takeCoords?: () => object | null }) => void} props.onSelect
 * @param {boolean} [props.keepsCoords]
 * @param {string | null} [props.lockedNotice] 있으면 안내를 보여 주고 고르기·위치 찾기를 막는다(최대 개수)
 * @param {boolean} [props.isBusy] 다른 동작 중이면 고르기를 막는다
 * @param {string} [props.locateHint] 찾은 동네 아래 안내(로그인: 추가하면 바로 인증)
 */
export default function RegionFinder({
  getSelectState,
  onSelect,
  keepsCoords = false,
  lockedNotice = null,
  isBusy = false,
  locateHint,
}) {
  const [keyword, setKeyword] = useState('');
  const searchKeyword = useDebouncedValue(keyword, REGION_SEARCH_DEBOUNCE_MS).trim();
  const search = useRegionSearch(searchKeyword);
  const locate = useLocateRegion();
  const coordsRef = useRef(null);
  const isLocked = lockedNotice !== null;

  useEffect(
    () => () => {
      coordsRef.current = null;
    },
    [],
  );

  function takeCoords() {
    const coords = coordsRef.current;
    coordsRef.current = null;
    return coords;
  }

  function handleKeywordChange(event) {
    setKeyword(event.target.value);
    // 새로 검색하면 찾은 위치 결과와 그 좌표를 버린다
    coordsRef.current = null;
    locate.reset();
  }

  function handleLocate() {
    coordsRef.current = null;
    locate.mutate(
      {
        onLocated: keepsCoords
          ? (coords) => {
              coordsRef.current = coords;
            }
          : undefined,
      },
      {
        onSuccess: (region) => {
          if (getSelectState(region).disabled) {
            coordsRef.current = null;
          }
        },
      },
    );
  }

  function handleSelectLocated(region) {
    onSelect(region, keepsCoords ? { takeCoords } : {});
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
        {locate.isError && (
          <p role="alert" className="text-caption text-danger">
            {getLocateErrorMessage(locate.error)}
          </p>
        )}
        {locate.isSuccess && (
          <div className="rounded-xl border border-line px-4 py-1">
            <ul aria-label="현재 위치의 동네">
              <RegionResultRow
                region={locate.data}
                selectState={getSelectState(locate.data)}
                isBlocked={isBusy || isLocked}
                onSelect={() => handleSelectLocated(locate.data)}
              />
            </ul>
            {locateHint && !getSelectState(locate.data).disabled && (
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
          onSelect={onSelect}
        />
      </div>
    </div>
  );
}
