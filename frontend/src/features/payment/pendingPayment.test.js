import { describe, expect, it } from 'vitest';

import { clearPendingPayment, readPendingPayment, savePendingPayment } from './pendingPayment';

function fakeStorage(initial = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

describe('pendingPayment', () => {
  it('저장한 rentalId를 읽고 지우면 null', () => {
    const storage = fakeStorage();
    savePendingPayment(7, storage);
    expect(readPendingPayment(storage)).toBe(7);
    clearPendingPayment(storage);
    expect(readPendingPayment(storage)).toBeNull();
  });

  it.each(['not json', '{"rentalId":"7"}', '{"rentalId":-1}', 'null'])(
    '값이 이상하면(%s) null',
    (value) => {
      expect(readPendingPayment(fakeStorage({ 'rental.pendingPayment': value }))).toBeNull();
    },
  );

  it('저장소를 못 써도 던지지 않는다', () => {
    const broken = {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
      removeItem: () => {
        throw new Error('blocked');
      },
    };
    expect(() => savePendingPayment(1, broken)).not.toThrow();
    expect(readPendingPayment(broken)).toBeNull();
    expect(() => clearPendingPayment(broken)).not.toThrow();
  });
});
