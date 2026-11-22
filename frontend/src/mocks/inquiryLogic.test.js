import { describe, expect, it } from 'vitest';

import {
  buildInquiry,
  checkDispute,
  checkInquiryBody,
  INQUIRY_CONTENT_MAX,
  listMyInquiries,
  openDisputeRentalIds,
} from './inquiryLogic';

function inquiry(overrides) {
  return {
    inquiryId: 1,
    memberId: 1,
    rentalId: 10,
    type: 'DISPUTE',
    status: 'RECEIVED',
    content: '이의',
    createdAt: '2026-11-20T10:00:00',
    ...overrides,
  };
}

describe('checkInquiryBody', () => {
  const field = (body) => checkInquiryBody(body)?.[0].field ?? null;

  it('종류·내용(공백만·길이)·rentalId 형식을 검사한다', () => {
    expect(field(null)).toBe('body');
    expect(field({ type: 'OTHER', content: '문의' })).toBe('type');
    expect(field({ type: 'GENERAL', content: '   ' })).toBe('content');
    expect(field({ type: 'GENERAL', content: '가'.repeat(INQUIRY_CONTENT_MAX + 1) })).toBe(
      'content',
    );
    expect(field({ type: 'GENERAL', content: '문의', rentalId: 0 })).toBe('rentalId');
  });

  it('분쟁은 rentalId 필수, 일반 문의는 선택', () => {
    expect(field({ type: 'DISPUTE', content: '이의' })).toBe('rentalId');
    expect(field({ type: 'GENERAL', content: '문의' })).toBeNull();
    expect(
      field({ type: 'GENERAL', content: '😀'.repeat(INQUIRY_CONTENT_MAX), rentalId: 3 }),
    ).toBeNull();
  });
});

describe('checkDispute', () => {
  it('결제 대기 대여는 409 NOT_ALLOWED', () => {
    expect(checkDispute({ rentalId: 1, status: 'PENDING_PAYMENT' }, [], 1)).toBe(
      'INQUIRY_DISPUTE_NOT_ALLOWED',
    );
  });

  it('같은 회원·대여에 RESOLVED가 아닌 분쟁이 있으면 409 ALREADY_OPEN, 상대방·처리 완료·일반 문의는 상관없다', () => {
    const rental = { rentalId: 10, status: 'COMPLETED' };
    expect(checkDispute(rental, [inquiry({ status: 'IN_PROGRESS' })], 1)).toBe(
      'INQUIRY_DISPUTE_ALREADY_OPEN',
    );
    expect(checkDispute(rental, [inquiry({ memberId: 3 })], 1)).toBeNull();
    expect(checkDispute(rental, [inquiry({ status: 'RESOLVED' })], 1)).toBeNull();
    expect(checkDispute(rental, [inquiry({ type: 'GENERAL' })], 1)).toBeNull();
    expect(checkDispute({ rentalId: 34, status: 'CANCELED' }, [inquiry()], 1)).toBeNull();
  });
});

describe('listMyInquiries·openDisputeRentalIds', () => {
  const inquiries = [
    inquiry({ inquiryId: 1, createdAt: '2026-11-10T09:00:00', type: 'GENERAL', rentalId: null }),
    inquiry({ inquiryId: 2, createdAt: '2026-11-15T09:00:00' }),
    inquiry({ inquiryId: 3, createdAt: '2026-11-15T09:00:00', status: 'RESOLVED', rentalId: 11 }),
    inquiry({ inquiryId: 4, memberId: 3, rentalId: 33 }),
  ];

  it('내 문의만 최신 접수 순(같은 시각이면 id 큰 것부터), 응답에 memberId는 없다', () => {
    const list = listMyInquiries(inquiries, 1);
    expect(list.map((i) => i.inquiryId)).toEqual([3, 2, 1]);
    expect(list[0]).not.toHaveProperty('memberId');
  });

  it('열린 분쟁의 대여는 접수한 회원과 상관없이 모은다', () => {
    expect([...openDisputeRentalIds(inquiries)].sort()).toEqual([10, 33]);
  });

  it('새 문의는 RECEIVED, 대여가 없으면 null', () => {
    const built = buildInquiry(
      { type: 'GENERAL', content: '문의' },
      { inquiryId: 9, memberId: 1, createdAt: '2026-11-22T10:00:00' },
    );
    expect(built).toMatchObject({ status: 'RECEIVED', rentalId: null, type: 'GENERAL' });
  });
});
