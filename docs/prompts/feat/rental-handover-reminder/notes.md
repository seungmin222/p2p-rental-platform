# feat/rental-handover-reminder 결정 기록 (REQ-017 인계 리마인더)

## 대상 범위
- **`RESERVED AND 오늘 - handover.auto-cancel-days ≤ startDate < 오늘`** (사용자 승인). 반납 리마인더처럼 `startDate = 어제`만 보지 않는다: 명세 문구가 "startDate < 오늘 … 1회"이고, 배치를 하루 빠뜨려도 다음 날 따라잡게 하기 위함. 하한은 같은 09:00에 도는 `NoHandoverCancelJob`의 대상(startDate < 오늘 - 3)과 겹치지 않게 해서 두 배치의 실행 순서에 의존하지 않게 했다.
- 1회 보장은 event_id `HANDOVER_REMINDER:{rentalId}`(기존 `rentalEventId`) + notifications `(member_id, event_id)` UK에 맡긴다. 범위 안의 대여는 최대 3일 연속 조회되지만 저장은 회원별 1건.

## 구조
- 조회·발송은 반납 리마인더와 같은 `RentalOverdueService`·`RentalOverdueTxService`에 붙이고 `Participants` record를 재사용했다(새 서비스를 만들 만큼의 로직이 없다). 잡은 `rental/batch/HandoverReminderJob`, 락 `rental.handover-reminder`, cron `app.batch.handover-reminder-cron`(09:00).
- 마이그레이션 없음.
