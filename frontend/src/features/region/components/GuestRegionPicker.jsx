import { Link, useNavigate } from 'react-router';

import { toLoginPath } from '../../../app/loginPath';
import { toast } from '../../../shared/ui';
import { useGuestRegionStore } from '../guestRegionStore';
import RegionFinder from './RegionFinder';

const LINK =
  'inline-flex min-h-11 items-center text-caption font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary';

/**
 * 비회원 임시 동네 선택(SCR-02, member.md REQ-004): 검색·현재 위치로 하나를 골라 이 브라우저에만 저장하고 홈으로.
 * 인증·내 동네 목록은 없다. 좌표는 받지 않는다(isGuest).
 */
export default function GuestRegionPicker() {
  const navigate = useNavigate();
  const guestRegion = useGuestRegionStore((state) => state.guestRegion);
  const setGuestRegion = useGuestRegionStore((state) => state.setGuestRegion);

  function getSelectState(region) {
    return region.regionId === guestRegion?.regionId
      ? { label: '선택됨', disabled: true }
      : { label: '선택', disabled: false };
  }

  function handleSelect(region) {
    setGuestRegion(region);
    toast.show({ message: `${region.name}을 동네로 정했어요` });
    navigate('/', { replace: true });
  }

  return (
    <div className="flex flex-col gap-6 py-4">
      <section aria-labelledby="guest-region-title" className="flex flex-col gap-1">
        <h2 id="guest-region-title" className="text-heading">
          지금 동네
        </h2>
        <p className="text-body">
          {guestRegion ? guestRegion.fullName : '아직 동네를 고르지 않았어요'}
        </p>
        <p className="flex flex-wrap items-center gap-x-2 text-caption text-ink-muted">
          <span>로그인하면 동네를 인증할 수 있어요</span>
          <Link to={toLoginPath('/regions')} className={LINK}>
            로그인
          </Link>
        </p>
      </section>

      <section aria-labelledby="find-region-title" className="flex flex-col gap-3">
        <h2 id="find-region-title" className="text-heading">
          동네 찾기
        </h2>
        <RegionFinder isGuest getSelectState={getSelectState} onSelect={handleSelect} />
      </section>
    </div>
  );
}
