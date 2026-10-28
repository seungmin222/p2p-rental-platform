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

## 수수료·정책 키
- **`cancel-fee.rate-percent`(30)·`cancel-fee.free-until-days-before`(3)를 policies.md §2 표 아래에 추가**, 코드에 30·3을 쓰지 않는다. 계산은 long으로 하고 원 미만 절사.
- **policies.md 키는 PolicyProperties 바인딩 형태에 맞춘다.** `cancel.reason-text.max`를 `cancel.reason-text-max`로 바꿨다(PR1의 `rental.lock-wait`과 같은 이유: 점 하나마다 중첩 record가 생긴다).
- **사유 검증은 클래스 수준 `@ValidCancelReason`**(PolicyProperties 주입). 길이 상한이 설정값이라 `@Size`에 넣을 수 없다. 오류 필드는 reasonText.

## 테스트
- **상세 조회 쿼리 수는 Hibernate Statistics로 단언한다.** test 프로필에서 `generate_statistics`를 켜고 세션 통계 로그는 WARN으로 낮췄다. 요청 직전에 `Statistics.clear()`를 불러 픽스처 저장이 섞이지 않게 하고 MockMvc 호출 하나의 `prepareStatementCount`만 본다. PR1 기준 4개.
