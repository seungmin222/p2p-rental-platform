# feat/fe-notification 결정 기록

## 요약
- 화면: SCR-11 알림 목록(`/notifications`, `pages/NotificationListPage.jsx`, RequireAuth 안), 하단 탭·PC 헤더 "알림" 안 읽은 수 배지, SCR-08 앞 대여 연체 시 수수료 없는 "예약 취소"(open-issues #7 프론트). 쓰는 API: `GET /api/notifications?cursor=`·`/unread-count`, `PATCH /api/notifications/{id}/read`·`/read-all`, STOMP `/user/queue/notifications`(AppLayout에서 로그인 동안).
- 새 파일: `features/notification/`(api·queries·constants·errorMessages·`notificationTarget.js`·`notificationCache.js`, components/`NotificationListItem`·`NotificationUnreadBadge`), `shared/ui/CountBadge.jsx`(채팅에서 옮김, `formatBadgeCount`는 `shared/lib/format.js`), `rentalData.js` `isOverdueCancelWaiver`, 목 `mocks/notification.js`·`notificationSeed.js`·`notificationLogic.js`(+ 대여 목 12·13, `hasPreviousOverdue`).
- 남은 일: 후기 화면(034 FE)·연장 화면(038 FE)은 다음 브랜치. REVIEW 대상 알림은 이동할 화면이 없어 읽음만 한다(지금 백엔드는 모두 RENTAL). 키워드(052)·설정(053)·웹 푸시(061)는 3순위.
- 확인 못 한 것: 브라우저 확인은 못 했다(앱 브라우저 패널이 MSW 서비스 워커를 등록하지 못함). 단위 테스트·lint·build만 통과.

## 결정
- **이동은 type이 아니라 targetType·targetId로 정한다.** openapi 설명이 그렇게 정했고 종류가 늘어도 표를 고칠 필요가 없다. 대상 id가 없거나 REVIEW·모르는 종류면 링크 없이 누르면 읽음만(오류 토스트 없음).
- **목록 진입만으로는 읽음 처리하지 않는다. 누르면 읽음 + 이동, 위에 "모두 읽음".** 명세에 규칙이 없어 정했다(사용자 승인). 읽음은 누르자마자 이동하므로 캐시를 먼저 고치고, 실패하면 되돌리지 않고 다시 받는다.
- **실시간 수신 때 안 읽은 수는 +1 대신 다시 받는다.** 본문(NotificationResponse)에 전체 수가 없고, 같은 알림이 중복으로 오면 +1이 어긋난다. 대상이 RENTAL이면 그 대여 상세도 무효화(상태가 바뀐 알림이 대부분이라서).
- **CountBadge를 shared/ui로 옮겼다.** 채팅·알림 두 곳이 같은 배지를 쓴다. 상한 99("99+")는 UI 표기라 `COUNT_BADGE_MAX`로 컴포넌트 옆에 둔다.
- **PC 헤더 알림은 Figma의 빨간 점 대신 숫자 배지.** 채팅과 같게(사용자 결정).
- **앞 대여 연체 면제 취소: 대여자에게 "취소 요청"을 숨기고 "예약 취소"만.** 단독 취소가 수수료 없이 되므로 동의 요청은 중복이다. 상대가 보낸 동의 요청이 있으면 거절·동의는 남긴다. 제공자는 앞 대여의 OVERDUE를 받으므로 화면을 바꾸지 않는다.
- **면제 수수료는 프론트에서 계산하지 않고 0원으로 보인다.** openapi `PATCH /cancel` 설명이 면제 시 수수료 없음으로 정했다. 새 정책 값을 쓰지 않는다. "0원" 문구는 결제가 켜져 있을 때만(기존 결제 표시 규칙).
