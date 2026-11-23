// 도메인별 핸들러는 각 화면 브랜치에서 추가한다. 처리하지 않는 요청은 실제 서버로 넘어간다(bypass).
import { authHandlers } from './auth';
import { chatHandlers } from './chat';
import { chatStompHandlers } from './chatStomp';
import { feedHandlers } from './feed';
import { inquiryHandlers } from './inquiry';
import { itemHandlers } from './item';
import { itemWriteHandlers } from './itemWrite';
import { memberHandlers } from './member';
import { notificationHandlers } from './notification';
import { regionHandlers } from './region';
import { paymentHandlers } from './payment';
import { paymentHistoryHandlers } from './paymentHistory';
import { profileHandlers } from './profile';
import { rentalHandlers } from './rental';
import { reviewHandlers } from './review';
import { termsHandlers } from './terms';
import { wishHandlers } from './wish';

// 캘린더(/api/items/:itemId/calendar)는 물품 상세(/api/items/:itemId)보다 구체적이라 순서와 무관하게 맞는다.
// 피드(/api/items/feed)와 권장 보증금(/api/items/deposit-guide)은 물품 상세와 경로가 겹치므로(itemId로 읽힌다) 반드시 상세보다 앞에 둔다.
export const handlers = [
  ...authHandlers,
  ...memberHandlers,
  ...termsHandlers,
  ...regionHandlers,
  ...rentalHandlers,
  ...reviewHandlers,
  ...paymentHandlers,
  ...paymentHistoryHandlers,
  ...inquiryHandlers,
  ...chatHandlers,
  ...chatStompHandlers,
  ...notificationHandlers,
  ...feedHandlers,
  ...itemWriteHandlers,
  // 관심·최근 본(REQ-022·035). /api/members/me/wishes·recent-items도 프로필(/api/members/:memberId/...)보다 앞
  ...wishHandlers,
  ...itemHandlers,
  // /api/members/:memberId(프로필)는 /api/members/me·/api/members/me/items와 경로가 겹쳐 맨 끝
  ...profileHandlers,
];
