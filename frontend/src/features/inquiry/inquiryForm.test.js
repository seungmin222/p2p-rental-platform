import { describe, expect, it } from 'vitest';

import { INQUIRY_CONTENT_MAX } from './constants';
import {
  getDisputeBlockReason,
  getInquiryWritePath,
  INQUIRY_CONTENT_REQUIRED,
  INQUIRY_CONTENT_TOO_LONG,
  readInquiryRentalId,
  toInquiryRequest,
  validateInquiryContent,
} from './inquiryForm';

describe('validateInquiryContent', () => {
  it('비었거나 공백만이면 오류', () => {
    expect(validateInquiryContent('')).toBe(INQUIRY_CONTENT_REQUIRED);
    expect(validateInquiryContent('  \n\t ')).toBe(INQUIRY_CONTENT_REQUIRED);
    expect(validateInquiryContent(undefined)).toBe(INQUIRY_CONTENT_REQUIRED);
  });

  it('최대 글자 수는 코드 포인트로 센다', () => {
    expect(validateInquiryContent('가'.repeat(INQUIRY_CONTENT_MAX))).toBe(true);
    expect(validateInquiryContent('가'.repeat(INQUIRY_CONTENT_MAX + 1))).toBe(
      INQUIRY_CONTENT_TOO_LONG,
    );
    // 이모지 하나는 UTF-16 두 칸이지만 한 글자
    expect(validateInquiryContent('😀'.repeat(INQUIRY_CONTENT_MAX))).toBe(true);
  });
});

describe('readInquiryRentalId', () => {
  const read = (query) => readInquiryRentalId(new URLSearchParams(query));

  it('대여 id를 읽고 종류(type)는 무시한다', () => {
    expect(read('rentalId=10')).toBe(10);
    expect(read('rentalId=10&type=DISPUTE')).toBe(10);
    expect(read('type=DISPUTE')).toBeNull();
  });

  it('틀린 값은 없는 것으로', () => {
    expect(read('rentalId=0')).toBeNull();
    expect(read('rentalId=-1')).toBeNull();
    expect(read('rentalId=1.5')).toBeNull();
    expect(read('rentalId=99999999999999999999')).toBeNull();
  });
});

describe('getInquiryWritePath', () => {
  it('대여만 주소에 넣는다', () => {
    expect(getInquiryWritePath()).toBe('/me/inquiries/new');
    expect(getInquiryWritePath({ rentalId: 10 })).toBe('/me/inquiries/new?rentalId=10');
  });
});

describe('getDisputeBlockReason', () => {
  it('대여가 없거나 결제 대기면 분쟁 불가', () => {
    expect(getDisputeBlockReason(null, undefined)).toBe('NO_RENTAL');
    expect(getDisputeBlockReason(10, { status: 'PENDING_PAYMENT' })).toBe('PENDING_PAYMENT');
  });

  it('그 밖의 상태는 허용(취소도 수수료 정산이 있을 수 있다). 상세를 못 받았으면 서버에 맡긴다', () => {
    for (const status of ['RESERVED', 'RENTING', 'OVERDUE', 'RETURNED', 'COMPLETED', 'CANCELED']) {
      expect(getDisputeBlockReason(10, { status })).toBeNull();
    }
    expect(getDisputeBlockReason(10, undefined)).toBeNull();
  });
});

describe('toInquiryRequest', () => {
  it('대여가 있으면 rentalId를 넣고 내용은 원문 그대로', () => {
    expect(toInquiryRequest({ type: 'DISPUTE', content: ' 이의 있어요 ' }, 10)).toEqual({
      type: 'DISPUTE',
      rentalId: 10,
      content: ' 이의 있어요 ',
    });
    expect(toInquiryRequest({ type: 'GENERAL', content: '문의' }, null)).toEqual({
      type: 'GENERAL',
      content: '문의',
    });
  });
});
