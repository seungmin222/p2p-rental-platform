const numberFormat = new Intl.NumberFormat('ko-KR');

const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

// 서버 시각은 오프셋 없는 LocalDateTime(Asia/Seoul)이다(conventions.md §3).
const OFFSET_PATTERN = /(Z|[+-]\d{2}:?\d{2})$/i;
// 날짜만 있는 값('2026-10-07')은 시각이 아니므로 받지 않는다
const DATE_TIME_PATTERN = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/;
const SEOUL_OFFSET = '+09:00';

// 금액은 유한한 숫자만 표시한다. null·NaN·문자열('0') 등은 빈 문자열(화면에 "NaN원"이 보이지 않게).
function isAmount(amount) {
  return typeof amount === 'number' && Number.isFinite(amount);
}

/** 12000 → "12,000원" */
export function formatPrice(amount) {
  return isAmount(amount) ? `${numberFormat.format(amount)}원` : '';
}

/** 12000 → "12,000원/일" */
export function formatDailyPrice(amount) {
  return isAmount(amount) ? `${formatPrice(amount)}/일` : '';
}

/** 50000 → "보증금 50,000원", 0 → "보증금 없음"(DepositStatus NONE 라벨과 같은 표현) */
export function formatDeposit(amount) {
  if (!isAmount(amount)) {
    return '';
  }
  return amount === 0 ? '보증금 없음' : `보증금 ${formatPrice(amount)}`;
}

/** 오프셋이 없는 시각 문자열은 서울 시각으로 해석한다(브라우저 시간대와 무관). 해석할 수 없으면 null. */
export function parseServerDateTime(dateTime) {
  let date = null;
  if (dateTime instanceof Date) {
    date = dateTime;
  } else if (typeof dateTime === 'string' && DATE_TIME_PATTERN.test(dateTime)) {
    date = new Date(OFFSET_PATTERN.test(dateTime) ? dateTime : `${dateTime}${SEOUL_OFFSET}`);
  }
  return date && !Number.isNaN(date.getTime()) ? date : null;
}

/** "방금 전" / "3분 전" / "2시간 전" / "2일 전" / "3주 전" / "5개월 전" / "1년 전". 해석할 수 없으면 "". */
export function formatElapsed(dateTime, now = new Date()) {
  const date = parseServerDateTime(dateTime);
  if (!date) {
    return '';
  }
  const elapsed = now.getTime() - date.getTime();
  // 미래 시각(기기 시계 오차)도 "방금 전"
  if (elapsed < MINUTE_MS) {
    return '방금 전';
  }
  if (elapsed < HOUR_MS) {
    return `${Math.floor(elapsed / MINUTE_MS)}분 전`;
  }
  if (elapsed < DAY_MS) {
    return `${Math.floor(elapsed / HOUR_MS)}시간 전`;
  }
  const days = Math.floor(elapsed / DAY_MS);
  if (days < 7) {
    return `${days}일 전`;
  }
  if (days < 30) {
    return `${Math.floor(days / 7)}주 전`;
  }
  if (days < 365) {
    return `${Math.floor(days / 30)}개월 전`;
  }
  return `${Math.floor(days / 365)}년 전`;
}
