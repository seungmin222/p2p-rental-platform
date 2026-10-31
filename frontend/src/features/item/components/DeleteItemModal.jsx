import { ConfirmModal } from '../../../shared/ui';
import { getErrorMessage, getErrorReasons } from '../errorMessages';
import { useDeleteItem } from '../queries';

/**
 * 물품 삭제 확인(REQ-008). 모달이 top layer라 실패 문구(진행 중인 대여 한 건씩 포함)는 모달 안에 보인다.
 * 열린 동안만 렌더해 이전 실패가 남지 않게 한다.
 * @param {{ itemId: number, name: string }} props.item
 * @param {(result: { alreadyDeleted: boolean }) => void} props.onDeleted 삭제 중 화면을 떠났으면 부르지 않는다(이동·토스트 없음)
 */
export default function DeleteItemModal({ item, onClose, onDeleted }) {
  const mutation = useDeleteItem();
  const reasons = getErrorReasons(mutation.error);

  return (
    <ConfirmModal
      open
      onClose={onClose}
      onConfirm={() => mutation.mutate(item.itemId, { onSuccess: onDeleted })}
      title="물품을 삭제할까요?"
      confirmLabel="삭제"
      danger
      isLoading={mutation.isPending}
    >
      <div className="flex flex-col gap-2 text-body">
        <p>"{item.name}"을(를) 삭제하면 피드와 검색에서 사라져요.</p>
        {mutation.error && (
          <div role="alert" className="flex flex-col gap-1 text-caption text-danger">
            <p>{getErrorMessage(mutation.error)}</p>
            {reasons.length > 0 && (
              <ul className="list-disc pl-4">
                {reasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </ConfirmModal>
  );
}
