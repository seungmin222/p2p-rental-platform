import { useEffect, useRef, useState } from 'react';
import { Controller, useWatch } from 'react-hook-form';

import { useKakaoMap } from '../../../shared/hooks/useKakaoMap';
import { Button, Skeleton, TextField } from '../../../shared/ui';
import { getErrorMessage } from '../errorMessages';
import { getTradePlaceCoords, validateAddress } from '../itemForm';
import { reverseGeocode, searchPlaces } from '../kakaoPlace';
import KakaoPlaceMap from './KakaoPlaceMap';

const PUBLIC_NOTICE = '가져온 위치는 거래 장소로 다른 사람에게 공개돼요.';
const FEED_NOTICE = '홈 피드에는 대표 동네 근처의 거래 장소만 보여요.';

// 화면을 벗어난 뒤 온 응답은 버린다
function useIsMounted() {
  const isMounted = useRef(true);
  useEffect(() => {
    isMounted.current = true;
    return () => {
      isMounted.current = false;
    };
  }, []);
  return isMounted;
}

function FieldMessage({ message }) {
  return message ? (
    <p role="alert" className="text-xs text-danger">
      {message}
    </p>
  ) : null;
}

/**
 * 거래 장소(REQ-006 주소·좌표, REQ-030 지도).
 * 카카오맵을 불러오면 지도 방식(`MapTradePlace`), 키가 없거나 불러오지 못하면 주소 직접 입력 + "현재 위치로 가져오기"
 * (`ManualTradePlace`). 좌표는 폼 값에만 두고 화면에 숫자로 보이지 않는다.
 */
export default function TradePlaceField(props) {
  const { status, kakao } = useKakaoMap();
  if (status === 'error') {
    return <ManualTradePlace {...props} />;
  }
  return <MapTradePlace {...props} kakao={kakao} />;
}

/**
 * 지도 방식. 주소 칸은 읽기 전용이고, 검색 결과 선택·지도 클릭·마커 끌기·현재 위치 중 하나로 주소와 좌표를 **같이** 바꾼다
 * (주소는 도로명, 없으면 지번). 좌표→주소를 찾는 동안은 제출을 막고(isLocating), 찾지 못하면 값을 그대로 둔다.
 * kakao가 null이면 SDK를 불러오는 중이다(검색·지도 자리만 스켈레톤, 검증은 그대로 걸린다).
 */
function MapTradePlace({
  kakao,
  control,
  register,
  setValue,
  error,
  disabled,
  isLocating,
  onLocatingChange,
}) {
  const [latitude, longitude] = useWatch({ control, name: ['latitude', 'longitude'] });
  const point = latitude != null && longitude != null ? { lat: latitude, lng: longitude } : null;
  const [query, setQuery] = useState('');
  const [results, setResults] = useState(null);
  const [isSearching, setIsSearching] = useState(false);
  const [locatingKind, setLocatingKind] = useState(null);
  const [placeError, setPlaceError] = useState(null);
  const isMounted = useIsMounted();
  // 요청 번호. 새 요청(검색·고르기)이 시작되면 이전 응답은 버린다
  const searchToken = useRef(0);
  const pickToken = useRef(0);

  function apply(place) {
    setValue('address', place.address, { shouldValidate: true });
    setValue('longitude', place.lng);
    setValue('latitude', place.lat, { shouldValidate: true });
  }

  async function handleSearch() {
    const keyword = query.trim();
    if (!keyword || !kakao) {
      return;
    }
    const token = ++searchToken.current;
    setIsSearching(true);
    setPlaceError(null);
    try {
      const found = await searchPlaces(kakao, keyword);
      if (isMounted.current && token === searchToken.current) {
        setResults(found);
      }
    } catch (searchError) {
      if (isMounted.current && token === searchToken.current) {
        setResults(null);
        setPlaceError(getErrorMessage(searchError));
      }
    } finally {
      if (isMounted.current && token === searchToken.current) {
        setIsSearching(false);
      }
    }
  }

  function handleSearchKeyDown(event) {
    // 물품 폼 안이라 엔터가 폼 제출로 가지 않게. 한글 조합 중 엔터는 조합 확정이라 넘긴다
    if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
      event.preventDefault();
      handleSearch();
    }
  }

  // 진행 중인 좌표→주소를 버린다(그 결과가 방금 고른 값을 덮지 않게)
  function cancelPick() {
    pickToken.current += 1;
    setLocatingKind(null);
    onLocatingChange(false);
  }

  function handleSelect(place) {
    cancelPick();
    setPlaceError(null);
    setResults(null);
    apply(place);
  }

  /** @returns {Promise<boolean>} 지도에 남길지(false면 마커를 이전 자리로) */
  async function resolvePick(kind, getPlace) {
    const token = ++pickToken.current;
    const isCurrent = () => isMounted.current && token === pickToken.current;
    setLocatingKind(kind);
    onLocatingChange(true);
    setPlaceError(null);
    try {
      const place = await getPlace();
      if (isCurrent()) {
        setResults(null);
        apply(place);
      }
      return true;
    } catch (pickError) {
      if (!isCurrent()) {
        return true;
      }
      setPlaceError(getErrorMessage(pickError));
      return false;
    } finally {
      if (isCurrent()) {
        setLocatingKind(null);
        onLocatingChange(false);
      }
    }
  }

  function handlePick(coords) {
    return resolvePick('pick', () => reverseGeocode(kakao, coords));
  }

  function handleLocate() {
    resolvePick('locate', async () => reverseGeocode(kakao, await getTradePlaceCoords()));
  }

  return (
    <div className="flex flex-col gap-3">
      {kakao ? (
        <div className="flex flex-col gap-2">
          <div className="flex items-end gap-2">
            <TextField
              label="장소 검색"
              type="search"
              placeholder="주소나 장소 이름"
              enterKeyHint="search"
              className="min-w-0 flex-1"
              value={query}
              disabled={disabled}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={handleSearchKeyDown}
            />
            <Button
              variant="outline"
              className="shrink-0"
              isLoading={isSearching}
              disabled={disabled || query.trim() === ''}
              onClick={handleSearch}
            >
              검색
            </Button>
          </div>
          {results && (
            <PlaceResults results={results} disabled={disabled} onSelect={handleSelect} />
          )}
        </div>
      ) : (
        <Skeleton className="h-12 rounded-lg" />
      )}
      <Controller
        name="latitude"
        control={control}
        rules={{
          validate: (value) => value != null || '장소를 검색하거나 지도에서 골라 주세요',
        }}
        render={({ field, fieldState }) => (
          <div ref={field.ref} tabIndex={-1} className="flex flex-col gap-2 outline-none">
            {kakao && point && (
              <KakaoPlaceMap
                kakao={kakao}
                point={point}
                label="거래 장소 지도"
                onPick={disabled ? undefined : handlePick}
              />
            )}
            {!kakao && <Skeleton className="h-48 rounded-xl" />}
            <div className="flex items-center justify-between gap-3">
              <span aria-live="polite" className="text-caption text-ink-muted">
                {point ? '지도를 눌러 위치를 옮길 수 있어요' : '아직 장소를 고르지 않았어요'}
              </span>
              <Button
                variant="outline"
                size="sm"
                className="shrink-0"
                isLoading={locatingKind === 'locate'}
                disabled={disabled || !kakao || isLocating}
                onClick={handleLocate}
              >
                현재 위치로 가져오기
              </Button>
            </div>
            <FieldMessage message={placeError ?? fieldState.error?.message} />
          </div>
        )}
      />
      <TextField
        label="거래 장소 주소"
        required
        readOnly
        placeholder="장소를 고르면 채워져요"
        hint={`${PUBLIC_NOTICE} ${FEED_NOTICE}`}
        disabled={disabled}
        aria-busy={isLocating || undefined}
        error={error}
        {...register('address', { validate: validateAddress })}
      />
    </div>
  );
}

function PlaceResults({ results, disabled, onSelect }) {
  if (results.length === 0) {
    return <p className="text-caption text-ink-muted">검색 결과가 없어요</p>;
  }
  return (
    <ul
      aria-label="장소 검색 결과"
      className="max-h-60 divide-y divide-divider overflow-y-auto rounded-xl border border-line"
    >
      {results.map((place, index) => (
        <li key={`${place.lat},${place.lng},${index}`}>
          <button
            type="button"
            disabled={disabled}
            onClick={() => onSelect(place)}
            className="flex min-h-11 w-full flex-col items-start px-3.5 py-2.5 text-left hover:bg-surface disabled:cursor-not-allowed"
          >
            <span className="text-body break-words">{place.label}</span>
            {place.label !== place.address && (
              <span className="text-caption break-words text-ink-muted">{place.address}</span>
            )}
          </button>
        </li>
      ))}
    </ul>
  );
}

/**
 * 지도를 불러오지 못했을 때(키 없음·로드 실패): 주소 직접 입력 + "현재 위치로 가져오기".
 * 주소와 좌표를 맞춰 보는 수단이 없어 어긋날 수 있다(수정에서 주소만 바꾸면 기존 좌표 유지 + 안내).
 * 권한 거부여도 버튼을 막지 않는다(브라우저 설정에서 다시 켤 수 있어서).
 */
export function ManualTradePlace({
  control,
  register,
  setValue,
  error,
  isEdit,
  disabled,
  isLocating,
  onLocatingChange,
}) {
  const [isFreshLocation, setIsFreshLocation] = useState(false);
  const [locationError, setLocationError] = useState(null);
  const isMounted = useIsMounted();

  async function handleLocate() {
    onLocatingChange(true);
    setLocationError(null);
    try {
      const { lat, lng } = await getTradePlaceCoords();
      if (isMounted.current) {
        setValue('longitude', lng);
        setValue('latitude', lat, { shouldValidate: true });
        setIsFreshLocation(true);
      }
    } catch (locateError) {
      if (isMounted.current) {
        setLocationError(getErrorMessage(locateError));
      }
    } finally {
      if (isMounted.current) {
        onLocatingChange(false);
      }
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <TextField
        label="거래 장소 주소"
        required
        placeholder="예: 인천 연수구 송도동 24-5"
        hint={isEdit ? '장소가 바뀌었다면 현재 위치를 다시 가져와 주세요' : undefined}
        disabled={disabled}
        error={error}
        {...register('address', { validate: validateAddress })}
      />
      <Controller
        name="latitude"
        control={control}
        rules={{ validate: (latitude) => latitude != null || '현재 위치를 가져와 주세요' }}
        render={({ field, fieldState }) => {
          let status = '아직 위치를 가져오지 않았어요';
          if (isFreshLocation) {
            status = '현재 위치를 가져왔어요';
          } else if (field.value != null) {
            status = '저장된 위치를 쓰고 있어요';
          }
          return (
            <div ref={field.ref} tabIndex={-1} className="flex flex-col gap-1.5 outline-none">
              <div className="flex items-center justify-between gap-3">
                <span aria-live="polite" className="text-caption text-ink-muted">
                  {status}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  className="shrink-0"
                  isLoading={isLocating}
                  disabled={disabled}
                  onClick={handleLocate}
                >
                  현재 위치로 가져오기
                </Button>
              </div>
              <p className="text-xs text-ink-muted">
                {!isEdit && '거래할 장소에서 눌러 주세요. '}
                {PUBLIC_NOTICE} {FEED_NOTICE}
              </p>
              <FieldMessage message={locationError ?? fieldState.error?.message} />
            </div>
          );
        }}
      />
    </div>
  );
}
