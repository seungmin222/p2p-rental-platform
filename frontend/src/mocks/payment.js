// 결제 API 목. 요청·응답·오류 코드는 docs/openapi.json 그대로이고, 승인과 환불은 토스를 부르지 않는 목 응답이다.
// 결제창만 실제 토스(테스트 키)이므로 주문 값은 paymentLogic.js에서 토스 형식을 지킨다.
import { http } from 'msw';

import { countDaysInclusive } from '../shared/lib/date';
import { findMockItem } from './item';
import {
  createMockExtensionOrder,
  createMockOrder,
  toConfirmResponse,
  toOrderResponse,
} from './paymentLogic';
import { CONFIRM_FAILURES, takeFailure } from './paymentScenario';
import { cancel, confirmExtension, currentMemberId, handle, invalidInput, reject } from './rental';
import { ok } from './response';
import { db, isPendingExtension, nowServer } from './rentalStore';

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

/** 연장료 주문(REQ-038). 연장 행으로 유효성을 본다: 다른 대여의 연장 404, 결제 대기 아님 409, 만료 409 */
function createExtensionOrder(rental, extensionId) {
  const extension = db.extensions.find(
    (candidate) =>
      candidate.extensionId === Number(extensionId) && candidate.rentalId === rental.rentalId,
  );
  if (!extension) {
    reject('RENTAL_EXTENSION_NOT_FOUND');
  }
  if (extension.status !== 'PENDING_PAYMENT') {
    reject('RENTAL_INVALID_TRANSITION');
  }
  if (!isPendingExtension(extension)) {
    reject('PAYMENT_EXPIRED');
  }
  // 진행 중인 주문이 연장에 묶여 있으면 그대로, 실패 뒤에는 새 주문
  let order = db.payments.find(
    (o) => o.extensionId === extension.extensionId && o.status === 'READY',
  );
  if (!order) {
    order = createMockExtensionOrder(
      extension,
      findMockItem(rental.itemId).name,
      crypto.randomUUID(),
    );
    db.payments.push(order);
  }
  return order;
}

/**
 * 연장료 승인(REQ-038). 연장이 만료됐거나 대여가 RENTING이 아니거나 종료일이 요청 때와 다르면 확정하지 않고
 * 연장 CANCELED + 환불(409 PAYMENT_CANCELED_AFTER_EXPIRY, open-issues #45 A). 금액 불일치는 결제 FAILED + 연장 CANCELED.
 */
function confirmExtensionOrder(order, rental, amount) {
  const extension = db.extensions.find((e) => e.extensionId === order.extensionId);
  const failure = takeFailure(CONFIRM_FAILURES);
  if (failure === 'PAYMENT_APPROVAL_FAILED') {
    failOrder(order);
    reject(failure);
  }
  if (
    failure === 'PAYMENT_CANCELED_AFTER_EXPIRY' ||
    !isPendingExtension(extension) ||
    rental.status !== 'RENTING' ||
    rental.endDate !== extension.prevEndDate
  ) {
    order.status = 'CANCELED';
    if (extension.status === 'PENDING_PAYMENT') {
      extension.status = 'CANCELED';
    }
    reject('PAYMENT_CANCELED_AFTER_EXPIRY');
  }
  if (failure) {
    reject(failure);
  }
  if (order.amount !== amount) {
    failOrder(order);
    extension.status = 'CANCELED';
    reject('PAYMENT_AMOUNT_MISMATCH');
  }
  order.status = 'APPROVED';
  confirmExtension(rental, extension);
  return ok(toConfirmResponse(order));
}

export const paymentHandlers = [
  http.post(
    '/api/payments',
    handle(async ({ request }) => {
      const { rentalId, extensionId } = (await request.json()) ?? {};
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
      if (extensionId != null) {
        return ok(toOrderResponse(createExtensionOrder(rental, extensionId), currentMemberId()), {
          status: 201,
        });
      }
      if (rental.status !== 'PENDING_PAYMENT') {
        reject('RENTAL_INVALID_TRANSITION');
      }
      if (isExpired(rental)) {
        reject('PAYMENT_EXPIRED');
      }
      // 진행 중인 주문이 있으면 같은 주문, 승인에 실패한 뒤에는 새 주문
      let order = db.payments.find(
        (o) => o.rentalId === rental.rentalId && o.status === 'READY' && o.type !== 'EXTENSION',
      );
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
      if (order.type === 'EXTENSION') {
        return confirmExtensionOrder(order, rental, amount);
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
