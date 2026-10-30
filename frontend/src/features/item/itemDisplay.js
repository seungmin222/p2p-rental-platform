// 피드·검색 카드의 표시 규칙. 일 대여료 0원은 무료 대여다(policies.md item.price-per-day: 0원 또는 1,000원 이상 — 0원 등록 허용).
import { formatDailyPrice, formatElapsed, formatPrice } from '../../shared/lib/format';

/**
 * 카드의 가격 줄. 시안처럼 금액은 굵게, 단위("/일")는 옅게 따로 그리려고 둘로 나눈다.
 * 0원이면 "무료"(단위 없음).
 */
export function getPriceParts(pricePerDay) {
  if (pricePerDay === 0) {
    return { amount: '무료', unit: '' };
  }
  const text = formatDailyPrice(pricePerDay);
  // 금액이 숫자가 아니면 빈 문자열(화면에 "NaN원"이 보이지 않게)
  return text === '' ? { amount: '', unit: '' } : { amount: formatPrice(pricePerDay), unit: '/일' };
}

/** 카드의 보증금 줄: 0원이면 "보증금 없음"(formatDeposit과 같은 표현)이라 보이지 않게 null을 돌려준다 */
export function getDepositText(deposit) {
  return typeof deposit === 'number' && deposit > 0 ? `보증금 ${formatPrice(deposit)}` : null;
}

/** "송도1동 · 3분 전". 시각을 해석할 수 없으면 동네만 */
export function getCardMeta(regionName, bumpedAt, now = new Date()) {
  const elapsed = formatElapsed(bumpedAt, now);
  return [regionName, elapsed].filter(Boolean).join(' · ');
}
