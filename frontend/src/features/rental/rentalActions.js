// 대여 상세(SCR-08) 하단 버튼 표. 상태 × 내 역할로 보일 버튼과 안내를 정한다. 버튼 노출 조건은 이 파일에만 둔다.
// 서버가 최종 검증하므로 UI는 보조다(state-machines.md §1 전이 표).
import { compareDate, formatMonthDay } from '../../shared/lib/date';
import { EXTENSION_POLICY, PAYMENT_ENABLED, RETURN_AUTO_CONFIRM_DAYS } from './constants';
import { canReportLost, getProposalState } from './deposit';
import { formatDateTime, isOverdueCancelWaiver, overdueDays } from './rentalData';

/** 버튼 종류. 화면은 이 값으로 열 모달·시트를 고른다. */
export const RENTAL_ACTION = {
  PAY: 'PAY', // 결제하기(대여자만)
  CANCEL: 'CANCEL', // 단독 취소
  REQUEST_CANCEL_AGREEMENT: 'REQUEST_CANCEL_AGREEMENT', // 양측 동의 취소 요청
  CANCEL_AGREEMENT_PENDING: 'CANCEL_AGREEMENT_PENDING', // 내가 보낸 요청(항상 비활성)
  REJECT_CANCEL_AGREEMENT: 'REJECT_CANCEL_AGREEMENT',
  ACCEPT_CANCEL_AGREEMENT: 'ACCEPT_CANCEL_AGREEMENT',
  HANDOVER: 'HANDOVER',
  RETURN_REQUEST: 'RETURN_REQUEST', // 대여자 반납 요청 / 제공자 대리 접수(같은 API)
  CONFIRM_ABNORMAL: 'CONFIRM_ABNORMAL',
  CONFIRM_NORMAL: 'CONFIRM_NORMAL',
  WRITE_REVIEW: 'WRITE_REVIEW', // 후기 쓰기(SCR-14로 이동, REQ-034)
  EXTEND: 'EXTEND', // 연장하기(대여자, REQ-038) → 새 종료일 시트
  PAY_EXTENSION: 'PAY_EXTENSION', // 결제 대기 연장의 연장료 결제(모달 없이 결제창)
  REPORT_LOST: 'REPORT_LOST', // 미반납 신고(제공자, REQ-018) → 확인 모달
  PROPOSE_DEDUCTION: 'PROPOSE_DEDUCTION', // 보증금 차감 제안(제공자, REQ-049) → 시트
  REJECT_DEDUCTION: 'REJECT_DEDUCTION', // 차감 제안 거절(대여자) → 확인 모달
  ACCEPT_DEDUCTION: 'ACCEPT_DEDUCTION', // 차감 제안 수락(대여자) → 확인 모달
};

const A = RENTAL_ACTION;

function action(type, label, variant, disabledReason = null) {
  return { type, label, variant, disabledReason };
}

/**
 * @param {object} rental RentalDetailResponse
 * @param {object | null | undefined} returnInspection RETURN 기록. undefined = 아직 못 불러옴, null = 없음
 * @param {string} today "YYYY-MM-DD" (호출부가 todayInSeoul()로 넘긴다)
 * @param {{isPaymentEnabled?: boolean, now?: number}} [options] now(밀리초)는 차감 제안 응답 기한 판단용
 * @returns {{actions: {type: string, label: string, variant: string, disabledReason: string | null}[],
 *   notice: {text?: string, tone: 'default' | 'danger', paymentExpiresAt?: string, paymentKind?: 'RENTAL' | 'EXTENSION'} | null}}
 *   disabledReason이 있으면 비활성 버튼 + 그 사유를 보여 준다. notice.paymentExpiresAt이면 남은 결제 시간을 센다
 *   (paymentKind가 EXTENSION이면 연장 결제 시간).
 */
export function getRentalActions(
  rental,
  returnInspection,
  today,
  { isPaymentEnabled = PAYMENT_ENABLED, now = Date.now() } = {},
) {
  const isOwner = rental.myRole === 'OWNER';

  switch (rental.status) {
    case 'PENDING_PAYMENT':
      return {
        actions: [
          action(A.CANCEL, '예약 취소', 'secondary'),
          ...(isPaymentEnabled && !isOwner ? [action(A.PAY, '결제하기', 'primary')] : []),
        ],
        // 결제가 꺼져 있으면 결제할 수 없으므로 남은 시간도 세지 않는다
        notice: isPaymentEnabled
          ? { tone: 'default', paymentExpiresAt: rental.paymentExpiresAt, paymentKind: 'RENTAL' }
          : null,
      };

    case 'RESERVED': {
      if (compareDate(today, rental.startDate) < 0) {
        return {
          actions: [
            action(A.CANCEL, '예약 취소', 'secondary'),
            ...(isOwner
              ? [
                  action(
                    A.HANDOVER,
                    '인계 확인',
                    'primary',
                    `${formatMonthDay(rental.startDate)}부터 인계할 수 있어요`,
                  ),
                ]
              : []),
          ],
          notice: null,
        };
      }
      const agreement = rental.activeCancelAgreement;
      // 앞 대여 연체(open-issues #7): 대여자는 시작일 뒤에도 수수료 없이 단독 취소. 동의 요청은 필요 없어 숨기고,
      // 상대가 보낸 요청이 있으면 거절·동의는 그대로 둔다
      if (isOverdueCancelWaiver(rental, today)) {
        return {
          actions: [
            ...(agreement && !agreement.requestedByMe
              ? [
                  action(A.REJECT_CANCEL_AGREEMENT, '거절', 'secondary'),
                  action(A.ACCEPT_CANCEL_AGREEMENT, '동의', 'outline'),
                ]
              : []),
            action(A.CANCEL, '예약 취소', 'secondary'),
          ],
          notice: {
            tone: 'danger',
            text: '앞 대여의 물품이 아직 반납되지 않았어요. 수수료 없이 예약을 취소할 수 있어요',
          },
        };
      }
      let cancelActions;
      if (!agreement) {
        cancelActions = [action(A.REQUEST_CANCEL_AGREEMENT, '취소 요청', 'secondary')];
      } else if (agreement.requestedByMe) {
        cancelActions = [
          action(
            A.CANCEL_AGREEMENT_PENDING,
            '취소 요청 중',
            'secondary',
            `${formatDateTime(agreement.expiresAt)}까지 상대의 응답을 기다려요`,
          ),
        ];
      } else {
        cancelActions = [
          action(A.REJECT_CANCEL_AGREEMENT, '거절', 'secondary'),
          action(A.ACCEPT_CANCEL_AGREEMENT, '동의', 'outline'),
        ];
      }
      return {
        actions: [
          ...cancelActions,
          ...(isOwner ? [action(A.HANDOVER, '인계 확인', 'primary')] : []),
        ],
        notice: null,
      };
    }

    case 'RENTING':
    case 'OVERDUE': {
      const days = rental.status === 'OVERDUE' ? overdueDays(rental.endDate, today) : 0;
      const returnAction = action(
        A.RETURN_REQUEST,
        isOwner ? '반납 대리 접수' : '반납 요청',
        'primary',
      );
      if (isOwner || rental.status !== 'RENTING') {
        return {
          // 미반납 신고는 반납 예정일 + lost-report.after-days가 지난 제공자에게만(그 전에는 숨김)
          actions: canReportLost(rental, today)
            ? [action(A.REPORT_LOST, '미반납 신고', 'secondary'), returnAction]
            : [returnAction],
          notice: days > 0 ? { tone: 'danger', text: `반납 예정일이 ${days}일 지났어요` } : null,
        };
      }
      return getRenterRentingActions(rental, today, returnAction);
    }

    case 'RETURNED':
      // 보증금 보류(이상 판정·미반납 신고)는 차감 협의(REQ-049). 미반납 신고 건은 판정 전이어도 상태 확인을 하지 않는다(#41)
      if (rental.depositStatus === 'HELD') {
        return returnInspection === undefined
          ? { actions: [], notice: null }
          : getHeldActions(rental, isOwner, now);
      }
      // 기록을 못 불러왔거나 이미 판정했으면 버튼·안내를 그리지 않는다
      if (!returnInspection || returnInspection.result != null) {
        return { actions: [], notice: null };
      }
      if (isOwner) {
        return {
          actions: [
            action(A.CONFIRM_ABNORMAL, '이상 있음', 'danger'),
            action(A.CONFIRM_NORMAL, '정상 확인', 'primary'),
          ],
          notice: null,
        };
      }
      return {
        actions: [],
        notice: {
          tone: 'default',
          text: `제공자가 상태를 확인하고 있어요. 반납 후 ${RETURN_AUTO_CONFIRM_DAYS}일이 지나면 자동으로 정상 처리돼요`,
        },
      };

    case 'COMPLETED':
      // 후기(REQ-034): 쓸 수 있는지는 서버 canWriteReview, 이미 썼는지는 reviewWritten. 기한이 지났으면 아무것도 없다
      if (rental.canWriteReview) {
        return { actions: [action(A.WRITE_REVIEW, '후기 쓰기', 'primary')], notice: null };
      }
      return {
        actions: [],
        notice: rental.reviewWritten ? { tone: 'default', text: '후기를 보냈어요' } : null,
      };

    default:
      // CANCELED
      return { actions: [], notice: null };
  }
}

/**
 * 대여 중(RENTING)인 대여자: 반납 요청 + 연장(REQ-038).
 * - 결제 대기 연장(pendingExtension)이 있으면 "연장 결제하기"와 남은 연장 결제 시간(대여당 1건이라 새 연장은 못 한다)
 * - 종료일이 지났으면(연체 배치 전) 연장 버튼 없음(서버 409 RENTAL_EXTENSION_OVERDUE)
 * - 확정된 연장이 extension.max-count에 닿으면 비활성 + 사유
 */
function getRenterRentingActions(rental, today, returnAction) {
  const pending = rental.pendingExtension;
  if (pending) {
    return {
      actions: [
        { ...returnAction, variant: 'secondary' },
        action(A.PAY_EXTENSION, '연장 결제하기', 'primary'),
      ],
      notice: { tone: 'default', paymentExpiresAt: pending.expiresAt, paymentKind: 'EXTENSION' },
    };
  }
  if (compareDate(today, rental.endDate) > 0) {
    return { actions: [returnAction], notice: null };
  }
  const isLimitReached = (rental.extensionCount ?? 0) >= EXTENSION_POLICY.MAX_COUNT;
  return {
    actions: [
      action(
        A.EXTEND,
        '연장하기',
        'secondary',
        isLimitReached ? `연장은 ${EXTENSION_POLICY.MAX_COUNT}번까지 할 수 있어요` : null,
      ),
      returnAction,
    ],
    notice: null,
  };
}

/**
 * 보증금 보류(HELD) 중인 RETURNED: 최신 차감 제안(latestDeductionProposal) 상태로 정한다.
 * - 제공자: 제안 없음·거절됨이면 "차감 제안", 응답 대기면 안내만
 * - 대여자: 응답 기한 전 제안이 있으면 거절·수락, 그 밖에는 안내만
 * 기한이 지난 PROPOSED는 응답할 수 없어(409 DEDUCTION_PROPOSAL_CLOSED, open-issues #43) 자동 수락 안내만 둔다.
 */
function getHeldActions(rental, isOwner, now) {
  const proposal = rental.latestDeductionProposal;
  const state = getProposalState(proposal, now);
  const info = (text) => ({ actions: [], notice: { tone: 'default', text } });

  if (state === 'CLOSED') {
    return info('응답 기한이 지나 자동 수락을 처리하고 있어요');
  }
  if (state === 'DONE') {
    return info('보증금 반환을 처리하고 있어요');
  }
  if (isOwner) {
    if (state === 'OPEN') {
      return info(`${formatDateTime(proposal.respondDeadline)}까지 대여자의 응답을 기다려요`);
    }
    return {
      actions: [
        action(A.PROPOSE_DEDUCTION, state === 'REJECTED' ? '다시 제안' : '차감 제안', 'primary'),
      ],
      notice: null,
    };
  }
  if (state === 'OPEN') {
    return {
      actions: [
        action(A.REJECT_DEDUCTION, '거절', 'secondary'),
        action(A.ACCEPT_DEDUCTION, '수락', 'primary'),
      ],
      notice: {
        tone: 'default',
        text: `${formatDateTime(proposal.respondDeadline)}까지 응답하지 않으면 자동으로 수락돼요`,
      },
    };
  }
  return info(
    state === 'REJECTED'
      ? '제안을 거절했어요. 제공자가 다시 제안할 수 있어요'
      : '제공자가 보증금 차감을 정리하고 있어요',
  );
}
