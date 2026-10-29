import { describe, expect, it } from 'vitest';

import { toItemRental } from './api';

describe('toItemRental', () => {
  it('대여자 닉네임을 꺼내 시트 한 줄 모양으로', () => {
    const rental = {
      rentalId: 23,
      startDate: '2026-10-29',
      endDate: '2026-10-31',
      status: 'RESERVED',
      renter: { memberId: 3, nickname: '텐트초보' },
    };
    expect(toItemRental(rental)).toEqual({
      rentalId: 23,
      startDate: '2026-10-29',
      endDate: '2026-10-31',
      status: 'RESERVED',
      renterNickname: '텐트초보',
    });
  });

  it('대여자가 없으면 빈 닉네임', () => {
    expect(
      toItemRental({ rentalId: 1, startDate: 'a', endDate: 'b', status: 'RESERVED' }),
    ).toMatchObject({ renterNickname: '' });
  });
});
