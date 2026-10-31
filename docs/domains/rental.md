# 예약·대여·반납·연체·연장 (rental)

담당: 이승민(A) · 화면: SCR-05(캘린더), 07, 08, 12 · 엔티티: rentals, rental_status_histories, rental_cancel_agreements, item_inspections, rental_extensions
**반드시 `state-machines.md`와 함께 읽는다.** 수치는 `policies.md`가 기준이다.

> **선행 머지**: `rentals`, `rental_status_histories` 엔티티와 마이그레이션은 B 담당의 피드 "대여 중" 배지(009), 기간 검색(010), 내 물품 관리(020)가 의존한다. `rentals`가 `items`·`members`를 FK로 참조하므로 그 엔티티·마이그레이션만 담은 `feat/member-item-entity`(A가 B 대행) 머지 직후, `feat/item-feed-search` 착수 전에 엔티티·마이그레이션·`ACTIVE_FOR_PERIOD` 상수만 담은 작은 PR(`feat/rental-entity`)을 먼저 머지한다.

## REQ-012 대여 캘린더 조회 [1]
`GET /api/items/{itemId}/calendar?month=YYYY-MM` (기본 이번 달)
- 날짜별 `AVAILABLE`(예약 가능) / `BOOKED`(예약됨) / `UNAVAILABLE`(가능 기간 외 또는 지난 날짜)
- BOOKED = ACTIVE_FOR_PERIOD 대여(PENDING_PAYMENT는 `payment_expires_at > now`인 것만)와 결제 대기 중인 연장 기간이 덮는 날짜
- 응답 예: `{ "month": "2026-10", "days": [{"date":"2026-10-01","status":"UNAVAILABLE"}, ...] }`
- 없음/DELETED 404

## REQ-013 / REQ-014 예약 요청 및 중복 예약 방지 [1] ★핵심
`POST /api/rentals { itemId, startDate, endDate }`

### 검증
- startDate ≥ 오늘, start ≤ end, 대여 일수 1~30일 → 아니면 400
- 본인 물품 → 400 `RENTAL_OWN_ITEM`
- 대표 동네 인증 필요 → 403 `REGION_NOT_VERIFIED`
- 물품 AVAILABLE 아님 → 404/409
- 기간이 **하나의** available_period 안에 완전히 포함 → 아니면 400 `RENTAL_OUT_OF_AVAILABLE_PERIOD`
- (3순위 REQ-062) 예약 제한 중이면 403

### 원자적 처리 (기본 전략)
```
RLock lock = redisson.getLock("lock:item:" + itemId);
if (!lock.tryLock(3, SECONDS)) throw BusinessException(RENTAL_LOCK_TIMEOUT)   // 409, 재시도 안내
try {
   rentalTxService.create(...)   // 별도 빈의 @Transactional(isolation = READ_COMMITTED) 메서드
   ├─ 겹침 조회: status IN ACTIVE_FOR_PERIOD
   │            AND start_date <= :newEnd AND end_date >= :newStart
   │            (PENDING_PAYMENT는 payment_expires_at > now 조건 추가)
   │            + 결제 대기 중 연장(rental_extensions PENDING_PAYMENT, 만료 전) 기간
   ├─ 있으면 409 RENTAL_PERIOD_CONFLICT "이미 예약된 기간입니다"
   └─ INSERT rental (status 아래 참고) + status_history
} finally { if (lock.isHeldByCurrentThread()) lock.unlock(); }
```
- **임대 시간(leaseTime)을 지정하지 않는다.** `tryLock(wait, lease, unit)`처럼 임대 시간을 주면 Redisson의 watchdog 자동 연장이 꺼져, 트랜잭션이 임대 시간을 넘기면 커밋 전에 락이 풀린다. 대기 시간만 주면 watchdog이 락을 쥔 동안 연장하고, 서버가 죽으면 기본 30초 뒤 풀린다.
- **트랜잭션 커밋이 락 해제보다 먼저** 일어나야 한다. 락 안에서 트랜잭션 메서드(별도 빈)를 호출하는 구조로 만들 것(트랜잭션 안에서 락을 잡으면 커밋 전에 락이 풀린다).
- 락 전략은 `RentalLockStrategy` 인터페이스로 추상화해 비교 실험에서 교체 가능하게 한다. 비교 대상(원본 도전 과제 ①): 격리 수준(READ COMMITTED / REPEATABLE READ) × {낙관적 락(version), 비관적 락(`SELECT ... FOR UPDATE`), Redisson 분산 락, **DB 유니크 인덱스(날짜별 점유 테이블)**}. 결과는 `docs/experiments/concurrency.md`.
- **DB 유니크 인덱스 방식**: `rental_dates(item_id, rented_date, rental_id)`에 UK(item_id, rented_date). 예약·연장 시 기간의 날짜마다 한 행씩 INSERT하고, 중복 키 오류가 나면 409로 바꾼다. 취소·결제 만료 시 해당 행 삭제, 반납 완료 시에는 유지(이력). 락 없이도 DB가 최종 방어선이 되므로, 실험 결과에 따라 분산 락과 **함께** 2차 방어로 쓰는 것도 선택지다. 실험 전에는 테이블만 만들고 기본 경로에서 쓰지 않는다.

### 금액
- rentalFee = pricePerDay × 일수, 결제 금액 = rentalFee + deposit
- pricePerDay·deposit을 대여 건에 스냅샷

### 생성 상태
- payment-enabled=true, 결제 금액 > 0: `PENDING_PAYMENT`, `payment_expires_at = now + 15분` → 응답 후 결제 화면
- 결제 금액 = 0 (무료 대여): 결제 레코드 없이 바로 `RESERVED`
- **1순위(payment-enabled=false)**: 바로 `RESERVED`

### 결제 만료 배치 [2]
매 1분: `PENDING_PAYMENT AND payment_expires_at <= now` → CANCELED(cancelType=`PAYMENT_EXPIRED`). 조회 쪽에서도 만료 조건을 걸기 때문에 배치가 늦어도 기간 선점은 풀린다.

## REQ-015 예약 취소 [1]

### 단독 취소 `PATCH /api/rentals/{id}/cancel { reason: CancelReason, reasonText? }`
- 당사자(대여자·제공자)만 → 아니면 403 `RENTAL_NOT_PARTICIPANT`. 없는 대여 404 `RENTAL_NOT_FOUND`
- 가능: PENDING_PAYMENT, 또는 RESERVED **이고 오늘 < startDate**. 그 외 상태는 409 `RENTAL_INVALID_TRANSITION`
- 오늘 ≥ startDate인 RESERVED(인계 안 됨): 단독 취소 409 `RENTAL_CANCEL_NOT_ALLOWED` → 양측 동의 취소로 안내
- reason=OTHER면 reasonText 필수(200자) → 아니면 400 `COMMON_INVALID_INPUT`
- `canceled_at`, `cancel_type`(RENTER / OWNER), 사유를 rental과 이력에 기록 (REQ-062 페널티 산정용)
- **환불액**: `policies.md` §2 취소 수수료 표. 대여자 단독 취소가 시작일 2일 전~전날이면 대여료의 30%를 `cancel_fee`로 남기고 나머지와 보증금을 PG 부분 취소. 그 외는 전액 취소
- cancel_fee > 0이면 정산 생성(`payment.md` REQ-039)
- PG 취소 실패 시 depositStatus=REFUND_FAILED + 재시도 배치(멱등성 키). rental은 즉시 CANCELED로 두고 기간 선점도 즉시 해제(환불 재시도는 결제 쪽에서 계속)
- 상대에게 알림(REQ-032 이후)
- 1순위: 상태만 CANCELED (수수료 계산은 하지만 금액 처리 없음). 화면은 결제가 꺼진 1순위에서 수수료를 표시하지 않는다(결제 없이 금액 처리가 없으므로)

### 양측 동의 취소 (시작일 이후, 인계 전)
- 요청 `POST /api/rentals/{id}/cancel-agreements { reason, reasonText? }`: 당사자 한쪽, RESERVED(아니면 409 `RENTAL_INVALID_TRANSITION`)이고 오늘 ≥ startDate(아니면 409 `RENTAL_CANCEL_AGREEMENT_TOO_EARLY` → 단독 취소로 안내). 진행 중인 요청이 있으면 409 `RENTAL_CANCEL_AGREEMENT_IN_PROGRESS`. 성공 201. 상대에게 `CANCEL_AGREEMENT_REQUESTED` 알림
- 응답 `PATCH /api/rentals/{id}/cancel-agreements/{agreementId} { action: ACCEPT | REJECT }`: 요청하지 않은 쪽만(요청자 본인은 403 `RENTAL_CANCEL_AGREEMENT_SELF_RESPONSE`). 그 대여의 요청이 아니면 404 `RENTAL_CANCEL_AGREEMENT_NOT_FOUND`, 이미 응답했거나 만료된 요청은 409 `RENTAL_CANCEL_AGREEMENT_CLOSED`. ACCEPT → rental CANCELED(cancelType=`MUTUAL`), 전액 환불. REJECT → 요청 종료, 양측 `CANCEL_AGREEMENT_RESULT` 알림
- 48시간 무응답 → EXPIRED (배치 10분)
- 저장: `rental_cancel_agreements` (erd.md)

### 인계 없음 자동 취소 [2]
- 매일 09:00: `RESERVED AND start_date + 3일 < 오늘` → CANCELED(cancelType=`NO_HANDOVER`), 전액 환불, 양측 알림
- 원본 명세의 "자동 취소하지 않음"을 바꾼 정책이다. 양측 무응답 시 결제금이 영구히 묶이는 것을 막기 위함

## REQ-016 대여 상태 관리 [1]
`state-machines.md` §1 전이 표가 명세 그 자체. 구현 규칙:
- `Rental` 엔티티 메서드로만 전이: `cancel(canceledBy, type, reason, reasonText, cancelFee, now)`, `handover(now)`, `requestReturn(now)`, `confirmNormal(now)`, `markAbnormal()`, `complete(now)`. 2순위 브랜치에서 추가: `confirmPayment()`, `expire()`, `markOverdue()`, `reportLost(now)`, `extend(newEnd)`
- 전이 메서드는 상태 가드만 보고 행위자를 받지 않는다. 누가 했는지는 서비스가 같은 트랜잭션에 저장하는 이력의 `changedBy`로 남긴다(대리 접수·양측 동의처럼 행위자를 엔티티가 알 수 없는 경우가 있다)
- 각 메서드는 현재 상태를 검사하고 위반 시 `BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION)`(409). 전이 전용 예외 클래스를 만들지 않는다
- 전이 시각을 컬럼에 기록: `handed_over_at`, `returned_at`, `completed_at`, `canceled_at`
- 전이마다 `RentalStatusHistory` 생성 (도메인 이벤트 → 리스너에서 저장해도 됨)
- 당사자 아님 403
- 상태 변경 후 알림·AI 큐 적재 등 부수효과는 **트랜잭션 커밋 후** (`@TransactionalEventListener(AFTER_COMMIT)`)

## REQ-017 물품 인계 확인 [1]
`PATCH /api/rentals/{id}/handover` (multipart: images 1~5장, memo ≤300자) — 사진 저장과 상태 전이를 한 요청으로 처리. 사진 형식·장수·용량은 policies.md `inspection.images.*`
- 제공자만(대여자 403 `RENTAL_OWNER_ONLY`, 제3자 403 `RENTAL_NOT_PARTICIPANT`), RESERVED만(409 `RENTAL_INVALID_TRANSITION`), 오늘 ≥ startDate(409 `RENTAL_HANDOVER_TOO_EARLY`), 사진 장수 위반 400 `IMAGE_COUNT_INVALID`
- multipart의 request part는 `application/json`(아니면 415 `COMMON_UNSUPPORTED_MEDIA_TYPE`). 동시에 다른 요청이 대여를 바꿨으면 409 `COMMON_CONCURRENT_UPDATE`(인계·반납 요청 공통. 상태 확인은 대여 행 락으로 한 줄로 서므로 늦은 쪽이 409 `RENTAL_INVALID_TRANSITION`)
- item_inspections(phase=HANDOVER) 저장 — **이후 수정 불가**
- → RENTING, `handed_over_at=now`, 대여자 알림
- 대여자는 대여 상세에서 인계 사진 확인, 이의는 채팅으로
- 리마인더 배치[2]: 매일 09:00, startDate 다음 날까지 인계 확인 없음(RESERVED, startDate < 오늘) → 양측 `HANDOVER_REMINDER` 알림 1회. 시작일 + 3일이 지나면 위의 자동 취소

## REQ-018 반납 요청 및 상태 확인 [1]

### 대여자 반납 요청 `POST /api/rentals/{id}/return-request`
- 입력: images 1~5장(형식·장수·용량은 policies.md `inspection.images.*`), checklist(5개 항목 각 true/false — `ReturnChecklistItem`), note(어떤 항목이 false면 필수, 300자)
- 가드: RENTING 또는 OVERDUE(아니면 409 `RENTAL_INVALID_TRANSITION`), 대여자 본인 또는 제공자 대리 접수(제3자 403 `RENTAL_NOT_PARTICIPANT`). 체크리스트 없이 사진만·항목 누락·모르는 키 → 400 `COMMON_INVALID_INPUT`, 사진 장수 위반 → 400 `IMAGE_COUNT_INVALID`
- item_inspections(phase=RETURN) 저장(수정 불가), `returned_at=now` → RETURNED, 제공자 알림
- 2순위: AI 비교 작업 큐 적재(REQ-040)
- **대리 접수**: 대여자가 요청 없이 물품을 돌려준 경우 제공자가 같은 API로 등록 가능(사진·체크리스트를 제공자가 작성, writtenBy=제공자)

### 미반납 신고 `POST /api/rentals/{id}/lost-report { note }` [2]
- 제공자만, OVERDUE이고 오늘 > endDate + 7일
- 반납 사진 없이 item_inspections(phase=RETURN, note, lost_reported=true) 기록 → RETURNED, `returned_at=now`, depositStatus=HELD
- 제공자는 이어서 D 등급 차감을 제안한다(`payment.md` REQ-049). 대여자에게 `LOST_REPORTED` 알림
- 이후 대여자가 물건을 돌려주면 제공자가 상태 확인(정상/이상)으로 처리
- 연체료 계산 기준일은 신고일

### 제공자 상태 확인 `PATCH /api/rentals/{id}/return`
- 입력: result(`NORMAL`/`ABNORMAL`), ABNORMAL이면 내용(500자)·사진 필수. NORMAL에 사진을 보내면 400 `IMAGE_COUNT_INVALID`
- 가드: 제공자만(대여자 403 `RENTAL_OWNER_ONLY`), RETURNED이고 반납 기록이 판정 전일 때만(아니면 409 `RENTAL_INVALID_TRANSITION`)
- 화면에 인계 사진 / 반납 사진 / 체크리스트(false 항목 강조) / AI 결과 / 연체료를 나란히 (조회는 `GET /inspections`)
- NORMAL → 보증금 처리(REQ-025: 연체료 차감 후 남은 금액 반환) 완료 후 COMPLETED(`completed_at=now`) → 정산 생성(REQ-039). 반환이 실패했거나 PG 결과를 알 수 없으면(타임아웃) 200에 RETURNED로 답하고 재시도 배치가 완료한다
- ABNORMAL → RETURNED 유지, depositStatus=HELD, 정산 보류 → `payment.md` REQ-049 흐름
- **자동 처리 배치**: `RETURNED AND returned_at + 3일 <= now AND 판정 없음 AND 미반납 신고 아님` → NORMAL로 처리
- 1순위: 보증금 처리 없이 판정 기록 후 COMPLETED (ABNORMAL이어도 기록만 하고 COMPLETED — 1순위에는 차감 흐름이 없음)

## REQ-019 빌린 내역 [1]
`GET /api/members/me/rentals?role=renter&tab=UPCOMING|ONGOING|DONE&page=`
- `role`은 필수이고 `renter`만 허용(그 외·누락 400). 빌려준 쪽은 REQ-020
- 탭 매핑은 `state-machines.md` UI 탭 표. 기본 ONGOING. 페이지 크기는 `rental-history.page`(20건) 고정이고 size 파라미터를 받지 않는다
- 정렬(모든 탭 공통): OVERDUE 건이 목록 최상단, 그다음 startDate DESC, id DESC
- 항목: 물품 썸네일·이름(삭제 시 이름은 그대로 두고 `isDeleted: true` → 화면이 "삭제된 물품" 표시), 제공자 닉네임, 기간, 결제 금액(대여료 + 보증금 스냅샷), 상태 배지. 화면은 결제가 꺼진 1순위에서 금액 이름을 '합계'로 표시한다(결제가 없으므로)
- 후기 미작성 COMPLETED 건(completed_at + `review.write-deadline-days` 이내)에 `canWriteReview: true`. 시각 기준이라 +7일 정각은 true, 1초 지나면 false. "미작성" 조건은 후기 테이블이 생기는 REQ-034에서 더한다
- 결제 시간이 지난 PENDING_PAYMENT도 만료 배치(REQ-024)가 바꾸기 전까지 UPCOMING에 그대로 보인다
- 본인 건만

## REQ-020 내 물품 관리 [1]
`GET /api/members/me/items?tab=AVAILABLE|RENTING|HIDDEN&page=`, `GET /api/items/{itemId}/rentals?page=`
- RENTING 탭 = RENTING·OVERDUE 대여가 있는 물품 / AVAILABLE 탭 = 그 외 공개 물품 / HIDDEN 탭 = 숨김(044 이후)
- 카드: 조회·관심·채팅 수, 다가오는 예약 건수(RESERVED·PENDING_PAYMENT 중 startDate ≥ 오늘)
- 예약 현황: 해당 물품 대여 건 전부(CANCELED 포함). 다가오는·진행 중 건(PENDING_PAYMENT·RESERVED·RENTING·OVERDUE·RETURNED)을 시작일 오름차순으로 먼저, 완료·취소 건을 시작일 내림차순으로 뒤에(open-issues #20). 각 건 → 대여 상세(제공자 뷰). 남의 물품 403 `ITEM_NOT_OWNER`, 없음·삭제 404 `ITEM_NOT_FOUND`
- 카드 메뉴: 수정·삭제·숨기기·끌어올리기

## REQ-023 / REQ-031 연체 감지·연체료·알림 [2 필수]
- Spring Scheduler + ShedLock: 매일 00:05, 그리고 매시 정각 재검사
- 대상: `status = RENTING AND end_date < 오늘` → OVERDUE 일괄 전환(이력: changedBy=null, reason=`OVERDUE_BATCH`)
- 대여자·제공자에게 `OVERDUE` 알림 (DB 저장 → WebSocket). **같은 건 중복 알림 금지**: event_id = `OVERDUE:{rentalId}` 유니크
- 실패 시 로그, 다음 주기 재처리 (전환이 멱등이므로 안전)
- **반납 리마인더**: 매일 09:00, `RENTING AND end_date = 내일` → 대여자 `RETURN_REMINDER` (event_id `RETURN_REMINDER:{rentalId}:{endDate}` — 연장으로 end_date가 바뀌면 다시 보낼 수 있게)
- **연체료**: 연체일수 = (반납 요청일 또는 미반납 신고일) − end_date. 연체료 = min(연체일수 × floor(pricePerDay × 1.5), deposit). 반납 시점에 `rentals.overdue_fee`로 확정해 저장하고, 보증금 처리(REQ-025)에서 먼저 차감. 대여 상세에는 OVERDUE 동안 "현재까지 연체료"를 계산해 보여 준다
- 연체 일수는 신뢰 점수에도 반영(REQ-054)
- 1순위·무료 대여(보증금 0원): 연체료를 계산·기록만 하고 징수하지 않음

## REQ-038 대여 연장 [2]
`POST /api/rentals/{id}/extensions { newEndDate }`
- RENTING만(OVERDUE 불가 → 409 + 반납 안내), 대여자 본인
- extension_count < 2, (newEnd − 기존 end) ≤ 7일 → 위반 400
- 기간 `(기존 end + 1) ~ newEnd`: available_period 안 + ACTIVE_FOR_PERIOD 대여(자기 자신 제외)·결제 대기 연장과 무겹침 → 겹치면 409 + **연장 가능한 최대 날짜** 안내
- 검사는 예약과 같은 락(`lock:item:{itemId}`)
- 추가 대여료 = pricePerDay(스냅샷) × 연장 일수, 보증금 유지
- 흐름: rental_extensions(status=`PENDING_PAYMENT`, expires_at=now+15분) 생성 → 그 기간을 선점 → 결제(type=EXTENSION) → 승인 시 extension `CONFIRMED`, end_date 갱신, extension_count++, 양측 알림
- 15분 내 미결제·결제 실패 → extension `CANCELED`, 기존 end 유지(배치 1분)
- 추가 대여료가 0원(무료 대여)이면 결제 없이 바로 CONFIRMED

## 테스트 포인트 (필수)
- 겹침 판정 단위 테스트: 경계 동일일(end == newStart → 겹침), 포함, 걸침, 분리
- **동시성 테스트**: 100 스레드가 동일 물품·기간 예약 → 성공 1건. 2개 인스턴스(Testcontainers 또는 docker compose)로도 확인
- 트랜잭션이 길어져도(지연 주입) 락이 커밋 전에 풀리지 않는지
- 만료된 PENDING_PAYMENT 기간은 다시 예약 가능, 결제 대기 연장 기간은 예약 불가
- 상태 전이: 허용 전이 전부 성공 + 대표적인 불허 전이(COMPLETED→RENTING 등) 409
- 취소 수수료: 시작일 3일 전 / 2일 전 / 전날 / 제공자 취소 경계
- 연체료: 0일, 1일, 보증금 초과 상한, 보증금 0원
- 연체 배치·반납 리마인더 멱등성(두 번 돌려도 알림 1회)
- 3일 자동 확인 배치, 시작일+3일 자동 취소 배치
