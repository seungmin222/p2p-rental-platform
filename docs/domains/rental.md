# 예약·대여·반납·연체·연장 (rental)

담당: 이승민(A) · 화면: SCR-05(캘린더), 07, 08, 12 · 엔티티: rentals, rental_status_histories, item_inspections, rental_extensions
**반드시 `state-machines.md`와 함께 읽는다.**

## REQ-012 대여 캘린더 조회 [1]
`GET /api/items/{itemId}/calendar?month=YYYY-MM` (기본 이번 달)
- 날짜별 `AVAILABLE`(예약 가능) / `BOOKED`(예약됨) / `UNAVAILABLE`(가능 기간 외 또는 지난 날짜)
- BOOKED = ACTIVE_FOR_PERIOD 대여(PENDING_PAYMENT는 `payment_expires_at > now`인 것만)가 덮는 날짜
- 응답 예: `{ "month": "2026-10", "days": [{"date":"2026-10-01","status":"UNAVAILABLE"}, ...] }`
- 없음/DELETED 404

## REQ-013 / REQ-014 예약 요청 및 중복 예약 방지 [1] ★핵심
`POST /api/rentals { itemId, startDate, endDate }`

### 검증
- startDate ≥ 오늘, start ≤ end, 대여 일수 1~30일 → 아니면 400
- 본인 물품 → 403 `RENTAL_OWN_ITEM`
- 대표 동네 인증 필요 → 403 `REGION_NOT_VERIFIED`
- 물품 AVAILABLE 아님 → 404/409
- 기간이 **하나의** available_period 안에 완전히 포함 → 아니면 400 `RENTAL_OUT_OF_AVAILABLE_PERIOD`
- (3순위 REQ-062) 예약 제한 중이면 403

### 원자적 처리 (기본 전략)
```
RLock lock = redisson.getLock("lock:item:" + itemId);
if (!lock.tryLock(3, 5, SECONDS)) throw 409 RENTAL_LOCK_TIMEOUT (재시도 안내)
try {
   @Transactional(isolation = READ_COMMITTED)  // 별도 빈 메서드로 호출해야 프록시 적용됨
   ├─ 겹침 조회: status IN ACTIVE_FOR_PERIOD
   │            AND start_date <= :newEnd AND end_date >= :newStart
   │            (PENDING_PAYMENT는 payment_expires_at > now 조건 추가)
   ├─ 있으면 409 RENTAL_PERIOD_CONFLICT "이미 예약된 기간입니다"
   └─ INSERT rental (status 아래 참고) + status_history
} finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
```
- **트랜잭션 커밋이 락 해제보다 먼저** 일어나야 한다. 락 안에서 트랜잭션 메서드를 호출하는 구조로 만들 것(트랜잭션 안에서 락을 잡으면 커밋 전에 락이 풀린다).
- 락 전략은 `RentalLockStrategy` 같은 인터페이스로 추상화해 비교 실험(낙관적/비관적/분산 × 격리 수준)에서 교체 가능하게 한다. 결과는 `docs/experiments/concurrency.md`.

### 금액
- rentalFee = pricePerDay × 일수, 결제 금액 = rentalFee + deposit
- pricePerDay·deposit을 대여 건에 스냅샷

### 생성 상태
- payment-enabled=true: `PENDING_PAYMENT`, `payment_expires_at = now + 15분` → 응답 후 결제 화면
- **1순위(payment-enabled=false)**: 바로 `RESERVED`

### 결제 만료 배치 [2]
매 1분: `PENDING_PAYMENT AND payment_expires_at <= now` → CANCELED(사유 "결제 시간 초과"). 조회 쪽에서도 만료 조건을 걸기 때문에 배치가 늦어도 기간 선점은 풀린다.

## REQ-015 예약 취소 [1]
`PATCH /api/rentals/{id}/cancel { reason: CancelReason, reasonText? }`
- 당사자(대여자·제공자)만 → 아니면 403
- 가능: PENDING_PAYMENT 또는 RESERVED **이고 오늘 < startDate**
- 오늘 ≥ startDate인 RESERVED(인계 안 됨): 단독 취소 409 `RENTAL_CANCEL_NOT_ALLOWED`. **양측 동의**가 있어야 취소 → 한쪽이 요청, 상대가 동의하는 2단계 흐름(구현 제안: `POST /api/rentals/{id}/cancel-agreements`)
- reason=OTHER면 reasonText 필수
- 이력에 취소 주체·사유 기록 (REQ-062 페널티 산정용: 대여자 사유 취소 집계)
- 결제 완료 건: 대여료+보증금 전액 PG 취소(REQ-024 이후). 실패 시 "환불 실패" 기록 + 배치 재시도(멱등성 키)
- 즉시 기간 선점 해제, 상대에게 알림(REQ-032 이후)
- 1순위: 상태만 CANCELED

## REQ-016 대여 상태 관리 [1]
`state-machines.md` §1 전이 표가 명세 그 자체. 구현 규칙:
- `Rental` 엔티티 메서드로만 전이: `confirmPayment()`, `expire()`, `cancel(by, reason)`, `handover(by)`, `markOverdue()`, `requestReturn(by)`, `confirmNormal(by)`, `markAbnormal(by)`, `complete()`, `extend(newEnd)`
- 각 메서드는 현재 상태를 검사하고 위반 시 `InvalidStateTransitionException`(409)
- 전이마다 `RentalStatusHistory` 생성 (도메인 이벤트 → 리스너에서 저장해도 됨)
- 당사자 아님 403
- 상태 변경 후 알림·AI 큐 적재 등 부수효과는 **트랜잭션 커밋 후** (`@TransactionalEventListener(AFTER_COMMIT)`)

## REQ-017 물품 인계 확인 [1]
`PATCH /api/rentals/{id}/handover` (multipart: images 1~5장, memo ≤300자)
- 제공자만(403), RESERVED만(409), 오늘 ≥ startDate(409 `RENTAL_HANDOVER_TOO_EARLY`), 사진 없음 400
- item_inspections(phase=HANDOVER) 저장 — **이후 수정 불가**
- → RENTING, 대여자 알림
- 대여자는 대여 상세에서 인계 사진 확인, 이의는 채팅으로
- 리마인더 배치[2]: startDate 다음 날까지 인계 확인 없음(RESERVED, startDate < 오늘) → 양측 `HANDOVER_REMINDER` 알림 1회. **자동 취소하지 않는다**

## REQ-018 반납 요청 및 상태 확인 [1]

### 대여자 반납 요청 `POST /api/rentals/{id}/return-request`
- 입력: images 1~5장, checklist(5개 항목 각 true/false — `ReturnChecklistItem`), note(어떤 항목이 false면 필수)
- 가드: RENTING 또는 OVERDUE, 대여자 본인. 체크리스트 없이 사진만 → 400
- item_inspections(phase=RETURN) 저장(수정 불가), `returned_at=now` → RETURNED, 제공자 알림
- 2순위: AI 비교 작업 큐 적재(REQ-040)
- **대리 접수**: 대여자가 요청 없이 물품을 돌려준 경우 제공자가 같은 API로 등록 가능(사진·체크리스트를 제공자가 작성, writtenBy=제공자)

### 제공자 상태 확인 `PATCH /api/rentals/{id}/return`
- 입력: result(`NORMAL`/`ABNORMAL`), ABNORMAL이면 내용·사진 필수
- 화면에 인계 사진 / 반납 사진 / 체크리스트(false 항목 강조) / AI 결과를 나란히 (조회는 `GET /inspections`)
- NORMAL → 보증금 전액 반환(REQ-025) 성공 후 COMPLETED → 정산 생성(REQ-039)
- ABNORMAL → RETURNED 유지, depositStatus=HELD, 정산 보류 → `payment.md` REQ-049 흐름
- **자동 처리 배치**: `RETURNED AND returned_at + 3일 <= now AND 판정 없음` → NORMAL로 처리
- 1순위: 보증금 처리 없이 판정 기록 후 COMPLETED (ABNORMAL이어도 기록만 하고 COMPLETED — 1순위에는 차감 흐름이 없음)

## REQ-019 빌린 내역 [1]
`GET /api/members/me/rentals?role=renter&tab=UPCOMING|ONGOING|DONE&page=`
- 탭 매핑은 `state-machines.md` UI 탭 표. 기본 ONGOING. 20건, startDate DESC
- 항목: 물품 썸네일·이름(삭제 시 "삭제된 물품"), 제공자 닉네임, 기간, 결제 금액, 상태 배지
- OVERDUE 건은 목록 최상단. 후기 미작성 COMPLETED 건에 `canWriteReview: true`
- 본인 건만

## REQ-020 내 물품 관리 [1]
`GET /api/members/me/items?tab=AVAILABLE|RENTING|HIDDEN`, `GET /api/items/{itemId}/rentals`
- RENTING 탭 = RENTING·OVERDUE 대여가 있는 물품 / AVAILABLE 탭 = 그 외 공개 물품 / HIDDEN 탭 = 숨김(044 이후)
- 카드: 조회·관심·채팅 수, 다가오는 예약 건수(RESERVED·PENDING_PAYMENT 중 startDate ≥ 오늘)
- 예약 현황: 해당 물품 대여 건 기간순, 각 건 → 대여 상세(제공자 뷰)
- 카드 메뉴: 수정·삭제·숨기기·끌어올리기

## REQ-023 / REQ-031 연체 감지 및 알림 [2 필수]
- Spring Scheduler + ShedLock: 매일 00:05, 그리고 매시 정각 재검사
- 대상: `status = RENTING AND end_date < 오늘` → OVERDUE 일괄 전환(이력: changedBy=null, reason=`OVERDUE_BATCH`)
- 대여자·제공자에게 `OVERDUE` 알림 (DB 저장 → WebSocket). **같은 건 중복 알림 금지**: event_id = `OVERDUE:{rentalId}` 유니크
- 실패 시 로그, 다음 주기 재처리 (전환이 멱등이므로 안전)
- 연체 일수는 신뢰 점수에 반영(REQ-054: 1일당 −3, 건당 최대 −10) → 반납 요청 시점에 `returned_at - end_date`로 계산

## REQ-038 대여 연장 [2]
`POST /api/rentals/{id}/extensions { newEndDate }`
- RENTING만(OVERDUE 불가 → 409 + 반납 안내), 대여자 본인
- extension_count < 2, (newEnd − 기존 end) ≤ 7일 → 위반 400
- 기간 `(기존 end + 1) ~ newEnd`: available_period 안 + ACTIVE_FOR_PERIOD 대여(자기 자신 제외)와 무겹침 → 겹치면 409 + **연장 가능한 최대 날짜** 안내
- 검사는 예약과 같은 락(`lock:item:{itemId}`)
- 추가 대여료 = pricePerDay(스냅샷) × 연장 일수, 보증금 유지
- 흐름: 연장 요청 생성 → 결제(type=EXTENSION) → 승인 시 end_date 갱신 + rental_extensions + extension_count++ + 양측 알림
- 결제 대기 중 연장 기간 선점 방법은 미정 → `open-issues.md` #3
- 결제 실패 시 기존 end 유지

## 테스트 포인트 (필수)
- 겹침 판정 단위 테스트: 경계 동일일(end == newStart → 겹침), 포함, 걸침, 분리
- **동시성 테스트**: 100 스레드가 동일 물품·기간 예약 → 성공 1건. 2개 인스턴스(Testcontainers 또는 docker compose)로도 확인
- 만료된 PENDING_PAYMENT 기간은 다시 예약 가능
- 상태 전이: 허용 전이 전부 성공 + 대표적인 불허 전이(COMPLETED→RENTING 등) 409
- 연체 배치 멱등성(두 번 돌려도 알림 1회)
- 3일 자동 확인 배치
