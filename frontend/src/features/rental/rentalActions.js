// 대여 상세(SCR-08) 하단 버튼 표. 상태 × 내 역할로 보일 버튼과 안내를 정한다. 버튼 노출 조건은 이 파일에만 둔다.
// 서버가 최종 검증하므로 UI는 보조다(state-machines.md §1 전이 표).
import { compareDate, formatMonthDay } from '../../shared/lib/date';
import { PAYMENT_ENABLED, RETURN_AUTO_CONFIRM_DAYS } from './constants';
import { formatDateTime, overdueDays } from './rentalData';

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
};

const A = RENTAL_ACTION;

function action(type, label, variant, disabledReason = null) {
  return { type, label, variant, disabledReason };
}

/**
 * @param {object} rental RentalDetailResponse
 * @param {object | null | undefined} returnInspection RETURN 기록. undefined = 아직 못 불러옴, null = 없음
 * @param {string} today "YYYY-MM-DD" (호출부가 todayInSeoul()로 넘긴다)
 * @param {{isPaymentEnabled?: boolean}} [options]
 * @returns {{actions: {type: string, label: string, variant: string, disabledReason: string | null}[],
 *   notice: {text?: string, tone: 'default' | 'danger', paymentExpiresAt?: string} | null}}
 *   disabledReason이 있으면 비활성 버튼 + 그 사유를 보여 준다. notice.paymentExpiresAt이면 남은 결제 시간을 센다.
 */
export function getRentalActions(
  rental,
  returnInspection,
  today,
  { isPaymentEnabled = PAYMENT_ENABLED } = {},
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
          ? { tone: 'default', paymentExpiresAt: rental.paymentExpiresAt }
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
      return {
        actions: [action(A.RETURN_REQUEST, isOwner ? '반납 대리 접수' : '반납 요청', 'primary')],
        notice: days > 0 ? { tone: 'danger', text: `반납 예정일이 ${days}일 지났어요` } : null,
      };
    }

    case 'RETURNED':
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

    default:
      // COMPLETED·CANCELED
      return { actions: [], notice: null };
  }
}
