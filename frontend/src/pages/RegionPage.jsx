import PageHeader from '../app/layout/PageHeader';
import GuestRegionPicker from '../features/region/components/GuestRegionPicker';
import MyRegionManager from '../features/region/components/MyRegionManager';
import { useAuthStore } from '../shared/api';

/** SCR-02 동네 설정·인증(REQ-004). 로그인하면 내 동네 관리, 아니면 임시 동네 선택(이 브라우저에만 저장) */
export default function RegionPage() {
  const isLoggedIn = useAuthStore((state) => state.accessToken !== null);

  return (
    <>
      <PageHeader title="동네 설정" />
      {isLoggedIn ? <MyRegionManager /> : <GuestRegionPicker />}
    </>
  );
}
