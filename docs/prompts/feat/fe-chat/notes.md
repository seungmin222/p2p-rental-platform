# feat/fe-chat 결정 기록 (SCR-09·10, REQ-026–029 프론트)

## 요약
- 화면: SCR-09 채팅 목록(`/chats`, `pages/ChatListPage.jsx`), SCR-10 채팅방(`/chats/:roomId`, `pages/ChatRoomPage.jsx`), 상세 "채팅하기"·하단 탭·PC 헤더 안 읽은 수 배지. 쓰는 API: `GET /api/chat-rooms`·`/{id}`·`/{id}/messages`, `POST /api/chat-rooms`, STOMP `/pub/chat-rooms/{id}/messages`·`/read`.
- STOMP 공통 모듈 `shared/ws`(`stompClient.js` 앱 전체 연결 하나·구독 receipt·AUTH 거부 3회 제한, `useStompSubscription`, `stompStatusStore`). 채팅 로직은 `features/chat`(`useChatRoomSession.js`, `queries.js`, `chatCache.js`), MSW 가짜 STOMP 서버는 `mocks/chatStomp.js`.
- 남은 일: `/user/queue/notifications` 구독은 알림 브랜치가 `useStompSubscription`으로 건다. 사진(050)·약속(051)·차단(056)·SYSTEM 메시지 생성은 미구현.
- 백엔드에 알릴 것: openapi.json `ChatMessageResponse.senderId` nullable 표시 누락. 연결 끊김 뒤 저장된 메시지가 실패로 보이는 한계는 clientMessageId를 서버가 저장하지 않아서다.

## 정해진 값 (2026-11-13 사용자 승인)
- **STOMP는 `@stomp/stompjs` 7.5.0 하나, SockJS 없음.** 승인 시점 최신 안정판(2026-10-15, 의존성 없음). vite에 `/ws` 프록시가 이미 있고 운영 Nginx도 같은 출처라 폴백이 필요 없다.
- **목은 MSW `ws.link('/ws')` 가짜 STOMP 서버까지 만든다.** 백엔드 없이 채팅 화면 전체(실시간·읽음·거부·재연결)를 확인하려고. 프레임 읽기·쓰기는 `mocks/stompFrame.js`, 서버 흉내는 `mocks/chatStomp.js`.
- **안 읽은 수 배지는 "99+"까지.** 정책 값이 아닌 UI 표기라 `features/chat/constants.js` `UNREAD_BADGE_MAX`에 둔다.
- **보내는 중 메시지에 시간 제한을 두지 않는다.** `/user/queue/errors` 거부와 연결 끊김일 때만 실패로 바꾼다. 정책 수치를 새로 만들지 않으려고.

## 연결 수명 (`shared/ws`)
- **연결은 앱 전체에 하나, authStore AT 유무에 묶는다**(`startStompSession`, `app/router.jsx`). 재발급으로 AT만 바뀌면 다시 연결하지 않는다. 서버가 만료를 CONNECT 때만 보므로 연결 중인 세션은 그대로 유효하고, 다시 연결하면 그 사이 메시지를 보충해야 하는 비용만 생긴다. frontend/CLAUDE.md의 "토큰 재발급 시 재연결" 문구를 이에 맞춰 고쳤다.
- **CONNECT 거부(ERROR `AUTH_UNAUTHORIZED`) → 다음 연결 전 `reissueAccessToken()`**. 실패하면 REST와 같은 unauthorized 핸들러를 부른다(핸들러를 router.jsx에서 함수로 빼 둘 다 등록).
- **구독마다 receipt를 단다.** 서버는 구독을 받으면 RECEIPT, 거부하면 ERROR에 `receipt-id`를 넣는다(StompAuthChannelInterceptor). 그래서 (1) 재연결 뒤 보충 조회 시점(`onReady`)을 알 수 있고 (2) 거부된 구독만 빼서 재연결마다 같은 거부로 끊기는 반복을 막는다.
- **CONNECT가 `AUTH_UNAUTHORIZED`로 연속 3회 거부된 뒤 4번째 거부면 재연결을 멈추고 unauthorized 핸들러를 부른다**(리뷰 반영). 재발급은 성공하는데 서버가 계속 거부하면 무한 반복되므로. 연결에 성공하면 횟수를 0으로. 3은 정책 값이 아닌 연결 설정(`MAX_AUTH_RETRY`).
- 재연결 간격은 1초부터 두 배씩 최대 30초, 하트비트는 stompjs 기본값(10초)을 둔다. 정책이 아닌 연결 설정이다.
- `/user/queue/notifications`는 지금 구독하지 않는다. 받아 쓸 화면이 없어서다. 알림 브랜치가 `useStompSubscription` 한 줄로 건다.

## 캐시·실시간
- **서버 데이터는 모두 TanStack Query 캐시에 두고 이벤트로 `setQueryData`**(`features/chat/chatCache.js` 순수 함수). 별도 스토어를 두지 않는다. 보내는 중 메시지만 화면 상태(useReducer)다(서버 데이터가 아님).
- **내역 캐시는 `staleTime: Infinity`·`gcTime: 0`.** 방에 있는 동안은 토픽·보충으로 고치고, 나가면 구독이 끊겨 캐시가 낡으므로 바로 버린다.
- **빈 구간 보충은 "구독이 받아들여진 뒤 + 내역이 있을 때" 한 번**(재연결마다). 첫 진입에도 한 번 돈다. GET 내역의 시점이 구독보다 앞설 수 있어서다. 이렇게 하면 보충 시점 이전 메시지는 REST로, 이후 메시지는 토픽으로 와서 빠지는 구간이 없다. 가진 메시지가 없으면 첫 페이지만 쓴다.
- **보고 있는 방의 목록 이벤트는 안 읽은 수를 더하지 않는다**(`activeRoom.js`). 이벤트의 수는 서버 커밋 직후 값이라, 토픽 메시지를 먼저 받고 읽음을 보낸 뒤 이벤트가 도착하면 읽은 것이 다시 안 읽음으로 보였다.
- **읽음을 보낸 뒤 전체 수는 목록 캐시의 그 방 수만큼 뺀다.** 나에게 오는 이벤트가 없다. 바로 다시 조회하면 서버가 아직 처리하지 않았을 수 있어서다. 목록 캐시가 없으면 다시 조회한다.
- **내가 보낸 메시지의 목록 반영은 방 토픽으로 돌아온 메시지로 한다.** 목록 이벤트(`/user/queue/chat-rooms`)는 받는 쪽에만 가기 때문이다.
- **연결이 끊기면 보내는 중 메시지를 모두 실패로 바꾼다.** 서버가 clientMessageId를 저장하지 않아 보충 조회로는 임시 메시지와 짝을 맞출 수 없다. 저장됐는데 실패로 보일 수 있고, 그때 다시 보내면 중복이 된다. 이 한계는 받아들였다(리뷰에서도 그대로 두기로 함).
- 읽음 기준값(`counterpartLastReadMessageId`)은 방 정보 캐시에 두고 `/user/queue/chat-reads`로 고친다.

## 화면
- **상단 바는 최근 대여 건이 있으면 "대여 상세", 없으면 대여자에게만 "예약하기".** CANCELED·COMPLETED여도 대여 상세로 간다(서버가 최근 건을 상태 무관으로 준다). 다시 예약하려면 물품 이름 링크로 상세에 간다.
- **시안에서 뺀 것**: 일 대여료(방 정보 응답에 없음), 신뢰 점수·응답 시간(034·3순위), 약속 카드·사진·캘린더·카메라 버튼(051·050). 말풍선·배지 글씨는 토큰 규칙에 따라 `text-body`·`text-xs`로 했다.
- 방 id 형식 검사는 `parseItemId`를 그대로 쓴다(양의 안전한 정수). 형식이 틀리거나 404·403이면 "채팅방을 찾을 수 없어요" + 목록 버튼을 보인다.
- **`RoomBody`에 `key={roomId}`**(리뷰 반영). 방을 바꿀 때 읽음 전송 기록(`lastReadSentRef`)·보내는 중 메시지가 이전 방 것으로 남지 않게.
- **방 토픽 구독이 거부되면 토스트로 알린다**(리뷰 반영, 예: `CHAT_NOT_PARTICIPANT`). 문구는 `errorMessages.js`.
- 말풍선 비대칭 모서리·입력창 `rounded-3xl`은 시안을 따른 규칙 밖 예외로 두고 design-system.md §2.4에 적었다.
- 공통 UI(`shared/ui`)는 새로 만들지 않았다. 배지·말풍선은 채팅에서만 써서 `features/chat/components`에 둔다.

## 명세와 다른 점(백엔드에 알릴 것)
- openapi.json `ChatMessageResponse.senderId`가 nullable로 표시되어 있지 않다(SYSTEM이면 null, api.md에는 있음). 화면은 null로 처리했다. 재생성할 때 `@Schema(nullable = true)`를 다는 것이 좋겠다.
