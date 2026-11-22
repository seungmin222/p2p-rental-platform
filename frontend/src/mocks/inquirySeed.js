// 문의·분쟁 목(REQ-063) 시드. 표는 mocks/inquiry.js 맨 위. 저장은 대여 목 db(rentalStore.js)의 inquiries.
import { at } from './rentalSeed';

/** 새로 만든 시드(memberId는 저장용, 응답에는 넣지 않는다) */
export function createInquirySeed() {
  return [
    {
      inquiryId: 1,
      memberId: 1,
      rentalId: null,
      type: 'GENERAL',
      status: 'RESOLVED',
      content: '물품 등록할 때 사진 순서를 바꾸는 방법이 궁금해요.',
      createdAt: at(-10, '09:30'),
    },
    {
      inquiryId: 2,
      memberId: 1,
      rentalId: 10,
      type: 'DISPUTE',
      status: 'IN_PROGRESS',
      content:
        '반납할 때 이미 있던 흠집인데 상태 확인에서 이상으로 나왔어요.\n인계 사진에도 같은 자리에 흠집이 보입니다.',
      createdAt: at(-5, '14:10'),
    },
    {
      inquiryId: 3,
      memberId: 1,
      rentalId: 14,
      type: 'GENERAL',
      status: 'RECEIVED',
      content: '거래 완료 후 영수증을 받을 수 있나요?',
      createdAt: at(-1, '20:05'),
    },
    {
      inquiryId: 4,
      memberId: 3,
      rentalId: 33,
      type: 'DISPUTE',
      status: 'IN_PROGRESS',
      content: '대여료 계산에 이의가 있어요.',
      createdAt: at(-2, '11:00'),
    },
  ];
}
