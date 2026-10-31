// 결제 API 목. 요청·응답·오류 코드는 docs/openapi.json 그대로이고, 승인과 환불은 토스를 부르지 않는 목 응답이다.
// 결제창만 실제 토스(테스트 키)이므로 주문 값은 paymentLogic.js에서 토스 형식을 지킨다.
import { http } from 'msw';

import { countDaysInclusive } from '../shared/lib/date';
import { findMockItem } from './item';
import { createMockOrder, toConfirmResponse, toOrderResponse } from './paymentLogic';
import { CONFIRM_FAILURES, takeFailure } from './paymentScenario';
import { cancel, currentMemberId, handle, invalidInput, reject } from './rental';
import { ok } from './response';
import { db, nowServer } from './rentalStore';

function isExpired(rental) {
  return rental.paymentExpiresAt <= nowServer();
}

function failOrder(order) {
  order.status = 'FAILED';
}

function applyScenario(failure, order, rental) {
  if (failure === 'PAYMENT_APPROVAL_FAILED') {
    failOrder(order);
  }
  if (failure === 'PAYMENT_CANCELED_AFTER_EXPIRY') {
    order.status = 'CANCELED';
    cancel(rental, {
      cancelType: 'PAYMENT_EXPIRED',
      reason: null,
      reasonText: null,
      cancelFee: 0,
    });
  }
  reject(failure);
}

export const paymentHandlers = [
  http.post(
    '/api/payments',
    handle(async ({ request }) => {
      const { rentalId } = (await request.json()) ?? {};
      if (rentalId == null) {
        invalidInput('rentalId', '필수 값입니다.');
      }
      const rental = db.rentals.find((candidate) => candidate.rentalId === Number(rentalId));
      if (!rental) {
        reject('RENTAL_NOT_FOUND');
      }
      if (rental.renterId !== currentMemberId()) {
        reject('PAYMENT_RENTER_ONLY');
      }
      if (rental.status !== 'PENDING_PAYMENT') {
        reject('RENTAL_INVALID_TRANSITION');
      }
      if (isExpired(rental)) {
        reject('PAYMENT_EXPIRED');
      }
      // 진행 중인 주문이 있으면 같은 주문, 승인에 실패한 뒤에는 새 주문
      let order = db.payments.find((o) => o.rentalId === rental.rentalId && o.status === 'READY');
      if (!order) {
        order = createMockOrder(
          rental,
          countDaysInclusive(rental.startDate, rental.endDate),
          findMockItem(rental.itemId).name,
          crypto.randomUUID(),
        );
        db.payments.push(order);
      }
      return ok(toOrderResponse(order, currentMemberId()), { status: 201 });
    }),
  ),

  http.post(
    '/api/payments/confirm',
    handle(async ({ request }) => {
      const { paymentKey, orderId, amount } = (await request.json()) ?? {};
      if (!paymentKey) {
        invalidInput('paymentKey', '필수 값입니다.');
      }
      if (!orderId) {
        invalidInput('orderId', '필수 값입니다.');
      }
      if (!Number.isInteger(amount)) {
        invalidInput('amount', '필수 값입니다.');
      }
      const order = db.payments.find((candidate) => candidate.orderId === orderId);
      if (!order) {
        reject('PAYMENT_NOT_FOUND');
      }
      const rental = db.rentals.find((candidate) => candidate.rentalId === order.rentalId);
      if (rental.renterId !== currentMemberId()) {
        reject('PAYMENT_RENTER_ONLY');
      }
      if (order.status === 'FAILED') {
        reject('PAYMENT_INVALID_STATUS');
      }
      // 이미 처리한 주문은 기존 결과(멱등)
      if (order.status !== 'READY') {
        return ok(toConfirmResponse(order));
      }
      const failure = takeFailure(CONFIRM_FAILURES);
      if (failure) {
        applyScenario(failure, order, rental);
      }
      if (rental.status !== 'PENDING_PAYMENT' || isExpired(rental)) {
        reject('PAYMENT_EXPIRED');
      }
      if (order.amount !== amount) {
        failOrder(order);
        cancel(rental, {
          cancelType: 'AMOUNT_MISMATCH',
          reason: null,
          reasonText: null,
          cancelFee: 0,
        });
        reject('PAYMENT_AMOUNT_MISMATCH');
      }
      Object.assign(order, {
        status: 'APPROVED',
        depositStatus: rental.deposit > 0 ? 'FROZEN' : 'NONE',
      });
      rental.status = 'RESERVED';
      return ok(toConfirmResponse(order));
    }),
  ),
];
