# API 목차

- 이 문서는 **경로·권한·REQ·순위의 색인**이다. 구현 전 API까지 포함한 계획표 역할을 한다.
- **필드(요청·응답 구조)는 여기에 쓰지 않는다.** 구현된 API의 필드는 백엔드 DTO 코드 → Swagger(`/swagger-ui.html`) → `docs/openapi.json` 순으로 자동 반영된다.
- Swagger 태그 = 아래 섹션명(Member, Item, Rental, Payment, Chat, Notification, Review, Admin), `@Operation` summary에 REQ ID 포함.
- 인증: `X` 불필요, `O` 회원, `A` 관리자, `옵션` 비회원도 가능하나 로그인 시 추가 동작.
- 순위: 엔드포인트를 구현할 단계. 해당 단계 전에는 만들지 않는다.
- 새 API를 만들면 여기에 한 줄 추가한다.

## 회원·인증

| 순위 | Method | Path | 설명 | 인증 | REQ |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | /api/members | 회원가입 | X | 001 |
| 1 | POST | /api/auth/login | 로그인 (AT 응답, RT 쿠키) | X | 002 |
| 1 | POST | /api/auth/logout | 로그아웃 (RT 쿠키 기준, 만료된 AT로도 가능) | X | 002 |
| 1 | POST | /api/auth/reissue | 토큰 재발급(RT 쿠키) | X | 003 |
| 1 | GET | /api/members/me | 내 정보 | O | 005 |
| 1 | PATCH | /api/members/me | 프로필 수정 (multipart) | O | 005 |
| 1 | PATCH | /api/members/me/password | 비밀번호 변경 | O | 005 |
| 1 | GET | /api/regions?keyword= | 동네 검색 | X | 004 |
| 1 | GET | /api/regions/by-coord?lat=&lng= | 좌표→행정동 (카카오 로컬 프록시) | X | 004 |
| 1 | GET | /api/members/me/regions | 내 동네 목록 | O | 004 |
| 1 | POST | /api/members/me/regions | 동네 추가 | O | 004 |
| 1 | PATCH | /api/members/me/regions/{id}/verify | 동네 인증(좌표 전송) | O | 004 |
| 1 | PATCH | /api/members/me/regions/{id}/primary | 대표 동네 설정 | O | 004 |
| 1 | DELETE | /api/members/me/regions/{id} | 동네 삭제 | O | 004 |
| 3 | PATCH | /api/members/me/regions/{id}/range | 범위 설정 | O | 042 |
| 2 | GET | /api/members/{id} | 사용자 프로필 | X | 021 |
| 2 | GET | /api/members/{id}/reviews | 받은 후기 | X | 021, 034 |
| 2 | GET | /api/members/{id}/items | 등록 물품 | X | 021 |
| 2 | POST | /api/auth/password-reset | 재설정 메일 | X | 036 |
| 2 | POST | /api/auth/password-reset/confirm | 재설정 | X | 036 |
| 2 | GET | /api/terms | 약관·정책 문서 | X | 037 |
| 3 | POST | /api/auth/oauth/kakao | 카카오 로그인 | X | 041 |
| 3 | DELETE | /api/members/me | 회원 탈퇴 | O | 043 |

## 물품

| 순위 | Method | Path | 설명 | 인증 | REQ |
| --- | --- | --- | --- | --- | --- |
| 1 | GET | /api/categories | 활성 카테고리 | X | 006 |
| 1 | POST | /api/items | 물품 등록 (multipart) | O | 006 |
| 1 | GET | /api/items/deposit-guide?categoryId=&itemPrice= | 권장 보증금 범위 | O | 006 |
| 1 | PATCH | /api/items/{itemId} | 수정 | O | 007 |
| 1 | DELETE | /api/items/{itemId} | 소프트 삭제 | O | 008 |
| 1 | GET | /api/items/feed?regionId=&range=&categoryId=&cursor= | 동네 피드 (regionId 생략 시 로그인 회원 대표 동네) | 옵션 | 009 |
| 1 | GET | /api/items?regionId=&range=&keyword=&categoryId=&minPrice=&maxPrice=&startDate=&endDate=&availableOnly=&sort=&page= | 검색 | 옵션 | 010 |
| 1 | GET | /api/items/{itemId} | 상세 | 옵션 | 011 |
| 1 | GET | /api/items/{itemId}/calendar?month=YYYY-MM | 대여 캘린더 | X | 012 |
| 1 | GET | /api/members/me/items/{itemId} | 수정 화면 조회 (소유자 전용, itemPrice 포함) | O | 007 |
| 2 | POST | /api/items/{itemId}/wishes | 관심 추가 | O | 022 |
| 2 | DELETE | /api/items/{itemId}/wishes | 관심 해제 | O | 022 |
| 2 | GET | /api/members/me/wishes | 관심 목록 | O | 022 |
| 2 | GET | /api/members/me/recent-items | 최근 본 상품 | O | 035 |
| 3 | GET | /api/items/recommended | 추천 | 옵션 | 060 |
| 3 | PATCH | /api/items/{itemId}/hide · /unhide | 숨기기 | O | 044 |
| 3 | PATCH | /api/items/{itemId}/bump | 끌어올리기 | O | 045 |
| 3 | GET | /api/search/popular · /api/search/recent | 인기·최근 검색어 | X·O | 046 |
| 3 | GET | /api/items/{itemId}/related | 다른 물품·비슷한 물품 | X | 047 |
| 3 | GET | /api/search/suggest?q= | 자동완성 | X | 064 |
| 3 | POST | /api/items/ai-category | AI 카테고리 추천 (multipart, 사진 저장 안 함) | O | 067 |
| 3 | POST | /api/items/ai-draft | AI 등록 보조: 제목·설명 초안, 대여가·보증금 제안 (미구현, 카테고리 추천은 위 API) | O | 067 |

## 대여

| 순위 | Method | Path | 설명 | 인증 | REQ |
| --- | --- | --- | --- | --- | --- |
| 1 | POST | /api/rentals | 예약 요청 | O | 013, 014 |
| 1 | GET | /api/rentals/{id} | 대여 상세(당사자) | O | 016 |
| 1 | PATCH | /api/rentals/{id}/cancel | 취소(사유) | O | 015 |
| 1 | POST | /api/rentals/{id}/cancel-agreements | 시작일 이후 양측 동의 취소 요청 | O | 015 |
| 1 | PATCH | /api/rentals/{id}/cancel-agreements/{agreementId} | 동의·거절 (`action: ACCEPT \| REJECT`) | O | 015 |
| 1 | PATCH | /api/rentals/{id}/handover | 인계 확인 (multipart: 사진·메모) | O | 017 |
| 1 | POST | /api/rentals/{id}/return-request | 반납 요청 (사진·체크리스트) | O | 018 |
| 1 | PATCH | /api/rentals/{id}/return | 제공자 상태 확인(정상/이상) | O | 018 |
| 2 | POST | /api/rentals/{id}/lost-report | 미반납 신고(예정일 + 7일 이후) | O | 018, 049 |
| 1 | GET | /api/rentals/{id}/inspections | 인계·반납 기록(+AI 결과) | O | 017, 018, 040 |
| 1 | GET | /api/members/me/rentals?role=renter&tab=&page= | 빌린 내역 | O | 019 |
| 1 | GET | /api/members/me/items?tab=&page= | 내 물품 관리 | O | 020 |
| 1 | GET | /api/items/{itemId}/rentals?page= | 물품별 예약 현황(제공자) | O | 020 |
| 2 | POST | /api/rentals/{id}/extensions | 연장 요청. 연장료 > 0이고 결제를 켰으면 결제 대기(기간 선점), 아니면 바로 확정 | O | 038 |
| 2 | POST | /api/rentals/{id}/reviews | 후기 작성 | O | 034 |

> 원 명세는 인계 사진 등록을 `POST /api/rentals/{id}/inspections`로도 표기한다. 인계 확인과 사진 등록은 한 요청이어야 하므로 `PATCH /handover`(multipart) 하나로 합쳤다(`open-issues.md` #4).

## 결제·정산

| 순위 | Method | Path | 설명 | 인증 | REQ |
| --- | --- | --- | --- | --- | --- |
| 2 | POST | /api/payments | 주문 생성. `extensionId`가 있으면 연장료 주문 | O | 024, 038 |
| 2 | POST | /api/payments/confirm | 결제 승인(대여·연장 공통, orderId로 구분) | O | 024, 038 |
| — | — | ~~/api/payments/{id}/cancel~~ | **외부 API 없음.** 원 명세에 있던 경로지만, 회원이 직접 환불을 호출할 수 있어 제거. 취소·환불은 대여 취소(015), 보증금 처리(025·049) 안에서 서버가 내부적으로만 PG를 호출 | — | 015, 025 |
| 2 | PATCH | /api/rentals/{id}/deposit | 보증금: 차감 제안 / 수락 / 거절 (`action` 필드) | O | 049 |
| 2 | GET | /api/rentals/{id}/deposit/policy?grade= | 등급별 차감 상한 계산 | O | 049 |
| 2 | GET | /api/members/me/payments?type=&from=&to=&page= | 결제·보증금 내역 | O | 033 |
| 2 | GET | /api/members/me/settlements?page= | 정산 내역 | O | 039 |
| 2 | GET | /api/admin/deposits/held | 보류 7일 초과 건 목록 (관리자 확정 대상) | A | 049, 057 |
| 2 | PATCH | /api/admin/rentals/{id}/deposit | 보증금 차감액 관리자 확정 (2순위로 앞당김) | A | 049, 057 |

## 채팅

| 순위 | Method | Path | 설명 | 인증 | REQ |
| --- | --- | --- | --- | --- | --- |
| 2 | POST | /api/chat-rooms | 생성 또는 기존 방 반환 (itemId) | O | 026 |
| 2 | GET | /api/chat-rooms | 목록 | O | 028 |
| 2 | GET | /api/chat-rooms/{id} | 방 정보 (상대, 물품 요약, 최근 대여 건 상태 — 상단 바 표시용) | O | 026, 028 |
| 2 | GET | /api/chat-rooms/{id}/messages?cursor= | 내역(50건) | O | 028 |
| 2 | GET | /api/chat-rooms/unread-count | 전체 안 읽은 수 | O | 029 |
| 2 | WS | /ws (STOMP) | CONNECT 헤더 `Authorization: Bearer <AT>` · SEND `/pub/chat-rooms/{id}`(027), `/pub/chat-rooms/{id}/read`(029) · SUB `/topic/chat-rooms.{id}`(방 메시지, 참여자만), `/user/queue/chat-rooms`(목록 갱신), `/user/queue/notifications`(알림), `/user/queue/errors`(SEND 거부 회신) · 거부된 CONNECT·SUBSCRIBE는 ERROR 프레임(`message` = 에러 코드) 후 연결 종료. 본문 형태는 아래 "STOMP 메시지" | O | 027, 029, 032 |
| 3 | POST | /api/chat-rooms/{id}/images | 사진 전송 | O | 050 |
| 3 | POST | /api/chat-rooms/{id}/appointments | 약속 잡기 | O | 051 |

### STOMP 메시지 (Swagger에 나오지 않아 여기 적는다, 027)

| 목적지 | 방향 | 본문 |
| --- | --- | --- |
| `/pub/chat-rooms/{id}` | SEND | `{ type: "TEXT", content, clientMessageId? }` — content 공백만 불가·`chat.message.max` 이하, clientMessageId는 낙관적 표시용 임시 ID(저장 안 함) |
| `/topic/chat-rooms.{id}` | MESSAGE | `{ messageId, roomId, senderId, type, content, sentAt, clientMessageId }` — 보낸 사람도 받는다. SYSTEM은 senderId null |
| `/user/queue/chat-rooms` | MESSAGE | `{ roomId, lastMessageId, lastMessagePreview, lastMessageAt }` — 메시지를 받은 쪽(상대방)에게 |
| `/user/queue/notifications` | MESSAGE | 알림 목록 API의 항목과 같은 형태(`NotificationResponse`) |
| `/user/queue/errors` | MESSAGE | `{ clientMessageId, error: { code, message, details } }` — 보낸 세션에만, 연결 유지. 코드: `COMMON_INVALID_INPUT`, `CHAT_ROOM_NOT_FOUND`, `CHAT_NOT_PARTICIPANT` |

## 알림

| 순위 | Method | Path | 설명 | 인증 | REQ |
| --- | --- | --- | --- | --- | --- |
| 2 | GET | /api/notifications?cursor= | 목록 | O | 032 |
| 2 | PATCH | /api/notifications/{id}/read | 읽음 | O | 032 |
| 2 | PATCH | /api/notifications/read-all | 모두 읽음 | O | 032 |
| 2 | GET | /api/notifications/unread-count | 안 읽은 알림 수 (상단 아이콘 배지) | O | 032 |
| 3 | GET/PATCH | /api/notifications/settings | 알림 설정 | O | 053 |
| 3 | POST/DELETE | /api/keywords | 키워드 알림 | O | 052 |

## 신뢰·기타

| 순위 | Method | Path | 설명 | 인증 | REQ |
| --- | --- | --- | --- | --- | --- |
| 3 | POST | /api/reports | 신고 | O | 055 |
| 3 | POST/DELETE | /api/blocks | 차단 | O | 056 |
| 3 | POST | /api/inquiries | 문의·분쟁 접수 (분쟁이면 정산 보류) | O | 063 |
| 3 | GET | /api/inquiries?page= | 내 문의 목록·처리 상태 | O | 063 |
| 3 | GET/PATCH | /api/admin/members · /items · /reports · /disputes · /categories · /category-suggestions | 관리자 (보증금 확정 2개 API는 위 결제 섹션에서 2순위로 먼저 구현) | A | 057 |
