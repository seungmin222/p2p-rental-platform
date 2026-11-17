// 알림 목록 캐시(useInfiniteQuery 데이터 { pages: CursorResponse[], pageParams })를 고치는 순수 함수.

function mapItems(data, mapItem) {
  return {
    ...data,
    pages: data.pages.map((page) => ({ ...page, items: (page.items ?? []).map(mapItem) })),
  };
}

/**
 * 실시간으로 받은 알림을 첫 페이지 맨 앞에 넣는다. 이미 있는 notificationId면 그대로(undefined 반환 = 고치지 않음).
 * 목록을 아직 안 불러왔으면 undefined — 다음에 화면에 들어올 때 새로 받는다.
 */
export function prependNotification(data, notification) {
  if (!data?.pages?.length) {
    return undefined;
  }
  const exists = data.pages.some((page) =>
    (page.items ?? []).some((item) => item.notificationId === notification.notificationId),
  );
  if (exists) {
    return undefined;
  }
  const [first, ...rest] = data.pages;
  return { ...data, pages: [{ ...first, items: [notification, ...(first.items ?? [])] }, ...rest] };
}

/** 알림 하나를 읽음으로. 목록이 없으면 undefined */
export function markReadInList(data, notificationId) {
  if (!data?.pages) {
    return undefined;
  }
  return mapItems(data, (item) =>
    item.notificationId === notificationId && !item.isRead ? { ...item, isRead: true } : item,
  );
}
