import PageHeader from '../app/layout/PageHeader';
import ItemListView from '../features/item/components/ItemListView';
import { useRecentItems } from '../features/item/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { ButtonLink } from '../shared/ui';

/**
 * SCR-12 최근 본 물품(REQ-035). 최근 본 순 최대 recent-items.max건(페이지 없음), 카드는 검색 한 줄 카드와 같다.
 * 삭제·숨김 물품과 내 물품은 서버가 빼고 준다.
 */
export default function MyRecentItemsPage() {
  const query = useRecentItems();
  useRefetchErrorToast([query]);

  return (
    <>
      <PageHeader title="최근 본 물품" />
      <ItemListView
        query={query}
        items={query.data}
        emptyTitle="최근 본 물품이 없어요"
        emptyDescription="물품 상세를 보면 여기에 모여요"
        emptyAction={
          <ButtonLink to="/" variant="secondary">
            물품 둘러보기
          </ButtonLink>
        }
      />
    </>
  );
}
