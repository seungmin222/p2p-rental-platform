// 결제·보증금·정산 Enum 코드 → 라벨 매핑은 이 파일 한 곳에서 한다(docs/state-machines.md §2·§3·§4·§5).
// tone은 shared/ui Badge의 tone과 1:1.

/** 결제 상태(§4). READY·FAILED는 결제 내역(REQ-033)에 나오지 않는다 */
export const PAYMENT_STATUS = {
  READY: { label: '결제 대기', tone: 'gray' },
  APPROVED: { label: '결제 완료', tone: 'green' },
  PARTIAL_CANCELED: { label: '부분 환불', tone: 'blue' },
  CANCELED: { label: '환불 완료', tone: 'gray' },
  FAILED: { label: '결제 실패', tone: 'red' },
};

/** 보증금 상태(§2) */
export const DEPOSIT_STATUS = {
  NONE: { label: '보증금 없음', tone: 'gray' },
  FROZEN: { label: '동결', tone: 'blue' },
  HELD: { label: '보류', tone: 'amber' },
  REFUNDED: { label: '반환', tone: 'green' },
  FORFEITED: { label: '전액 차감', tone: 'red' },
  REFUND_FAILED: { label: '반환 실패', tone: 'red' },
};

/** 정산 상태(§3) */
export const SETTLEMENT_STATUS = {
  SCHEDULED: { label: '지급 예정', tone: 'blue' },
  HELD: { label: '보류', tone: 'amber' },
  PAID: { label: '지급 완료', tone: 'ink' },
};

/** 정산 종류(§5 SettlementSource) */
export const SETTLEMENT_SOURCE = {
  COMPLETED: { label: '거래 완료' },
  CANCEL_FEE: { label: '취소 수수료' },
};

/** 결제 종류(PaymentHistoryResponse.paymentType) */
export const PAYMENT_TYPE = {
  RENTAL: { label: '대여료·보증금' },
  EXTENSION: { label: '연장료' },
};

/** SCR-12 /me/payments 탭(URL ?tab=). 정산은 같은 화면 별도 탭(payment.md REQ-033) */
export const PAYMENT_PAGE_TABS = {
  payments: { label: '결제·보증금' },
  settlements: { label: '정산' },
};

// policies.md payment-history.default-range (from·to를 비우면 서버가 최근 3개월로 조회). 날짜 계산은 서버가 하고 화면은 문구로만 쓴다
export const PAYMENT_HISTORY_DEFAULT_RANGE_MONTHS = 3;

// policies.md settlement.payout-delay (거래 완료일 기준 영업일 +2일, 주말만 제외). 정산 탭 안내 문구에만 쓴다
export const SETTLEMENT_PAYOUT_DELAY_BUSINESS_DAYS = 2;
