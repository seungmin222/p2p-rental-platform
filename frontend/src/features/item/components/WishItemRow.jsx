import { Button, Thumbnail, toast } from '../../../shared/ui';
import { getErrorMessage } from '../errorMessages';
import { useToggleWish } from '../queries';
import ItemCard from './ItemCard';
import WishButton from './WishButton';

// 삭제·숨김 물품의 표시 문구(openapi.json WishItemResponse.status 설명). 둘 다 상세는 404라 링크가 없다
const UNAVAILABLE_LABELS = {
  DELETED: '삭제된 물품',
  HIDDEN: '볼 수 없는 물품',
};

/**
 * 관심 목록 한 줄(REQ-022). 볼 수 있는 물품은 검색 한 줄 카드 + 오른쪽 하트(바로 해제·다시 담기),
 * 삭제·숨김 물품은 흐린 빈 썸네일 + 안내 + "삭제"(관심 해제)만. 결과는 onChange(itemId, isWished)로 화면에 알린다.
 * 요청 중에 다시 누르면 무시한다.
 */
export default function WishItemRow({ item, isWished, onChange }) {
  const toggleWish = useToggleWish();

  function toggle(wished) {
    if (toggleWish.isPending) {
      return;
    }
    toggleWish.mutate(
      { itemId: item.itemId, wished },
      {
        onSuccess: (data) => onChange(item.itemId, data.isWished),
        // 그사이 삭제·숨김된 물품을 다시 담으면 404 — 해제한 상태 그대로 두고 알리기만 한다
        onError: (error) => toast.show({ message: getErrorMessage(error), tone: 'danger' }),
      },
    );
  }

  if (item.isUnavailable) {
    const label = UNAVAILABLE_LABELS[item.status];
    return (
      <div className="flex items-center gap-3.5 py-3.5">
        <Thumbnail url={null} className="size-26 opacity-60" />
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="font-semibold text-ink-muted">{label}</span>
          <span className="text-caption text-ink-subtle">더 이상 볼 수 없는 물품이에요</span>
        </div>
        <Button
          variant="secondary"
          size="sm"
          aria-label={`${label} 관심 해제`}
          isLoading={toggleWish.isPending}
          onClick={() => toggle(false)}
        >
          삭제
        </Button>
      </div>
    );
  }
  return (
    <div className="flex items-center gap-1">
      <div className="min-w-0 flex-1">
        <ItemCard item={item} />
      </div>
      <WishButton
        label={`${item.name} 관심`}
        isWished={isWished}
        isPending={toggleWish.isPending}
        onClick={() => toggle(!isWished)}
      />
    </div>
  );
}
