// 결제·보증금 내역(REQ-033)·정산 내역(REQ-039) 목. 요청·응답은 docs/openapi.json 그대로이고, 계산은 paymentHistoryLogic.js.
// 대여·결제 목과 같은 db(rentals·payments)에서 매번 만들어 금액이 대여 상세·빌린 내역과 어긋나지 않는다.
import { http } from 'msw';

import { todayInSeoul } from '../shared/lib/date';
import { findMockItem } from './item';
import { readPage, toPageResponse } from './pageList';
import {
  buildPaymentHistory,
  buildSettlements,
  filterPaymentHistory,
  readHistoryQuery,
  sumSettlements,
} from './paymentHistoryLogic';
import { currentMemberId, handle, invalidInput } from './rental';
import { ok } from './response';
import { db } from './rentalStore';

function requirePage(searchParams) {
  const page = readPage(searchParams.get('page'));
  if (page == null) {
    invalidInput('page', '형식이 올바르지 않습니다.');
  }
  return page;
}

export const paymentHistoryHandlers = [
  http.get(
    '/api/members/me/payments',
    handle(({ request }) => {
      const { searchParams } = new URL(request.url);
      const query = readHistoryQuery(searchParams, todayInSeoul());
      if (query.error) {
        invalidInput(query.error.field, query.error.reason);
      }
      const page = requirePage(searchParams);
      const entries = buildPaymentHistory(db.rentals, db.payments, currentMemberId(), findMockItem);
      return ok(toPageResponse(filterPaymentHistory(entries, query), page));
    }),
  ),

  http.get(
    '/api/members/me/settlements',
    handle(({ request }) => {
      const page = requirePage(new URL(request.url).searchParams);
      const settlements = buildSettlements(
        db.rentals,
        db.payments,
        currentMemberId(),
        findMockItem,
        todayInSeoul(),
      );
      return ok({ ...sumSettlements(settlements), settlements: toPageResponse(settlements, page) });
    }),
  ),
];
