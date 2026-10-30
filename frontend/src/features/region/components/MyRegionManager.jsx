import { useLocation, useNavigate } from 'react-router';

import { Button, toast } from '../../../shared/ui';
import { REGION_MAX_PER_MEMBER } from '../constants';
import { getErrorMessage } from '../errorMessages';
import { LOCATION_ERROR, LocationError } from '../geolocation';
import { isRegionAdded, isRegionLimitReached } from '../myRegions';
import { useAddRegion, useMyRegions, useRegionMutating, useVerifyRegion } from '../queries';
import MyRegionList from './MyRegionList';
import RegionFinder from './RegionFinder';

const SECTION_TITLE = 'flex items-baseline justify-between text-heading';

/**
 * 로그인 사용자의 동네 설정(SCR-02): 내 동네 목록 + 추가. 추가·인증 후에도 이 화면에 머문다.
 * 로그인 직후(state.fromLogin)이고 동네가 1개 이상이면 아래에 "홈으로 가기".
 */
export default function MyRegionManager() {
  const location = useLocation();
  const navigate = useNavigate();
  const regions = useMyRegions();
  const add = useAddRegion();
  const verify = useVerifyRegion();
  const isMutating = useRegionMutating();

  const list = regions.data ?? [];
  const isLimitReached = isRegionLimitReached(list);
  const isFromLogin = location.state?.fromLogin === true;

  function getSelectState(region) {
    return isRegionAdded(list, region.regionId)
      ? { label: '추가됨', disabled: true }
      : { label: '추가', disabled: !regions.isSuccess };
  }

  /**
   * 추가. 현재 위치로 찾은 동네면(takeCoords 있음) 같은 좌표로 바로 인증한다(위치를 다시 받지 않음).
   * 결과: 둘 다 성공 / 추가만 성공(인증 실패 문구 앞에 "동네는 추가했어요.") / 추가 실패(좌표도 버림)
   */
  async function handleSelect(region, { takeCoords }) {
    let added;
    try {
      added = await add.mutateAsync({ regionId: region.regionId });
    } catch (error) {
      takeCoords?.();
      toast.show({ message: getErrorMessage(error), tone: 'danger' });
      return;
    }
    if (!takeCoords) {
      toast.show({ message: `${region.name}을 추가했어요. 목록에서 인증해 주세요` });
      return;
    }
    try {
      await verify.mutateAsync({
        memberRegionId: added.memberRegionId,
        locate: async () => {
          const coords = takeCoords();
          if (!coords) {
            throw new LocationError(LOCATION_ERROR.UNAVAILABLE);
          }
          return coords;
        },
      });
      toast.show({ message: `${region.name}을 추가하고 인증했어요` });
    } catch (error) {
      toast.show({ message: `동네는 추가했어요. ${getErrorMessage(error)}`, tone: 'danger' });
    }
  }

  return (
    <div className="flex flex-col gap-8 py-4">
      <section aria-labelledby="my-regions-title">
        <h2 id="my-regions-title" className={SECTION_TITLE}>
          내 동네
          {regions.isSuccess && (
            <span className="text-caption text-ink-muted">
              {list.length}/{REGION_MAX_PER_MEMBER}
            </span>
          )}
        </h2>
        <MyRegionList query={regions} />
      </section>

      <section aria-labelledby="add-region-title" className="flex flex-col gap-3">
        <h2 id="add-region-title" className={SECTION_TITLE}>
          동네 추가
        </h2>
        <RegionFinder
          keepsCoords
          getSelectState={getSelectState}
          onSelect={handleSelect}
          isBusy={isMutating}
          lockedNotice={
            isLimitReached
              ? `동네는 ${REGION_MAX_PER_MEMBER}개까지 추가할 수 있어요. 기존 동네를 삭제한 뒤 추가해 주세요`
              : null
          }
          locateHint="추가하면 지금 위치로 바로 인증돼요"
        />
      </section>

      {isFromLogin && list.length > 0 && (
        <Button fullWidth onClick={() => navigate('/', { replace: true })}>
          홈으로 가기
        </Button>
      )}
    </div>
  );
}
