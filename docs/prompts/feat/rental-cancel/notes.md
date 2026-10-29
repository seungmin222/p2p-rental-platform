# feat/rental-cancel 결정 기록

REQ-015 단독 취소 + 양측 동의 취소(1순위), 016 중 취소 전이. 결제·환불·정산·알림·인계 없음 자동 취소·페널티 집계는 범위 밖(수수료는 계산해 `rentals.cancel_fee`에 기록만).

## 전이
- **`Rental.validateCancelable(type, today)`를 공개해 서비스가 수수료 계산 전에 부르고, `cancel()`도 다시 부른다.** 수수료가 cancel의 인자라 계산 전에 가드를 통과해야 한다. 가드를 한 곳에 두면 두 경로의 조건이 갈라지지 않는다.
- **PENDING_PAYMENT 단독 취소는 시점 제한이 없다.** 시작일이 오늘인 예약을 자정 직전에 만들면 시작일이 지난 결제 대기가 생길 수 있고, state-machines에도 결제 대기 사용자 취소에 시점 조건이 없다. `CancelFeeCalculator`도 PENDING_PAYMENT를 날짜 검사보다 먼저 보고 0을 돌려준다(시작일 이후 IllegalArgumentException은 RESERVED에서만).
- **이번에 받는 취소 종류는 RENTER·OWNER·MUTUAL뿐**, 시스템 종류(PAYMENT_EXPIRED 등)는 IllegalArgumentException. 각 배치 브랜치(024·017)에서 가드를 정한다.
- **이력은 엔티티가 아니라 서비스가 같은 트랜잭션에 저장한다.** MUTUAL은 이력 changedBy(수락자)와 canceledBy(요청자)가 달라서 엔티티가 알 수 없다. 사유 문자열은 `RentalStatusHistory.cancelReasonOf`("SCHEDULE_CHANGE", 텍스트가 있으면 "OTHER: {텍스트}").
- **사유 텍스트는 앞뒤 공백을 지우고 비면 null로 본다.** 공백만 보낸 OTHER도 400.

## 동시성
- **취소·동의 요청·동의 응답은 모두 `RentalRepository.findByIdForUpdate`(PESSIMISTIC_WRITE)로 대여 행을 잠그고 시작한다.** 같은 대여의 취소 경로끼리 한 줄로 선다. `@Version`은 앞으로 생길 다른 경로(인계 017 등)와의 충돌용이고, 충돌하면 `GlobalExceptionHandler`가 `ObjectOptimisticLockingFailureException` → 409 `COMMON_CONCURRENT_UPDATE`.
- **락 대기 시간 힌트를 두지 않는다.** 락 안의 작업이 행 한두 개라 대기는 밀리초 단위다. MySQL에는 문장 단위 `FOR UPDATE WAIT n`이 없고, `jakarta.persistence.lock.timeout` 힌트를 Hibernate 7이 MySQL에서 어떻게 처리하는지 확인하지 못했다. 지금은 `innodb_lock_wait_timeout` 기본 50초를 기다리고, 넘으면 500으로 나간다. **락 실험 브랜치에서 다시 본다**(타임아웃 값·409 매핑).
- **단독 취소는 물품 Redisson 락을 잡지 않는다.** 취소와 같은 기간 예약이 겹치면 예약은 커밋 전 상태(RESERVED)를 보고 409 `PERIOD_CONFLICT`가 된다. 중복 예약이 아니라 거절 쪽이라 허용한다. 행 락은 PK라 갭 락이 없어 새 대여 INSERT를 막지 않는다.

## 양측 동의 취소
- **MUTUAL 취소의 `rentals.canceled_by`는 요청자, 이력 `changed_by`는 수락자.** rental에 남는 사유가 요청자의 사유라 "누가·왜"가 짝이 맞고, 이력은 "누가 상태를 바꿨나"라 실제 전이를 일으킨 수락자다. `rental_cancel_agreements`에 응답자 컬럼이 없어 수락자는 이력에만 남는다. 페널티(REQ-062)는 trust.md대로 `cancel_type`만 보고(대여자 사유 취소 = `RENTER`) MUTUAL은 집계하지 않으므로 이 선택이 페널티에 영향을 주지 않는다.
- **진행 중 = REQUESTED이고 expires_at > now**(`RentalCancelAgreementRepository.findOpen`). 상태만 보면 만료 배치가 늦을 때 새 요청이 막힌다. 응답도 같은 조건(`isOpen`)이라 정확히 만료 시각이면 409 CLOSED.
- **동의 요청 가드는 서비스에 둔다**(RESERVED 아님 → INVALID_TRANSITION, 시작일 전 → TOO_EARLY). 대여 전이가 아니라 요청 생성이라서다. 수락 시 전이는 `Rental.cancel(MUTUAL)`이 다시 확인한다.
- **응답 검사 순서: 대여 404 → 당사자 403 → 요청 404(다른 대여 포함) → 본인 응답 403 → 닫힘 409.** 본인 응답은 상태와 상관없는 권한 문제라 닫힘보다 먼저 본다.
- **`RentalCancelAgreement`에 `@Version`을 두지 않는다. 만료 배치와 응답이 겹치면 "만료 전에 확인을 통과한 응답이 이긴다".** 만료는 벌크 UPDATE라 버전을 올리지 않으므로 `@Version`을 두면 벌크와 섞여 의도치 않은 409나 덮어쓰기가 생긴다. 응답끼리는 대여 행 락으로 직렬화되고, 배치의 벌크 쿼리는 `status = REQUESTED and expires_at <= :now`로 상태를 다시 확인해 이미 응답한 행을 건드리지 않는다.
- **수락·거절 응답은 같은 트랜잭션 안에서 `RentalQueryService.getRental`로 만든다.** 진행 중인 요청 조회(JPQL)가 실행되기 전 자동 flush로 ACCEPTED·REJECTED가 반영돼 activeCancelAgreement가 null이 된다. 통합 테스트로 고정했다.

## 만료 배치
- **`batch/CancelAgreementExpireJob`은 `RentalCancelService.expireOverdue(now)`만 부르고, 서비스는 벌크 `@Modifying(clearAutomatically = true, flushAutomatically = true)`로 EXPIRED 처리한다.** 대여 상태를 바꾸지 않으니 대여 이력은 없다. 벌크라 auditing이 돌지 않아 updated_at을 쿼리에서 직접 넣는다.
- **cron은 `app.batch.cancel-agreement-expire-cron`**(10분, test 프로필은 `"-"`로 끄고 테스트가 직접 호출). 이후 배치 키도 `app.batch.*`에 모은다. ShedLock 이름 `rental.cancel-agreement-expire`, lockAtMostFor 5m(단일 UPDATE라 넉넉함)·lockAtLeastFor 30s.

## 수수료·정책 키
- **`cancel-fee.rate-percent`(30)·`cancel-fee.free-until-days-before`(3)를 policies.md §2 표 아래에 추가**, 코드에 30·3을 쓰지 않는다. 계산은 long으로 하고 원 미만 절사.
- **policies.md 키는 PolicyProperties 바인딩 형태에 맞춘다.** `cancel.reason-text.max`를 `cancel.reason-text-max`로 바꿨다(PR1의 `rental.lock-wait`과 같은 이유: 점 하나마다 중첩 record가 생긴다).
- **사유 검증은 클래스 수준 `@ValidCancelReason`**(PolicyProperties 주입). 길이 상한이 설정값이라 `@Size`에 넣을 수 없다. 오류 필드는 reasonText.

## 테스트
- **상세 조회 쿼리 수는 Hibernate Statistics로 단언한다.** test 프로필에서 `generate_statistics`를 켜고 세션 통계 로그는 WARN으로 낮췄다. 요청 직전에 `Statistics.clear()`를 불러 픽스처 저장이 섞이지 않게 하고 MockMvc 호출 하나의 `prepareStatementCount`만 본다. PR1 기준 4개 → activeCancelAgreement 조회 1개를 더해 5개. 대여가 RESERVED가 아니어도 조회한다(상태별 분기로 쿼리 수가 달라지지 않게).
- **동시성 테스트는 대여 10건에서 반복한다.** 한 번의 경쟁은 겹치지 않을 수 있어서다. 서비스에서 `findByIdForUpdate`를 `findById`로 바꾸면 두 테스트 모두 실패하는 것을 확인했다(확인 후 원복).
