// 대여 상세(SCR-08) 스텝바 5단계(시안 RentalDetail.html). 순수 함수라 node 환경에서 테스트한다.
import { PAYMENT_ENABLED } from './constants';

// 상태 → 현재 단계 번호. CANCELED는 스텝바 대신 취소 정보 카드를 그린다.
const CURRENT_STEP = {
  PENDING_PAYMENT: 0,
  RESERVED: 1,
  RENTING: 2,
  OVERDUE: 2,
  RETURNED: 3,
  COMPLETED: 4,
};

/**
 * @param {string} status RentalStatus
 * @param {{isPaymentEnabled?: boolean}} [options]
 * @returns {{label: string, state: 'done' | 'current' | 'upcoming', isDanger: boolean}[] | null}
 *   CANCELED(또는 모르는 상태)면 null. COMPLETED는 다섯 단계 모두 done. OVERDUE는 "대여 중"이 isDanger.
 */
export function getRentalSteps(status, { isPaymentEnabled = PAYMENT_ENABLED } = {}) {
  const current = CURRENT_STEP[status];
  if (current == null) {
    return null;
  }
  const labels = [
    isPaymentEnabled ? '결제' : '예약',
    '대여 예정',
    '대여 중',
    '반납 완료',
    '거래 완료',
  ];
  return labels.map((label, index) => {
    let state = 'upcoming';
    if (index < current || status === 'COMPLETED') {
      state = 'done';
    } else if (index === current) {
      state = 'current';
    }
    return { label, state, isDanger: status === 'OVERDUE' && index === current };
  });
}
