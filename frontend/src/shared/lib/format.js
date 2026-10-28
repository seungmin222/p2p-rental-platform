const numberFormat = new Intl.NumberFormat('ko-KR');

const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

// 서버 시각은 오프셋 없는 LocalDateTime(Asia/Seoul)이다(conventions.md §3).
const OFFSET_PATTERN = /(Z|[+-]\d{2}:?\d{2})$/i;
const SEOUL_OFFSET = '+09:00';

/** 12000 → "12,000원" */
export function formatPrice(amount) {
  return `${numberFormat.format(amount)}원`;
}

/** 12000 → "12,000원/일" */
export function formatDailyPrice(amount) {
  return `${formatPrice(amount)}/일`;
}

/** 50000 → "보증금 50,000원", 0 → "보증금 없음"(DepositStatus NONE 라벨과 같은 표현) */
export function formatDeposit(amount) {
  return amount === 0 ? '보증금 없음' : `보증금 ${formatPrice(amount)}`;
}

/** 오프셋이 없는 시각 문자열은 서울 시각으로 해석한다(브라우저 시간대와 무관). */
export function parseServerDateTime(dateTime) {
  if (dateTime instanceof Date) {
    return dateTime;
  }
  return new Date(OFFSET_PATTERN.test(dateTime) ? dateTime : `${dateTime}${SEOUL_OFFSET}`);
}

/** "방금 전" / "3분 전" / "2시간 전" / "2일 전" / "3주 전" / "5개월 전" / "1년 전" */
export function formatElapsed(dateTime, now = new Date()) {
  const elapsed = now.getTime() - parseServerDateTime(dateTime).getTime();
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
