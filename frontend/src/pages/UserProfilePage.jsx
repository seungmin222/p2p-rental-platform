import { useParams } from 'react-router';

import PageHeader from '../app/layout/PageHeader';
import ItemListView from '../features/item/components/ItemListView';
import { parseItemId } from '../features/item/itemId';
import ProfileHeader, { ProfileHeaderSkeleton } from '../features/profile/components/ProfileHeader';
import { getErrorMessage } from '../features/profile/errorMessages';
import { useMemberItems, useMemberProfile } from '../features/profile/queries';
import ReceivedReviewList from '../features/review/components/ReceivedReviewList';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { useTabParam } from '../shared/hooks/useTabParam';
import { isInitialLoadError } from '../shared/lib/queryState';
import { ButtonLink, ErrorState, Tabs } from '../shared/ui';

const PROFILE_TABS = { items: { label: '등록 물품' }, reviews: { label: '받은 후기' } };
const TAB_ITEMS = Object.entries(PROFILE_TABS).map(([value, { label }]) => ({ value, label }));
const DEFAULT_TAB = 'items';

function MemberItemList({ memberId }) {
  const query = useMemberItems(memberId);
  useRefetchErrorToast([query]);
  return <ItemListView query={query} items={query.data} emptyTitle="대여 가능한 물품이 없어요" />;
}

function NotFound() {
  return (
    <>
      <PageHeader title="프로필" />
      <ErrorState title="사용자를 찾을 수 없어요" message="주소를 다시 확인해 주세요" />
      <div className="flex justify-center">
        <ButtonLink to="/" variant="secondary" size="sm" replace>
          홈으로
        </ButtonLink>
      </div>
    </>
  );
}

/**
 * SCR-13 사용자 프로필(REQ-021). 비회원도 볼 수 있다(RequireAuth 밖).
 * 등록 물품(AVAILABLE만) · 받은 후기를 탭(?tab=)으로 나누고, 둘 다 page 방식 무한 스크롤.
 * 탈퇴 회원은 "탈퇴한 사용자"만 보이고 목록 탭을 그리지 않는다(서버도 빈 목록).
 */
export default function UserProfilePage() {
  const params = useParams();
  // 회원 id도 물품 id와 같은 규칙(양의 안전한 정수)이라 같은 함수로 거른다
  const memberId = parseItemId(params.memberId);
  const query = useMemberProfile(memberId);
  const [tab, setTab] = useTabParam(PROFILE_TABS, DEFAULT_TAB);
  useRefetchErrorToast([query]);

  if (memberId == null) {
    return <NotFound />;
  }
  if (isInitialLoadError(query)) {
    if (query.error?.code === 'MEMBER_NOT_FOUND' || query.error?.status === 400) {
      return <NotFound />;
    }
    return (
      <>
        <PageHeader title="프로필" />
        <ErrorState message={getErrorMessage(query.error)} onRetry={query.refetch} />
      </>
    );
  }

  const profile = query.data;
  return (
    <>
      <PageHeader title={profile && !profile.isWithdrawn ? profile.nickname : '프로필'} />
      {profile ? <ProfileHeader profile={profile} /> : <ProfileHeaderSkeleton />}
      {profile && !profile.isWithdrawn && (
        <Tabs
          label="프로필 탭"
          items={TAB_ITEMS}
          value={tab}
          onChange={setTab}
          listClassName="sticky top-14 z-10 -mx-4 bg-white px-4"
        >
          {tab === 'items' ? (
            <MemberItemList key={memberId} memberId={memberId} />
          ) : (
            <ReceivedReviewList key={memberId} memberId={memberId} />
          )}
        </Tabs>
      )}
    </>
  );
}
