import { describe, expect, it } from 'vitest';

import { getRentalActions, RENTAL_ACTION as A } from './rentalActions';

const TODAY = '2026-10-29';

function rental(overrides = {}) {
  return {
    rentalId: 1,
    status: 'RESERVED',
    myRole: 'RENTER',
    startDate: '2026-11-03',
    endDate: '2026-11-05',
    rentalFee: 30000,
    paymentExpiresAt: null,
    activeCancelAgreement: null,
    ...overrides,
  };
}

const types = (result) => result.actions.map((a) => a.type);
const disabled = (result) =>
  Object.fromEntries(result.actions.map((a) => [a.type, a.disabledReason]));

const ROLES = ['RENTER', 'OWNER'];
// 시작일 전날 / 당일
const START_CASES = [
  ['전날', '2026-10-30'],
  ['당일', TODAY],
];

describe('getRentalActions', () => {
  describe('PENDING_PAYMENT', () => {
    const pending = (myRole) =>
      rental({ status: 'PENDING_PAYMENT', myRole, paymentExpiresAt: '2026-10-29T10:15:00' });

    it.each(ROLES)('%s: 결제 꺼짐이면 예약 취소만, 남은 결제 시간 안내 없음', (myRole) => {
      const result = getRentalActions(pending(myRole), undefined, TODAY, {
        isPaymentEnabled: false,
      });
      expect(types(result)).toEqual([A.CANCEL]);
      expect(result.notice).toBeNull();
    });

    it.each([
      ['RENTER', [A.CANCEL, A.PAY]],
      ['OWNER', [A.CANCEL]],
    ])('%s: 결제 켜짐이면 결제하기는 대여자만, 남은 결제 시간 안내는 둘 다', (myRole, expected) => {
      const result = getRentalActions(pending(myRole), undefined, TODAY, {
        isPaymentEnabled: true,
      });
      expect(types(result)).toEqual(expected);
      expect(result.notice).toEqual({ tone: 'default', paymentExpiresAt: '2026-10-29T10:15:00' });
    });
  });

  describe('RESERVED, 오늘 < 시작일', () => {
    it('대여자: 예약 취소만', () => {
      const result = getRentalActions(rental({ startDate: '2026-10-30' }), undefined, TODAY);
      expect(types(result)).toEqual([A.CANCEL]);
      expect(result.notice).toBeNull();
    });

    it('제공자: 예약 취소 + 인계 확인(비활성, 시작일 안내)', () => {
      const result = getRentalActions(
        rental({ myRole: 'OWNER', startDate: '2026-10-30' }),
        undefined,
        TODAY,
      );
      expect(types(result)).toEqual([A.CANCEL, A.HANDOVER]);
      expect(disabled(result)).toEqual({
        [A.CANCEL]: null,
        [A.HANDOVER]: '10월 30일부터 인계할 수 있어요',
      });
    });
  });

  describe('RESERVED, 오늘 ≥ 시작일', () => {
    const started = (overrides) => rental({ startDate: TODAY, ...overrides });

    it.each([
      ['RENTER', [A.REQUEST_CANCEL_AGREEMENT]],
      ['OWNER', [A.REQUEST_CANCEL_AGREEMENT, A.HANDOVER]],
    ])('%s: 동의 요청 없음 → 취소 요청', (myRole, expected) => {
      const result = getRentalActions(started({ myRole }), undefined, TODAY);
      expect(types(result)).toEqual(expected);
      expect(result.actions.every((a) => a.disabledReason === null)).toBe(true);
    });

    it('시작일이 지난 뒤에도 같다(인계 확인 활성)', () => {
      const result = getRentalActions(
        rental({ myRole: 'OWNER', startDate: '2026-10-27' }),
        undefined,
        TODAY,
      );
      expect(types(result)).toEqual([A.REQUEST_CANCEL_AGREEMENT, A.HANDOVER]);
      expect(disabled(result)[A.HANDOVER]).toBeNull();
    });

    it.each([
      ['RENTER', [A.CANCEL_AGREEMENT_PENDING]],
      ['OWNER', [A.CANCEL_AGREEMENT_PENDING, A.HANDOVER]],
    ])('%s: 내가 요청함 → 취소 요청 중(비활성, 만료 시각)', (myRole, expected) => {
      const agreement = {
        agreementId: 7,
        reason: 'SCHEDULE_CHANGE',
        reasonText: null,
        expiresAt: '2026-10-31T09:05:00',
        requestedByMe: true,
      };
      const result = getRentalActions(
        started({ myRole, activeCancelAgreement: agreement }),
        undefined,
        TODAY,
      );
      expect(types(result)).toEqual(expected);
      expect(disabled(result)[A.CANCEL_AGREEMENT_PENDING]).toBe(
        '10월 31일 09:05까지 상대의 응답을 기다려요',
      );
    });

    it.each([
      ['RENTER', [A.REJECT_CANCEL_AGREEMENT, A.ACCEPT_CANCEL_AGREEMENT]],
      ['OWNER', [A.REJECT_CANCEL_AGREEMENT, A.ACCEPT_CANCEL_AGREEMENT, A.HANDOVER]],
    ])('%s: 상대가 요청함 → 거절·동의', (myRole, expected) => {
      const agreement = {
        agreementId: 7,
        reason: 'OTHER',
        reasonText: '사정이 생겼어요',
        expiresAt: '2026-10-31T09:05:00',
        requestedByMe: false,
      };
      const result = getRentalActions(
        started({ myRole, activeCancelAgreement: agreement }),
        undefined,
        TODAY,
      );
      expect(types(result)).toEqual(expected);
    });
  });

  describe.each(START_CASES)('시작일 %s의 RESERVED', (_, startDate) => {
    it.each(ROLES)('%s: 단독 취소와 동의 요청 중 하나만 보인다', (myRole) => {
      const result = getRentalActions(rental({ myRole, startDate }), undefined, TODAY);
      const hasCancel = types(result).includes(A.CANCEL);
      const hasRequest = types(result).includes(A.REQUEST_CANCEL_AGREEMENT);
      expect(hasCancel).toBe(startDate !== TODAY);
      expect(hasRequest).toBe(startDate === TODAY);
    });
  });

  describe('RENTING·OVERDUE', () => {
    it.each([
      ['RENTER', '반납 요청'],
      ['OWNER', '반납 대리 접수'],
    ])('%s: 같은 반납 요청 액션, 라벨만 다르다', (myRole, label) => {
      const result = getRentalActions(
        rental({ status: 'RENTING', myRole, startDate: '2026-10-27', endDate: '2026-10-31' }),
        undefined,
        TODAY,
      );
      expect(result.actions).toEqual([
        { type: A.RETURN_REQUEST, label, variant: 'primary', disabledReason: null },
      ]);
      expect(result.notice).toBeNull();
    });

    it.each(ROLES)('%s: OVERDUE면 지난 일수 안내(연체료 없음)', (myRole) => {
      const result = getRentalActions(
        rental({ status: 'OVERDUE', myRole, startDate: '2026-10-20', endDate: '2026-10-26' }),
        undefined,
        TODAY,
      );
      expect(types(result)).toEqual([A.RETURN_REQUEST]);
      expect(result.notice).toEqual({ tone: 'danger', text: '반납 예정일이 3일 지났어요' });
    });
  });

  describe('RETURNED', () => {
    const returned = (myRole) =>
      rental({ status: 'RETURNED', myRole, startDate: '2026-10-20', endDate: '2026-10-26' });
    const pendingRecord = { phase: 'RETURN', result: null };
    const judgedRecord = { phase: 'RETURN', result: 'NORMAL' };

    it.each(ROLES)('%s: inspections를 못 불러왔으면 아무것도 그리지 않는다', (myRole) => {
      expect(getRentalActions(returned(myRole), undefined, TODAY)).toEqual({
        actions: [],
        notice: null,
      });
    });

    it('제공자, 판정 전: 이상 있음(danger)·정상 확인(primary)', () => {
      const result = getRentalActions(returned('OWNER'), pendingRecord, TODAY);
      expect(result.actions.map((a) => [a.type, a.variant])).toEqual([
        [A.CONFIRM_ABNORMAL, 'danger'],
        [A.CONFIRM_NORMAL, 'primary'],
      ]);
    });

    it('대여자, 판정 전: 버튼 없이 자동 확인 안내', () => {
      const result = getRentalActions(returned('RENTER'), pendingRecord, TODAY);
      expect(result.actions).toEqual([]);
      expect(result.notice.text).toBe(
        '제공자가 상태를 확인하고 있어요. 반납 후 3일이 지나면 자동으로 정상 처리돼요',
      );
    });

    it.each(ROLES)('%s, 판정 후: 아무것도 그리지 않는다', (myRole) => {
      expect(getRentalActions(returned(myRole), judgedRecord, TODAY)).toEqual({
        actions: [],
        notice: null,
      });
    });
  });

  describe.each(['COMPLETED', 'CANCELED'])('%s', (status) => {
    it.each(ROLES)('%s: 버튼 없음', (myRole) => {
      expect(getRentalActions(rental({ status, myRole }), null, TODAY)).toEqual({
        actions: [],
        notice: null,
      });
    });
  });
});
