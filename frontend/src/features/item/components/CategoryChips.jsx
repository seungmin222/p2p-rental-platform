import { Chip } from '../../../shared/ui';
import { useCategories } from '../queries';

/**
 * 카테고리 칩 가로 스크롤(전체 + 활성 카테고리). 선택은 호출부가 URL ?category=에 둔다.
 * 카테고리를 못 받아도 "전체"는 보여 피드는 쓸 수 있다.
 */
export default function CategoryChips({ categoryId, onChange }) {
  const { data: categories = [] } = useCategories();
  return (
    <div
      role="group"
      aria-label="카테고리"
      className="-mx-4 flex [scrollbar-width:none] gap-2 overflow-x-auto px-4 py-3 [&::-webkit-scrollbar]:hidden"
    >
      <Chip selected={categoryId == null} onClick={() => onChange(null)}>
        전체
      </Chip>
      {categories.map((category) => (
        <Chip
          key={category.categoryId}
          selected={categoryId === category.categoryId}
          onClick={() => onChange(category.categoryId)}
        >
          {category.name}
        </Chip>
      ))}
    </div>
  );
}
