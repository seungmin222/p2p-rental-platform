import { formatPrice } from '../../../shared/lib/format';
import { ConfirmModal, toast } from '../../../shared/ui';
import { DAMAGE_GRADE } from '../../payment/constants';
import { getErrorMessage } from '../errorMessages';
import { useDepositAction } from '../queries';
import { FormError } from './FormParts';

/**
 * 보증금 차감 제안에 수락·거절(대여자, REQ-049). 최신 제안의 proposalId를 보낸다(다른 제안에 잘못 응답하지 않게).
 * 제안 내용(사유·사진)은 상세의 보증금 카드에 있다.
 */
export default function DeductionRespondModal({ rental, action, onClose }) {
  const mutation = useDepositAction(rental.rentalId);
  const proposal = rental.latestDeductionProposal;
  const isAccept = action === 'ACCEPT';
  const grade = DAMAGE_GRADE[proposal.grade]?.label ?? proposal.grade;

  function handleConfirm() {
    mutation.mutate(
      { request: { action, proposalId: proposal.proposalId } },
      {
        onSuccess: (result) => {
          onClose();
          let message = '차감 제안을 거절했어요';
          if (isAccept) {
            // 반환이 실패했거나 결과를 모르면 200 + RETURNED(재시도 배치가 완료한다)
            message =
              result.status === 'COMPLETED'
                ? '차감 제안을 수락했어요. 거래가 완료됐어요'
                : '차감 제안을 수락했어요. 보증금 반환을 처리하고 있어요';
          }
          toast.show({ message });
        },
      },
    );
  }

  return (
    <ConfirmModal
      open
      onClose={onClose}
      onConfirm={handleConfirm}
      title={isAccept ? '차감 제안을 수락할까요?' : '차감 제안을 거절할까요?'}
      confirmLabel={isAccept ? '수락' : '거절'}
      cancelLabel="닫기"
      danger={isAccept}
      isLoading={mutation.isPending}
    >
      <div className="flex flex-col gap-2">
        <p className="font-semibold tabular-nums">
          {grade} · {formatPrice(proposal.amount)}
        </p>
        <p>
          {isAccept
            ? '수락하면 이 금액을 보증금에서 빼고 남은 금액을 돌려받아요. 거래가 완료돼요.'
            : '거절하면 보증금 보류가 이어지고, 제공자가 다시 제안할 수 있어요.'}
        </p>
        {mutation.error && <FormError message={getErrorMessage(mutation.error)} />}
      </div>
    </ConfirmModal>
  );
}
