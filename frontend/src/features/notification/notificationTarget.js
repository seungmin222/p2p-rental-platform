// 알림을 눌렀을 때 갈 화면. 종류(type)가 아니라 targetType·targetId로 정한다(openapi NotificationResponse).

const PATH_BY_TARGET = {
  RENTAL: (id) => `/rentals/${id}`, // SCR-08
  ITEM: (id) => `/items/${id}`, // SCR-05
  CHAT_ROOM: (id) => `/chats/${id}`, // SCR-10
  // REVIEW: 후기 하나를 보는 화면이 없다 → 이동하지 않는다
};

/** 이동할 경로. 대상 id가 없거나, 모르는 종류이거나, 화면이 없는 종류(REVIEW)면 null(눌러도 읽음만 처리) */
export function getNotificationPath(notification) {
  const toPath = PATH_BY_TARGET[notification?.targetType];
  const id = notification?.targetId;
  if (!toPath || id == null) {
    return null;
  }
  return toPath(id);
}
