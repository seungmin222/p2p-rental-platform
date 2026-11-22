// 문의·분쟁 API 목(REQ-063). 요청·응답은 docs/openapi.json 그대로, 계산은 inquiryLogic.js.
// 저장은 대여 목과 같은 db(rentalStore.js)의 inquiries — 분쟁을 접수하면 정산 목(paymentHistory.js)이 그 대여의
// 지급 예정 정산을 보류(HELD)로 보고 heldTotal에 더한다. 처리 상태는 관리자(REQ-057)가 바꾸므로 목에서는 바뀌지 않는다.
//
// | 시드 | 회원 | 종류 | 대여 | 상태 | 확인할 것 |
// | 1 | 1 | 일반 | 없음 | RESOLVED | 처리 완료 배지 |
// | 2 | 1 | 분쟁 | 10 | IN_PROGRESS | 대여 10에 다시 분쟁 → 409 INQUIRY_DISPUTE_ALREADY_OPEN |
// | 3 | 1 | 일반 | 14 | RECEIVED | 대여 링크 |
// | 4 | 3 | 분쟁 | 33 | IN_PROGRESS | user1(제공자)의 33 정산 보류 이유(상대방 분쟁, user1 목록에는 없음) |
// user1로 대여 34(취소 수수료 정산, 지급 예정)에 분쟁을 접수하면 정산 탭의 34가 보류로 바뀐다. 대여 1(결제 대기)은 분쟁 불가.
import { http } from 'msw';

import {
  buildInquiry,
  checkDispute,
  checkInquiryBody,
  listMyInquiries,
  toInquiryResponse,
} from './inquiryLogic';
import { readPage, toPageResponse } from './pageList';
import { currentMemberId, findParticipantRental, handle, invalidInput, reject } from './rental';
import { db, nowServer } from './rentalStore';
import { ok } from './response';

export const inquiryHandlers = [
  http.post(
    '/api/inquiries',
    handle(async ({ request }) => {
      const body = await request.json().catch(() => null);
      const details = checkInquiryBody(body);
      if (details) {
        reject('COMMON_INVALID_INPUT', details);
      }
      const memberId = currentMemberId();
      if (body.rentalId != null) {
        const rental = findParticipantRental(body.rentalId);
        if (body.type === 'DISPUTE') {
          const code = checkDispute(rental, db.inquiries, memberId);
          if (code) {
            reject(code);
          }
        }
      }
      const inquiry = buildInquiry(body, {
        inquiryId: Math.max(0, ...db.inquiries.map((i) => i.inquiryId)) + 1,
        memberId,
        createdAt: nowServer(),
      });
      db.inquiries.push(inquiry);
      return ok(toInquiryResponse(inquiry), { status: 201 });
    }),
  ),

  http.get(
    '/api/inquiries',
    handle(({ request }) => {
      const page = readPage(new URL(request.url).searchParams.get('page'));
      if (page == null) {
        invalidInput('page', '형식이 올바르지 않습니다.');
      }
      return ok(toPageResponse(listMyInquiries(db.inquiries, currentMemberId()), page));
    }),
  ),
];
