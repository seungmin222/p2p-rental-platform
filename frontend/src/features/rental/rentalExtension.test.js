import { describe, expect, it } from 'vitest';

import {
  estimateExtensionFee,
  getExtensionMonths,
  getExtensionOptions,
  isExtensionPaymentExpired,
  readMaxEndDate,
} from './rentalExtension';

const END = '2026-11-05';

function days(statuses) {
  // 2026-11-06부터 차례로
  return statuses.map((status, index) => ({
    date: `2026-11-${String(6 + index).padStart(2, '0')}`,
    status,
  }));
}

const selectable = (options) => options.filter((o) => o.isSelectable).map((o) => o.date);

describe('getExtensionMonths', () => {
  it('한 달 안이면 그 달 하나', () => {
    expect(getExtensionMonths(END, 7)).toEqual(['2026-11']);
  });

  it('달을 넘으면 두 달', () => {
    expect(getExtensionMonths('2026-11-28', 7)).toEqual(['2026-11', '2026-12']);
    expect(getExtensionMonths('2026-11-30', 7)).toEqual(['2026-12']);
  });
});

describe('getExtensionOptions', () => {
  it('종료일 다음 날부터 maxDays개, 일수는 1부터', () => {
    const options = getExtensionOptions(END, days(Array(7).fill('AVAILABLE')), 7);
    expect(options).toHaveLength(7);
    expect(options[0]).toEqual({ date: '2026-11-06', days: 1, isSelectable: true });
    expect(options[6]).toEqual({ date: '2026-11-12', days: 7, isSelectable: true });
  });

  it('처음으로 AVAILABLE이 아닌 날부터는 뒤가 다시 비어 있어도 고를 수 없다(연장 구간은 이어져야 한다)', () => {
    const options = getExtensionOptions(
      END,
      days([
        'AVAILABLE',
        'AVAILABLE',
        'BOOKED',
        'AVAILABLE',
        'AVAILABLE',
        'AVAILABLE',
        'AVAILABLE',
      ]),
      7,
    );
    expect(selectable(options)).toEqual(['2026-11-06', '2026-11-07']);
  });

  it('캘린더에 없는 날짜·UNAVAILABLE도 막는다', () => {
    expect(selectable(getExtensionOptions(END, days(['AVAILABLE', 'UNAVAILABLE']), 7))).toEqual([
      '2026-11-06',
    ]);
    expect(selectable(getExtensionOptions(END, [], 7))).toEqual([]);
  });

  it('캘린더를 못 불러왔으면(null) 서버에 맡기고 모두 고를 수 있다', () => {
    expect(selectable(getExtensionOptions(END, null, 3))).toHaveLength(3);
  });

  it('서버가 알려 준 maxEndDate 뒤는 막고, null이면 하루도 못 고른다', () => {
    const allAvailable = days(Array(7).fill('AVAILABLE'));
    expect(selectable(getExtensionOptions(END, allAvailable, 7, '2026-11-07'))).toEqual([
      '2026-11-06',
      '2026-11-07',
    ]);
    expect(selectable(getExtensionOptions(END, allAvailable, 7, null))).toEqual([]);
  });
});

describe('readMaxEndDate', () => {
  it('연장 기간 오류의 details maxEndDate', () => {
    const error = {
      code: 'RENTAL_EXTENSION_PERIOD_CONFLICT',
      details: [{ field: 'maxEndDate', reason: '2026-11-08' }],
    };
    expect(readMaxEndDate(error)).toBe('2026-11-08');
    expect(
      readMaxEndDate({
        code: 'RENTAL_OUT_OF_AVAILABLE_PERIOD',
        details: [{ field: 'maxEndDate', reason: '2026-11-07' }],
      }),
    ).toBe('2026-11-07');
  });

  it('details가 비었거나 형식이 틀리면 null(하루도 안 됨)', () => {
    expect(readMaxEndDate({ code: 'RENTAL_EXTENSION_PERIOD_CONFLICT', details: [] })).toBeNull();
    expect(
      readMaxEndDate({
        code: 'RENTAL_EXTENSION_PERIOD_CONFLICT',
        details: [{ field: 'maxEndDate', reason: 'x' }],
      }),
    ).toBeNull();
  });

  it('다른 오류는 undefined(모름)', () => {
    expect(readMaxEndDate({ code: 'RENTAL_EXTENSION_IN_PROGRESS', details: [] })).toBeUndefined();
    expect(readMaxEndDate(null)).toBeUndefined();
  });
});

describe('estimateExtensionFee', () => {
  it('일 대여료 × 일수', () => {
    expect(estimateExtensionFee(5000, 3)).toBe(15000);
    expect(estimateExtensionFee(0, 3)).toBe(0);
  });
});

describe('isExtensionPaymentExpired', () => {
  const at = (text) => new Date(text).getTime();

  it('결제 대기 연장이 없으면 false', () => {
    expect(isExtensionPaymentExpired({ pendingExtension: null }, Date.now())).toBe(false);
  });

  it('만료 시각(서울)이 지나면 true', () => {
    const rental = { pendingExtension: { expiresAt: '2026-11-05T20:15:00' } };
    expect(isExtensionPaymentExpired(rental, at('2026-11-05T20:14:59+09:00'))).toBe(false);
    expect(isExtensionPaymentExpired(rental, at('2026-11-05T20:15:00+09:00'))).toBe(true);
  });
});
