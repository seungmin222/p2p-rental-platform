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

// policies.md inspection.images (인계·반납 사진 1~5장, 장당 10MB)
export const INSPECTION_IMAGES = { MIN_COUNT: 1, MAX_COUNT: 5, MAX_FILE_SIZE: 10 * 1024 * 1024 };
