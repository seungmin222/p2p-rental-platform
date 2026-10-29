// 도메인별 핸들러는 각 화면 브랜치에서 추가한다. 처리하지 않는 요청은 실제 서버로 넘어간다(bypass).
import { authHandlers } from './auth';
import { itemHandlers } from './item';
import { rentalHandlers } from './rental';

// 캘린더(/api/items/:itemId/calendar)가 물품 상세(/api/items/:itemId)보다 구체적이라 순서와 무관하게 맞는다
export const handlers = [...authHandlers, ...rentalHandlers, ...itemHandlers];
