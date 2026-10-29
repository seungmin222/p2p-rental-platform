// Enum 코드 → 라벨 매핑은 이 파일 한 곳에서 한다(docs/state-machines.md §1).

/** tone은 shared/ui Badge의 tone과 1:1 */
export const RENTAL_STATUS = {
  PENDING_PAYMENT: { label: '결제 대기', tone: 'gray' },
  RESERVED: { label: '대여 예정', tone: 'blue' },
  RENTING: { label: '대여 중', tone: 'green' },
  OVERDUE: { label: '연체', tone: 'red' },
  RETURNED: { label: '상태 확인 대기', tone: 'amber' },
  COMPLETED: { label: '거래 완료', tone: 'ink' },
  CANCELED: { label: '취소', tone: 'gray' },
};

/** REQ-019 빌린 내역 탭(키 = API tab 값). state-machines.md "UI 탭 매핑" */
export const RENTAL_TABS = {
  UPCOMING: { label: '대여 예정', statuses: ['PENDING_PAYMENT', 'RESERVED'] },
  ONGOING: { label: '대여 중', statuses: ['RENTING', 'OVERDUE', 'RETURNED'] },
  DONE: { label: '완료', statuses: ['COMPLETED', 'CANCELED'] },
};

/** REQ-012 캘린더 날짜 상태 */
export const RENTAL_DAY_STATUS = {
  AVAILABLE: { label: '예약 가능' },
  BOOKED: { label: '예약됨' },
  UNAVAILABLE: { label: '예약 불가' },
};

// policies.md rental.min-days / rental.max-days (최초 예약 기준)
export const RENTAL_PERIOD = { MIN_DAYS: 1, MAX_DAYS: 30 };

// policies.md inspection.images (인계·반납 사진 1~5장, jpg/png, 장당 10MB). 상태 확인 '이상' 사진도 같은 값
export const INSPECTION_IMAGES = {
  MIN_COUNT: 1,
  MAX_COUNT: 5,
  MAX_FILE_SIZE: 10 * 1024 * 1024,
  ACCEPT: ['image/jpeg', 'image/png'],
};

/** 결제 플래그(1순위 false). 백엔드 payment-enabled와 맞춘다(frontend/CLAUDE.md "개발 방식") */
export const PAYMENT_ENABLED = import.meta.env.VITE_PAYMENT_ENABLED === 'true';

/** 글자 수 상한 */
export const RENTAL_TEXT_MAX = {
  CANCEL_REASON: 200, // policies.md cancel.reason-text-max (사유 OTHER일 때 필수)
  HANDOVER_MEMO: 300, // policies.md inspection.handover-memo-max
  RETURN_NOTE: 300, // policies.md return.note-max ('아니오' 항목이 있으면 필수)
  ABNORMAL_NOTE: 500, // policies.md inspection.abnormal-note-max
};

// policies.md return.auto-confirm-days (반납 요청 후 3일 → 자동 '정상')
export const RETURN_AUTO_CONFIRM_DAYS = 3;

// policies.md payment.expire-minutes (예약 후 15분 안에 결제하지 않으면 자동 취소)
export const PAYMENT_EXPIRE_MINUTES = 15;

// policies.md §2 취소 수수료: cancel-fee.free-until-days-before(오늘 ≤ 시작일 − 3일이면 무료),
// cancel-fee.rate-percent(대여자가 시작일 2일 전 ~ 전날 취소하면 대여료의 30%, 원 미만 절사)
export const CANCEL_FEE = { FREE_UNTIL_DAYS_BEFORE: 3, RATE_PERCENT: 30 };

/** state-machines.md §5 CancelReason */
export const CANCEL_REASON = {
  SCHEDULE_CHANGE: { label: '일정 변경' },
  ITEM_PROBLEM: { label: '물품 문제' },
  COUNTERPART_REQUEST: { label: '상대방 요청' },
  OTHER: { label: '기타' }, // 사유 텍스트 필수
};

/** state-machines.md §5 CancelType */
export const CANCEL_TYPE = {
  RENTER: { label: '대여자 취소' },
  OWNER: { label: '제공자 취소' },
  MUTUAL: { label: '양측 동의 취소' },
  PAYMENT_EXPIRED: { label: '결제 시간 만료' },
  AMOUNT_MISMATCH: { label: '결제 금액 불일치' },
  NO_HANDOVER: { label: '인계 없음 자동 취소' },
};

/** state-machines.md §5 ReturnChecklistItem. question은 반납 폼의 예/아니오 질문 */
export const RETURN_CHECKLIST_ITEM = {
  NO_EXTERIOR_DAMAGE: { label: '외관 파손 없음', question: '외관에 파손이 없나요?' },
  ALL_PARTS_INCLUDED: { label: '구성품 전부', question: '구성품이 모두 있나요?' },
  NO_CONTAMINATION: { label: '오염 없음', question: '오염 없이 정리했나요?' },
  WORKS_NORMALLY: { label: '정상 작동', question: '정상적으로 작동하나요?' },
  CONSUMABLES_RESTORED: {
    label: '충전·소모품 원상 복구',
    question: '충전·소모품을 원래대로 해 두었나요?',
  },
};

/** state-machines.md §5 InspectionResult (제공자 상태 확인 판정) */
export const INSPECTION_RESULT = {
  NORMAL: { label: '정상' },
  ABNORMAL: { label: '이상' },
};
