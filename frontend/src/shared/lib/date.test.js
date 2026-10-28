import { describe, expect, it } from 'vitest';

import {
  addDays,
  addMonths,
  buildMonthGrid,
  compareDate,
  countDaysInclusive,
  formatDateRange,
  formatMonth,
  formatMonthDay,
  todayInSeoul,
  weekdayOf,
} from './date';

describe('addDays', () => {
  it('월·연 경계를 넘는다', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2027-01-01', -1)).toBe('2026-12-31');
  });

  it('윤년 2월을 반영한다', () => {
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
    expect(addDays('2027-02-28', 1)).toBe('2027-03-01');
  });
});

describe('countDaysInclusive', () => {
  it('같은 날은 1일', () => {
    expect(countDaysInclusive('2026-10-01', '2026-10-01')).toBe(1);
  });

  it('시작·종료일을 포함해 센다', () => {
    expect(countDaysInclusive('2026-10-01', '2026-10-30')).toBe(30);
    expect(countDaysInclusive('2026-10-01', '2026-10-31')).toBe(31);
    expect(countDaysInclusive('2026-12-30', '2027-01-02')).toBe(4);
    expect(countDaysInclusive('2028-02-28', '2028-03-01')).toBe(3);
  });

  it('종료일이 시작일보다 앞서면 오류', () => {
    expect(() => countDaysInclusive('2026-10-02', '2026-10-01')).toThrow();
  });
});

describe('날짜 검증', () => {
  it('없는 날짜는 거부한다', () => {
    expect(() => addDays('2026-02-30', 0)).toThrow();
    expect(() => addDays('2026-13-01', 0)).toThrow();
    expect(() => addDays('2026-00-10', 0)).toThrow();
    expect(() => addDays('2026-04-31', 0)).toThrow();
    expect(() => addDays('2026-10-00', 0)).toThrow();
  });

  it('윤년 2/29는 허용, 평년 2/29는 거부', () => {
    expect(addDays('2028-02-29', 0)).toBe('2028-02-29');
    expect(() => addDays('2027-02-29', 0)).toThrow();
  });

  it('월은 01~12만', () => {
    expect(() => addMonths('2026-00', 1)).toThrow();
    expect(() => addMonths('2026-13', 1)).toThrow();
    expect(() => buildMonthGrid('2026-13')).toThrow();
    expect(addMonths('2026-12', 0)).toBe('2026-12');
  });
});

describe('compareDate', () => {
  it('앞이면 -1, 같으면 0, 뒤면 1', () => {
    expect(compareDate('2026-09-30', '2026-10-01')).toBe(-1);
    expect(compareDate('2026-10-01', '2026-10-01')).toBe(0);
    expect(compareDate('2027-01-01', '2026-12-31')).toBe(1);
  });

  it('형식이 아니면 오류', () => {
    expect(() => compareDate('2026-10-1', '2026-10-01')).toThrow();
  });
});

describe('todayInSeoul', () => {
  it('UTC 15시 이후는 서울 기준 다음 날', () => {
    expect(todayInSeoul(new Date('2026-10-31T14:59:59Z'))).toBe('2026-10-31');
    expect(todayInSeoul(new Date('2026-10-31T15:00:00Z'))).toBe('2026-11-01');
    expect(todayInSeoul(new Date('2026-12-31T15:00:00Z'))).toBe('2027-01-01');
  });
});

describe('addMonths', () => {
  it('연 경계를 넘는다', () => {
    expect(addMonths('2026-12', 1)).toBe('2027-01');
    expect(addMonths('2027-01', -1)).toBe('2026-12');
    expect(addMonths('2026-10', 14)).toBe('2027-12');
  });
});

describe('buildMonthGrid', () => {
  it('일요일 시작, 앞뒤 빈칸은 null', () => {
    // 2026-10-01은 목요일
    const grid = buildMonthGrid('2026-10');
    expect(grid.slice(0, 5)).toEqual([null, null, null, null, '2026-10-01']);
    // 31일이 토요일이라 뒤 빈칸 없음
    expect(grid).toHaveLength(35);
    expect(grid.at(-1)).toBe('2026-10-31');

    // 2026-11-30은 월요일 → 뒤 빈칸 5개
    const november = buildMonthGrid('2026-11');
    expect(november).toHaveLength(35);
    expect(november.slice(-6)).toEqual(['2026-11-30', null, null, null, null, null]);
  });

  it('윤년 2월은 29일', () => {
    const grid = buildMonthGrid('2028-02');
    expect(grid.filter(Boolean)).toHaveLength(29);
    expect(grid.filter(Boolean).at(-1)).toBe('2028-02-29');
  });

  it('일요일에 시작해 토요일에 끝나는 달은 빈칸이 없다', () => {
    // 2026-02-01은 일요일, 28일은 토요일
    expect(buildMonthGrid('2026-02')).toHaveLength(28);
    expect(buildMonthGrid('2026-02')[0]).toBe('2026-02-01');
  });
});

describe('표시 포맷', () => {
  it('월·일 라벨', () => {
    expect(formatMonth('2026-10')).toBe('2026년 10월');
    expect(formatMonthDay('2026-10-03')).toBe('10월 3일');
  });

  it('요일', () => {
    expect(weekdayOf('2026-10-09')).toBe('금');
    expect(weekdayOf('2026-10-11')).toBe('일');
    expect(weekdayOf('2028-02-29')).toBe('화');
  });
});

describe('formatDateRange', () => {
  it('같은 달', () => {
    expect(formatDateRange('2026-10-09', '2026-10-11')).toBe('10월 9일(금) ~ 11일(일) · 3일');
  });

  it('달이 다르면 끝에도 월을 쓴다', () => {
    expect(formatDateRange('2026-10-30', '2026-11-02')).toBe('10월 30일(금) ~ 11월 2일(월) · 4일');
  });

  it('연 경계는 연도 없이 월로 구분하고 일수를 이어 센다', () => {
    expect(formatDateRange('2026-12-30', '2027-01-02')).toBe('12월 30일(수) ~ 1월 2일(토) · 4일');
    expect(formatDateRange('2026-12-31', '2027-01-01')).toBe('12월 31일(목) ~ 1월 1일(금) · 2일');
  });

  it('같은 날은 하루', () => {
    expect(formatDateRange('2026-10-09', '2026-10-09')).toBe('10월 9일(금) · 1일');
  });

  it('순서가 뒤집히면 오류', () => {
    expect(() => formatDateRange('2026-10-11', '2026-10-09')).toThrow();
  });
});
