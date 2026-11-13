// 지금 화면에 보이는 채팅방 id(화면 전용). 보고 있는 방의 목록 갱신 이벤트는 안 읽은 수를 더하지 않는다 —
// 이벤트의 수는 서버 커밋 직후 기준이라, 방 토픽 메시지를 먼저 받아 읽음을 보낸 뒤 이벤트가 오면 읽은 것이 다시 안 읽음으로 보인다.
let activeRoomId = null;

export function setActiveChatRoom(roomId) {
  activeRoomId = roomId;
}

export function clearActiveChatRoom(roomId) {
  if (activeRoomId === roomId) {
    activeRoomId = null;
  }
}

export function getActiveChatRoom() {
  return activeRoomId;
}
