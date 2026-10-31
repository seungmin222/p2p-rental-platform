// 결제 목의 순수 함수. 목 주문이 실제 토스 결제창에 그대로 들어가므로 토스 문서의 형식을 지킨다
// (docs.tosspayments.com/sdk/v2/js/payment): orderId 6~64자, customerKey 22~50자, orderName 100자 이하.
export const TOSS_ORDER_ID_PATTERN = /^[A-Za-z0-9_=-]{6,64}$/;
export const TOSS_CUSTOMER_KEY_PATTERN = /^[A-Za-z0-9_=.@-]{22,50}$/;
export const TOSS_ORDER_NAME_MAX = 100;

/** 회원마다 같은 UUID 모양 값(백엔드도 회원 ID로 만든 UUID를 저장하지 않고 쓴다) */
export function toMockCustomerKey(memberId) {
  return `00000000-0000-4000-8000-${String(memberId).padStart(12, '0')}`;
}

/** 대여 → 주문 행. amount는 서버처럼 대여 시점 금액으로 계산한다(대여료 + 보증금) */
export function createMockOrder(rental, rentalDays, itemName, orderId) {
  return {
    orderId,
    rentalId: rental.rentalId,
    amount: rental.pricePerDay * rentalDays + rental.deposit,
    orderName: itemName.slice(0, TOSS_ORDER_NAME_MAX),
    status: 'READY',
    depositStatus: 'NONE',
  };
}

/** PaymentOrderResponse */
export function toOrderResponse(order, memberId) {
  return {
    orderId: order.orderId,
    amount: order.amount,
    orderName: order.orderName,
    customerKey: toMockCustomerKey(memberId),
  };
}

/** PaymentConfirmResponse */
export function toConfirmResponse(order) {
  return {
    orderId: order.orderId,
    rentalId: order.rentalId,
    amount: order.amount,
    status: order.status,
    depositStatus: order.depositStatus,
  };
}

/**
 * 동결 보증금이 있는가. 승인된 주문이 있으면 그 상태로, 시드 대여처럼 주문이 없으면 보증금이 있는 건을
 * 결제된 것으로 본다. 결제가 꺼져 있으면 받은 돈이 없다.
 */
export function hasFrozenDeposit(rental, orders, isPaymentEnabled) {
  if (!isPaymentEnabled || rental.deposit <= 0) {
    return false;
  }
  const approved = orders.find(
    (order) => order.rentalId === rental.rentalId && order.status === 'APPROVED',
  );
  return approved ? approved.depositStatus === 'FROZEN' : true;
}
