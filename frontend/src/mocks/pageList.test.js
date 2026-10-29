import { describe, expect, it } from 'vitest';

import {
  countUpcomingRentals,
  listItemRentals,
  listMyItems,
  listRentalHistory,
  readPage,
  toPageResponse,
} from './pageList';
import { createRentalSeed } from './rentalSeed';

const ME = 1;

function rental(rentalId, status, startDate, extra = {}) {
  return { rentalId, status, startDate, endDate: startDate, renterId: ME, itemId: 1, ...extra };
}

describe('listRentalHistory', () => {
  it('OVERDUE 먼저, 그다음 startDate DESC, id DESC', () => {
    const rentals = [
      rental(1, 'RENTING', '2026-10-01'),
      rental(2, 'RETURNED', '2026-10-05'),
      rental(3, 'OVERDUE', '2026-09-01'),
      rental(4, 'RENTING', '2026-10-05'),
    ];
    expect(listRentalHistory(rentals, ME, 'ONGOING').map((r) => r.rentalId)).toEqual([3, 4, 2, 1]);
  });

  it('탭에 속한 상태와 내가 대여자인 건만', () => {
    const rentals = [
      rental(1, 'RESERVED', '2026-10-01'),
      rental(2, 'PENDING_PAYMENT', '2026-10-02'),
      rental(3, 'RENTING', '2026-10-03'),
      rental(4, 'RESERVED', '2026-10-04', { renterId: 3 }),
      rental(5, 'CANCELED', '2026-10-05'),
    ];
    expect(listRentalHistory(rentals, ME, 'UPCOMING').map((r) => r.rentalId)).toEqual([2, 1]);
    expect(listRentalHistory(rentals, ME, 'DONE').map((r) => r.rentalId)).toEqual([5]);
  });

  it('시드의 완료 탭은 20건이 넘어 두 페이지', () => {
    const { rentals } = createRentalSeed();
    const done = listRentalHistory(rentals, ME, 'DONE');
    expect(done.length).toBeGreaterThan(20);
    expect(toPageResponse(done, 0).hasNext).toBe(true);
    expect(toPageResponse(done, 1).hasNext).toBe(false);
  });
});

describe('toPageResponse', () => {
  const all = Array.from({ length: 45 }, (_, i) => i);

  it('20건씩 자르고 다음 페이지 여부를 알린다', () => {
    expect(toPageResponse(all, 0)).toMatchObject({ page: 0, size: 20, totalElements: 45 });
    expect(toPageResponse(all, 0).items).toHaveLength(20);
    expect(toPageResponse(all, 1).hasNext).toBe(true);
    expect(toPageResponse(all, 2)).toMatchObject({ items: [40, 41, 42, 43, 44], hasNext: false });
  });

  it('딱 20건이면 다음 페이지가 없다, 범위 밖 페이지는 빈 목록', () => {
    expect(toPageResponse(all.slice(0, 20), 0).hasNext).toBe(false);
    expect(toPageResponse(all, 9)).toMatchObject({ items: [], hasNext: false });
  });
});

describe('readPage', () => {
  it('없으면 0, 0 이상 정수만', () => {
    expect(readPage(null)).toBe(0);
    expect(readPage('3')).toBe(3);
    expect(readPage('-1')).toBeNull();
    expect(readPage('1.5')).toBeNull();
    expect(readPage('a')).toBeNull();
  });

  it('int32 범위를 넘으면 null(400)', () => {
    expect(readPage('2147483647')).toBe(2147483647);
    expect(readPage('2147483648')).toBeNull();
    expect(readPage('99999999999999999999')).toBeNull();
  });
});

describe('내 물품', () => {
  const items = [{ itemId: 1 }, { itemId: 2 }, { itemId: 3 }];
  const rentals = [
    rental(1, 'OVERDUE', '2026-10-01', { itemId: 1 }),
    rental(2, 'RETURNED', '2026-10-01', { itemId: 2 }),
    rental(3, 'RESERVED', '2026-10-29', { itemId: 2 }),
    rental(4, 'PENDING_PAYMENT', '2026-11-02', {
      itemId: 2,
      paymentExpiresAt: '2026-10-29T12:10:00',
    }),
    rental(5, 'RESERVED', '2026-10-28', { itemId: 2 }),
    rental(6, 'CANCELED', '2026-11-03', { itemId: 2 }),
  ];

  it('RENTING·OVERDUE 대여가 있으면 대여 중 탭, 나머지는 대여 가능 탭(최근 등록 순)', () => {
    expect(listMyItems(items, rentals, 'RENTING').map((i) => i.itemId)).toEqual([1]);
    expect(listMyItems(items, rentals, 'AVAILABLE').map((i) => i.itemId)).toEqual([3, 2]);
    expect(listMyItems(items, rentals, 'HIDDEN')).toEqual([]);
  });

  it('다가오는 예약은 RESERVED·PENDING_PAYMENT 중 시작일이 오늘 이후', () => {
    expect(countUpcomingRentals(rentals, 2, '2026-10-29', '2026-10-29T12:00:00')).toBe(2);
  });

  it('결제 시간이 지난 PENDING_PAYMENT는 세지 않는다(정각에 만료)', () => {
    const pending = (paymentExpiresAt) => [
      rental(1, 'PENDING_PAYMENT', '2026-11-02', { itemId: 9, paymentExpiresAt }),
    ];
    const now = '2026-10-29T12:00:00';
    expect(countUpcomingRentals(pending('2026-10-29T12:10:00'), 9, '2026-10-29', now)).toBe(1);
    expect(countUpcomingRentals(pending('2026-10-29T12:00:00'), 9, '2026-10-29', now)).toBe(0);
    expect(countUpcomingRentals(pending('2026-10-29T11:50:00'), 9, '2026-10-29', now)).toBe(0);
  });

  it('예약 현황은 그 물품의 대여 건 전부(CANCELED 포함)', () => {
    expect(listItemRentals(rentals, 2).map((r) => r.rentalId)).toEqual([2, 5, 3, 4, 6]);
  });
});

describe('listItemRentals 정렬(open-issues #20)', () => {
  const item = (rentalId, status, startDate) => rental(rentalId, status, startDate, { itemId: 7 });

  it('다가오는·진행 중 건이 먼저, 완료·취소 건이 뒤에', () => {
    const rentals = [
      item(1, 'COMPLETED', '2026-11-20'),
      item(2, 'RESERVED', '2026-11-01'),
      item(3, 'CANCELED', '2026-11-25'),
      item(4, 'PENDING_PAYMENT', '2026-11-30'),
      item(5, 'RENTING', '2026-10-27'),
      item(6, 'OVERDUE', '2026-10-20'),
      item(7, 'RETURNED', '2026-10-25'),
    ];
    const statuses = listItemRentals(rentals, 7).map((r) => r.status);
    expect(statuses.slice(0, 5)).not.toContain('COMPLETED');
    expect(statuses.slice(5).sort()).toEqual(['CANCELED', 'COMPLETED']);
  });

  it('앞 그룹은 시작일 오름차순, 뒤 그룹은 시작일 내림차순', () => {
    const rentals = [
      item(1, 'COMPLETED', '2026-10-01'),
      item(2, 'RESERVED', '2026-11-05'),
      item(3, 'CANCELED', '2026-10-15'),
      item(4, 'RENTING', '2026-10-28'),
      item(5, 'COMPLETED', '2026-09-20'),
      item(6, 'PENDING_PAYMENT', '2026-11-01'),
    ];
    expect(listItemRentals(rentals, 7).map((r) => r.rentalId)).toEqual([4, 6, 2, 3, 1, 5]);
  });

  it('같은 시작일은 두 그룹 모두 id 오름차순', () => {
    const rentals = [
      item(9, 'RESERVED', '2026-11-01'),
      item(3, 'PENDING_PAYMENT', '2026-11-01'),
      item(8, 'CANCELED', '2026-10-01'),
      item(2, 'COMPLETED', '2026-10-01'),
    ];
    expect(listItemRentals(rentals, 7).map((r) => r.rentalId)).toEqual([3, 9, 2, 8]);
  });

  it('시드 물품 107: 다가오는 예약 32가 맨 위, 지난 건은 최근 것부터', () => {
    const ids = listItemRentals(createRentalSeed().rentals, 107).map((r) => r.rentalId);
    expect(ids[0]).toBe(32);
    expect(ids.slice(1, 4)).toEqual([71, 72, 73]);
    expect(ids).toHaveLength(23);
  });
});
