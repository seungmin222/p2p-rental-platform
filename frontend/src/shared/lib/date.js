// 대여 기간은 "YYYY-MM-DD" 문자열로만 다룬다. new Date('2026-10-01')은 UTC 자정이라
// 브라우저 시간대에 따라 하루가 밀리므로, 계산은 모두 UTC 기준 밀리초로 한다.

const DAY_MS = 24 * 60 * 60 * 1000;
const SEOUL_OFFSET_MS = 9 * 60 * 60 * 1000;
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const MONTH_PATTERN = /^(\d{4})-(\d{2})$/;

function pad2(n) {
  return String(n).padStart(2, '0');
}

function parseDate(date) {
  const match = DATE_PATTERN.exec(date);
  if (!match) {
    throw new Error(`날짜 형식이 아닙니다(YYYY-MM-DD): ${date}`);
  }
  return { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) };
}

function parseMonth(month) {
  const match = MONTH_PATTERN.exec(month);
  if (!match) {
    throw new Error(`월 형식이 아닙니다(YYYY-MM): ${month}`);
  }
  return { year: Number(match[1]), month: Number(match[2]) };
}

function toUtcMs(date) {
  const { year, month, day } = parseDate(date);
  return Date.UTC(year, month - 1, day);
}

function fromUtcMs(ms) {
  const d = new Date(ms);
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}-${pad2(d.getUTCDate())}`;
}

export function addDays(date, days) {
  return fromUtcMs(toUtcMs(date) + days * DAY_MS);
}

/** 시작일·종료일을 모두 포함한 일수. 같은 날이면 1일. */
export function countDaysInclusive(startDate, endDate) {
  return Math.round((toUtcMs(endDate) - toUtcMs(startDate)) / DAY_MS) + 1;
}

export function compareDate(a, b) {
  return Math.sign(toUtcMs(a) - toUtcMs(b));
}

/** 브라우저 시간대와 무관하게 서울 기준 오늘 날짜 */
export function todayInSeoul(now = new Date()) {
  return fromUtcMs(now.getTime() + SEOUL_OFFSET_MS);
}

export function monthOf(date) {
  return date.slice(0, 7);
}

export function addMonths(month, months) {
  const { year, month: m } = parseMonth(month);
  const d = new Date(Date.UTC(year, m - 1 + months, 1));
  return `${d.getUTCFullYear()}-${pad2(d.getUTCMonth() + 1)}`;
}

/** 일요일 시작 달력 칸. 길이는 7의 배수이고, 이번 달이 아닌 앞뒤 칸은 null. */
export function buildMonthGrid(month) {
  const { year, month: m } = parseMonth(month);
  const firstWeekday = new Date(Date.UTC(year, m - 1, 1)).getUTCDay();
  const daysInMonth = new Date(Date.UTC(year, m, 0)).getUTCDate();
  const cells = Array.from({ length: firstWeekday }, () => null);
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push(`${month}-${pad2(day)}`);
  }
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  return cells;
}

/** "2026-10" → "2026년 10월" */
export function formatMonth(month) {
  const { year, month: m } = parseMonth(month);
  return `${year}년 ${m}월`;
}

/** "2026-10-03" → "10월 3일" */
export function formatMonthDay(date) {
  const { month, day } = parseDate(date);
  return `${month}월 ${day}일`;
}
