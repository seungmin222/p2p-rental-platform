// 대여 목 시드. 날짜는 todayInSeoul() 기준 상대값(D+n = 오늘 + n일). 나 = 회원 1(동네주민).
// 저장소는 rentalStore.js(sessionStorage). 초기화는 window.__mockPayment.reset().
//
// 대여자 쪽(제공자 캠핑조아·이웃)
// | id | 상태 | 기간 | 경우 |
// | --- | --- | --- | --- |
// | 1 | PENDING_PAYMENT | D+5~D+7 | 결제 만료 12분 전 |
// | 2 | RESERVED | D+4~D+6 | 단독 취소(수수료 없음) |
// | 3 | RESERVED | D+2~D+3 | 단독 취소 수수료 구간(대여료 30%), 물품 101 캘린더에 예약됨 |
// | 4 | RESERVED | D+0~D+2 | 시작일 당일, 동의 요청 없음 → 취소 요청 |
// | 5 | RESERVED | D+0~D+1 | 동의 요청 진행 중, 내가 요청(requestedByMe true) |
// | 6 | RESERVED | D-1~D+1 | 동의 요청 진행 중, 제공자가 요청(requestedByMe false) |
// | 7 | RENTING | D-2~D+3 | 반납 요청 |
// | 8 | OVERDUE | D-7~D-2 | 연체 2일, 삭제된 물품 |
// | 9 | RETURNED | D-5~D-1 | 내가 반납 요청, 판정 전 |
// | 10 | COMPLETED | D-20~D-18 | 정상 판정 |
// | 11 | CANCELED | D+1~D+2 | 대여자 취소(RENTER), 수수료 기록 있음 |
//
// 제공자 쪽(대여자 텐트초보)
// | id | 상태 | 기간 | 경우 |
// | --- | --- | --- | --- |
// | 21 | PENDING_PAYMENT | D+6~D+8 | 결제 대기 |
// | 22 | RESERVED | D+3~D+4 | 인계 확인 비활성(시작일 전) |
// | 23 | RESERVED | D+0~D+2 | 시작일 당일 → 인계 확인 |
// | 24 | RESERVED | D+0~D+1 | 동의 요청 진행 중, 내가 요청 |
// | 25 | RESERVED | D-1~D+2 | 동의 요청 진행 중, 대여자가 요청(사유 OTHER) |
// | 26 | RENTING | D-3~D+2 | 반납 대리 접수 |
// | 27 | OVERDUE | D-6~D-1 | 연체 1일 |
// | 28 | RETURNED | D-6~D-2 | 대여자 반납, 체크리스트 '아니오' 있음, 판정 전 |
// | 29 | RETURNED | D-8~D-4 | 제공자 대리 접수(writerRole OWNER), 판정 전 |
// | 30 | COMPLETED | D-30~D-28 | 이상 판정(1순위라 완료) |
// | 31 | CANCELED | D+2~D+3 | 양측 동의 취소(MUTUAL) |
// | 32 | RESERVED | D+5~D+6 | 물품 107 다가오는 예약(내 물품 대여 가능 탭) |
//
// 목록용(빌린 내역 무한 스크롤·예약 현황 시트 더 불러오기). 상세 화면 경우는 위 표와 같고 새 경우는 없다
// | id | 상태 | 기간 | 쓰임 |
// | --- | --- | --- | --- |
// | 41~65 | COMPLETED(4건마다 CANCELED) | D-30부터 4일 간격으로 과거, 각 3일 | 대여자 쪽, 물품 101·105·102 순환 → 빌린 내역 완료 탭 27건(2페이지) |
// | 71~92 | COMPLETED | D-10부터 4일 간격으로 과거, 각 2일 | 제공자 쪽 물품 107(대여자 텐트초보·이웃 번갈아) → 예약 현황 23건(2페이지) |
//
// 그 밖
// | id | 경우 |
// | --- | --- |
// | 900 | 남의 대여(물품 101, D+8~D+10 RESERVED) → 상세 403 RENTAL_NOT_PARTICIPANT |
// | 901 | 남의 결제 대기(물품 101, D+12~D+13), 결제 만료 지남 → 캘린더에서 예약 가능 |
// | 902 | 남의 결제 대기(물품 101, D+14), 만료 전 → 캘린더에서 예약됨 |
// | 999 | 없음 → 404 RENTAL_NOT_FOUND |
import { addDays, todayInSeoul } from '../shared/lib/date';
import { findMockItem, MOCK_ME, placeholderImage } from './item';

const MINUTE_MS = 60 * 1000;
const SEOUL_OFFSET_MS = 9 * 60 * MINUTE_MS;

/** Date → 서버 형식(오프셋 없는 서울 LocalDateTime) */
export function toServerDateTime(date) {
  return new Date(date.getTime() + SEOUL_OFFSET_MS).toISOString().slice(0, 19);
}

/** 지금부터 minutes분 뒤(음수면 전) */
export function minutesFromNow(minutes) {
  return toServerDateTime(new Date(Date.now() + minutes * MINUTE_MS));
}

/** 오늘 + days일의 hh:mm */
function at(days, time) {
  return `${addDays(todayInSeoul(), days)}T${time}:00`;
}

function photos(label, count) {
  return Array.from({ length: count }, (_, i) => placeholderImage(`${label} ${i + 1}`));
}

function rental(rentalId, itemId, renterId, status, [from, to], extra = {}) {
  const item = findMockItem(itemId);
  const today = todayInSeoul();
  return {
    rentalId,
    itemId,
    renterId,
    ownerId: item.ownerId,
    status,
    startDate: addDays(today, from),
    endDate: addDays(today, to),
    pricePerDay: item.pricePerDay,
    deposit: item.deposit,
    createdAt: at(Math.min(from, 0) - 3, '10:00'),
    paymentExpiresAt: null,
    handedOverAt: null,
    returnedAt: null,
    completedAt: null,
    canceledAt: null,
    cancelType: null,
    cancelReason: null,
    cancelReasonText: null,
    // 백엔드는 int(NOT NULL DEFAULT 0)라 취소 전에도 0
    cancelFee: 0,
    ...extra,
  };
}

function handover(rentalId, days, memo = '충전기 포함, 앞면 작은 흠집 있어요') {
  return {
    rentalId,
    phase: 'HANDOVER',
    imageUrls: photos('인계', 2),
    note: memo,
    checklist: null,
    writerRole: 'OWNER',
    createdAt: at(days, '18:00'),
    result: null,
    resultNote: null,
    resultImageUrls: [],
  };
}

const ALL_YES = {
  NO_EXTERIOR_DAMAGE: true,
  ALL_PARTS_INCLUDED: true,
  NO_CONTAMINATION: true,
  WORKS_NORMALLY: true,
  CONSUMABLES_RESTORED: true,
};

function returnRecord(rentalId, days, extra = {}) {
  return {
    rentalId,
    phase: 'RETURN',
    imageUrls: photos('반납', 2),
    note: null,
    checklist: ALL_YES,
    writerRole: 'RENTER',
    createdAt: at(days, '19:00'),
    result: null,
    resultNote: null,
    resultImageUrls: [],
    ...extra,
  };
}

function agreement(agreementId, rentalId, requesterId, extra = {}) {
  return {
    agreementId,
    rentalId,
    requesterId,
    reason: 'SCHEDULE_CHANGE',
    reasonText: null,
    status: 'REQUESTED',
    expiresAt: minutesFromNow(47 * 60),
    ...extra,
  };
}

/** 목록용 지난 대여(위 표 41~65, 71~92) */
function pastRentals() {
  const PAST_ITEMS = [101, 105, 102];
  const renterSide = Array.from({ length: 25 }, (_, i) => {
    const from = -30 - i * 4;
    const period = [from, from + 2];
    if (i % 4 === 3) {
      return rental(41 + i, PAST_ITEMS[i % 3], MOCK_ME, 'CANCELED', period, {
        canceledAt: at(from - 2, '12:00'),
        cancelType: 'RENTER',
        cancelReason: 'SCHEDULE_CHANGE',
      });
    }
    return rental(41 + i, PAST_ITEMS[i % 3], MOCK_ME, 'COMPLETED', period, {
      handedOverAt: at(from, '18:00'),
      returnedAt: at(from + 2, '19:00'),
      completedAt: at(from + 3, '09:00'),
    });
  });
  const ownerSide = Array.from({ length: 22 }, (_, i) => {
    const from = -10 - i * 4;
    return rental(71 + i, 107, i % 2 === 0 ? 3 : 4, 'COMPLETED', [from, from + 1], {
      handedOverAt: at(from, '18:00'),
      returnedAt: at(from + 1, '19:00'),
      completedAt: at(from + 2, '09:00'),
    });
  });
  return [...renterSide, ...ownerSide];
}

/** 새로 만든 시드. 목 저장소는 이 값을 고쳐 가며 쓴다. */
export function createRentalSeed() {
  const RENTER = 3; // 제공자 쪽 시드의 대여자 텐트초보
  const OTHER = 4; // 남의 대여
  const canceledAt = at(-1, '11:20');

  const rentals = [
    // 대여자 쪽
    rental(1, 105, MOCK_ME, 'PENDING_PAYMENT', [5, 7], { paymentExpiresAt: minutesFromNow(12) }),
    rental(2, 105, MOCK_ME, 'RESERVED', [4, 6]),
    rental(3, 101, MOCK_ME, 'RESERVED', [2, 3]),
    rental(4, 105, MOCK_ME, 'RESERVED', [0, 2]),
    rental(5, 105, MOCK_ME, 'RESERVED', [0, 1]),
    rental(6, 105, MOCK_ME, 'RESERVED', [-1, 1]),
    rental(7, 105, MOCK_ME, 'RENTING', [-2, 3], { handedOverAt: at(-2, '18:00') }),
    rental(8, 106, MOCK_ME, 'OVERDUE', [-7, -2], { handedOverAt: at(-7, '18:00') }),
    rental(9, 105, MOCK_ME, 'RETURNED', [-5, -1], {
      handedOverAt: at(-5, '18:00'),
      returnedAt: at(-1, '19:00'),
    }),
    rental(10, 101, MOCK_ME, 'COMPLETED', [-20, -18], {
      handedOverAt: at(-20, '18:00'),
      returnedAt: at(-18, '19:00'),
      completedAt: at(-17, '09:00'),
    }),
    rental(11, 105, MOCK_ME, 'CANCELED', [1, 2], {
      canceledAt,
      cancelType: 'RENTER',
      cancelReason: 'SCHEDULE_CHANGE',
      cancelFee: 2400,
    }),
    // 제공자 쪽
    rental(21, 104, RENTER, 'PENDING_PAYMENT', [6, 8], { paymentExpiresAt: minutesFromNow(9) }),
    rental(22, 104, RENTER, 'RESERVED', [3, 4]),
    rental(23, 103, RENTER, 'RESERVED', [0, 2]),
    rental(24, 104, RENTER, 'RESERVED', [0, 1]),
    rental(25, 103, RENTER, 'RESERVED', [-1, 2]),
    rental(26, 104, RENTER, 'RENTING', [-3, 2], { handedOverAt: at(-3, '18:00') }),
    rental(27, 103, RENTER, 'OVERDUE', [-6, -1], { handedOverAt: at(-6, '18:00') }),
    rental(28, 104, RENTER, 'RETURNED', [-6, -2], {
      handedOverAt: at(-6, '18:00'),
      returnedAt: at(-2, '19:00'),
    }),
    rental(29, 103, RENTER, 'RETURNED', [-8, -4], {
      handedOverAt: at(-8, '18:00'),
      returnedAt: at(-1, '19:00'),
    }),
    rental(30, 104, RENTER, 'COMPLETED', [-30, -28], {
      handedOverAt: at(-30, '18:00'),
      returnedAt: at(-28, '19:00'),
      completedAt: at(-27, '10:00'),
    }),
    rental(31, 103, RENTER, 'CANCELED', [2, 3], {
      canceledAt,
      cancelType: 'MUTUAL',
      cancelReason: 'ITEM_PROBLEM',
      cancelFee: 0,
    }),
    rental(32, 107, RENTER, 'RESERVED', [5, 6]),
    ...pastRentals(),
    // 남의 대여
    rental(900, 101, OTHER, 'RESERVED', [8, 10]),
    rental(901, 101, OTHER, 'PENDING_PAYMENT', [12, 13], { paymentExpiresAt: minutesFromNow(-5) }),
    rental(902, 101, OTHER, 'PENDING_PAYMENT', [14, 14], { paymentExpiresAt: minutesFromNow(10) }),
  ];

  const agreements = [
    agreement(1, 5, MOCK_ME),
    agreement(2, 6, 2, { reason: 'ITEM_PROBLEM' }),
    agreement(3, 24, MOCK_ME, { reason: 'COUNTERPART_REQUEST' }),
    agreement(4, 25, RENTER, { reason: 'OTHER', reasonText: '갑자기 출장이 잡혔어요' }),
  ];

  const inspections = [
    handover(7, -2),
    handover(8, -7, null),
    handover(9, -5),
    returnRecord(9, -1),
    handover(10, -20),
    returnRecord(10, -18, { result: 'NORMAL' }),
    handover(26, -3),
    handover(27, -6),
    handover(28, -6),
    returnRecord(28, -2, {
      checklist: { ...ALL_YES, NO_CONTAMINATION: false },
      note: '옆면에 얼룩이 생겼어요',
    }),
    handover(29, -8),
    returnRecord(29, -1, { writerRole: 'OWNER', note: '대여자가 문 앞에 두고 가서 대신 접수해요' }),
    handover(30, -30),
    returnRecord(30, -28, {
      checklist: { ...ALL_YES, NO_EXTERIOR_DAMAGE: false },
      note: '렌즈 덮개에 금이 갔어요',
      result: 'ABNORMAL',
      resultNote: '렌즈 덮개 파손',
      resultImageUrls: photos('판정', 1),
    }),
  ].map((inspection, index) => ({ inspectionId: index + 1, ...inspection }));

  return { rentals, agreements, inspections };
}
