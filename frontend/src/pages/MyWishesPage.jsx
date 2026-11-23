import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import PageHeader from '../app/layout/PageHeader';
import ItemListView from '../features/item/components/ItemListView';
import WishItemRow from '../features/item/components/WishItemRow';
import { removeWishRowFromCache, useMyWishes } from '../features/item/queries';
import { useRefetchErrorToast } from '../shared/hooks/useRefetchErrorToast';
import { ButtonLink } from '../shared/ui';

/**
 * SCR-12 관심 목록(REQ-022). 최근 담은 순, page/hasNext 무한 스크롤.
 * 이 화면에서 해제한 물품은 빈 하트로 남겨 다시 담을 수 있게 한다(페이지 방식이라 줄을 빼면 다음 페이지가 한 칸 밀린다).
 * 삭제·숨김 물품은 다시 담을 수 없어 해제하면 화면 state와 쿼리 캐시 양쪽에서 줄을 뺀다. 다시 들어오면 서버 목록을 새로 받는다.
 */
export default function MyWishesPage() {
  const queryClient = useQueryClient();
  const query = useMyWishes();
  useRefetchErrorToast([query]);
  // 이 화면에서 바꾼 관심 상태(itemId → isWished). 서버 목록에 있는 항목은 담긴 상태로 시작한다
  const [changed, setChanged] = useState(() => new Map());

  function handleChange(itemId, isWished) {
    setChanged((previous) => new Map(previous).set(itemId, isWished));
    const item = query.data?.find((candidate) => candidate.itemId === itemId);
    if (item?.isUnavailable && !isWished) {
      removeWishRowFromCache(queryClient, itemId);
    }
  }

  const items = query.data?.filter(
    (item) => !(item.isUnavailable && changed.get(item.itemId) === false),
  );

  return (
    <>
      <PageHeader title="관심 목록" />
      <ItemListView
        query={query}
        items={items}
        emptyTitle="관심 목록이 비어 있어요"
        emptyDescription="마음에 드는 물품의 하트를 눌러 담아 보세요"
        emptyAction={
          <ButtonLink to="/" variant="secondary">
            물품 둘러보기
          </ButtonLink>
        }
        renderItem={(item) => (
          <WishItemRow
            item={item}
            isWished={changed.get(item.itemId) ?? true}
            onChange={handleChange}
          />
        )}
      />
    </>
  );
}
