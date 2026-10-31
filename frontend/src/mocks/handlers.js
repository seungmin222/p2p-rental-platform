// 도메인별 핸들러는 각 화면 브랜치에서 추가한다. 처리하지 않는 요청은 실제 서버로 넘어간다(bypass).
import { authHandlers } from './auth';
import { feedHandlers } from './feed';
import { itemHandlers } from './item';
import { itemWriteHandlers } from './itemWrite';
import { memberHandlers } from './member';
import { regionHandlers } from './region';
import { paymentHandlers } from './payment';
import { rentalHandlers } from './rental';

// 캘린더(/api/items/:itemId/calendar)는 물품 상세(/api/items/:itemId)보다 구체적이라 순서와 무관하게 맞는다.
// 피드(/api/items/feed)와 권장 보증금(/api/items/deposit-guide)은 물품 상세와 경로가 겹치므로(itemId로 읽힌다) 반드시 상세보다 앞에 둔다.
export const handlers = [
  ...authHandlers,
  ...memberHandlers,
  ...regionHandlers,
  ...rentalHandlers,
  ...paymentHandlers,
  ...feedHandlers,
  ...itemWriteHandlers,
  ...itemHandlers,
];
