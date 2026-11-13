// 채팅 목 시드. 메모리 저장소라 새로고침하면 시드로 돌아간다. 시각은 지금 기준 상대값.
//
// | 방 | 물품 | 제공자 | 대여자 | 경우 |
// | --- | --- | --- | --- | --- |
// | 1 | 101 | 캠핑조아(2) | 나(1) | 대화 중, 안 읽은 2건, 최근 대여 건 있음(대여 목 기준) → "대여 상세" |
// | 2 | 202 | 이웃(4) | 나(1) | 메시지 3건 중 안 읽은 2건(SYSTEM 안내 줄 포함), 대여한 적 없음 → "예약하기" |
// | 3 | 103 | 나(1) | 텐트초보(3) | 내가 제공자, 상대가 내 메시지를 읽음 → 내 메시지 '읽음' |
// | 4 | 105 | 캠핑조아(2) | 나(1) | 메시지 없음(생성 시각으로 정렬, "대화를 시작해 보세요") |
// | 5 | 104 | 나(1) | 탈퇴 회원(98) | 탈퇴한 사용자 → 입력 비활성 |
// | 6 | 102 | 캠핑조아(2) | 나(1) | 메시지 120건 → 위로 스크롤하면 이전 페이지 2번 |
// | 7 | 106 | 이웃(4) | 나(1) | 삭제된 물품, 최근 대여 건 연체(대여 목 8번) |
//
// 실시간(mocks/chatStomp.js): 내가 보내면 1.5초 뒤 상대가 읽고, 3초 뒤 자동 답장한다. 내용이 "/fail"이면 서버 거부를 흉내 낸다.
import { toServerDateTime } from './rentalSeed';

export const WITHDRAWN_MEMBER_ID = 98;

const MINUTE_MS = 60 * 1000;

function minutesAgo(minutes) {
  return toServerDateTime(new Date(Date.now() - minutes * MINUTE_MS));
}

export function createChatSeed() {
  const rooms = [
    { roomId: 1, itemId: 101, ownerId: 2, renterId: 1, createdAt: minutesAgo(60 * 50) },
    { roomId: 2, itemId: 202, ownerId: 4, renterId: 1, createdAt: minutesAgo(60 * 30) },
    { roomId: 3, itemId: 103, ownerId: 1, renterId: 3, createdAt: minutesAgo(60 * 26) },
    { roomId: 4, itemId: 105, ownerId: 2, renterId: 1, createdAt: minutesAgo(90) },
    {
      roomId: 5,
      itemId: 104,
      ownerId: 1,
      renterId: WITHDRAWN_MEMBER_ID,
      createdAt: minutesAgo(60 * 24 * 10),
    },
    { roomId: 6, itemId: 102, ownerId: 2, renterId: 1, createdAt: minutesAgo(60 * 24 * 5) },
    { roomId: 7, itemId: 106, ownerId: 4, renterId: 1, createdAt: minutesAgo(60 * 24 * 12) },
  ];

  const messages = [];
  let nextMessageId = 1;
  function add(roomId, senderId, content, minutes, type = 'TEXT') {
    messages.push({
      messageId: nextMessageId,
      roomId,
      senderId,
      type,
      content,
      sentAt: minutesAgo(minutes),
    });
    nextMessageId += 1;
  }

  // 방 6: 오래된 대화 120건(페이지 넘김 확인용)
  for (let i = 0; i < 120; i += 1) {
    add(6, i % 2 === 0 ? 1 : 2, `지난 대화 ${i + 1}`, 60 * 24 * 4 - i * 10);
  }
  add(7, 4, '반납 일정 확인 부탁드려요', 60 * 24 * 2);
  add(5, WITHDRAWN_MEMBER_ID, '혹시 다음 주에도 대여 가능할까요?', 60 * 24 * 9);
  add(5, 1, '네, 가능해요', 60 * 24 * 9 - 30);
  add(3, 3, '안녕하세요, 아직 대여 가능한가요?', 60 * 25);
  add(3, 1, '네 가능합니다. 원하시는 날짜 알려 주세요', 60 * 24 + 50);
  add(2, 1, '주말에 빌릴 수 있을까요?', 60 * 3);
  add(2, null, '예약 요청이 들어왔어요', 60 * 2 + 30, 'SYSTEM');
  add(2, 4, '네 토요일 오전에 오세요', 60 * 2);
  add(1, 1, '안녕하세요! 이번 주말에 빌릴 수 있을까요?', 60 * 49);
  add(1, 2, '네 가능해요. 금요일 오전 괜찮으세요?', 60 * 48);
  add(1, 1, '좋아요, 금요일 10시에 뵐게요', 60 * 47);
  add(1, 2, '구성품 사진 보내 드릴게요', 25);
  add(1, 2, '팩이랑 망치도 같이 드려요', 20);

  const lastId = (roomId) =>
    messages.filter((message) => message.roomId === roomId).at(-1)?.messageId ?? 0;

  return {
    rooms,
    messages,
    lastRead: {
      // 방 1: 나는 마지막 2건을 안 읽음, 상대는 내 마지막 메시지까지 읽음
      '1:1': lastId(1) - 2,
      '1:2': lastId(1) - 2,
      // 방 2: 내 메시지까지만 읽음 → SYSTEM 1 + 상대 1 = 안 읽은 2건
      '2:1': messages.find((m) => m.roomId === 2).messageId,
      '3:1': lastId(3),
      '3:3': lastId(3),
      '5:1': lastId(5),
      '6:1': lastId(6),
      '6:2': lastId(6),
      '7:1': lastId(7),
    },
    nextRoomId: rooms.length + 1,
    nextMessageId,
  };
}
