// 경로의 물품 id. Number로 안전하게 옮길 수 있는 양의 정수만 받는다(더 길면 1e+23이나 다른 id로 바뀌어 요청된다).

/** @returns {number | null} */
export function parseItemId(value) {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) {
    return null;
  }
  const itemId = Number(value);
  return itemId > 0 && Number.isSafeInteger(itemId) ? itemId : null;
}
