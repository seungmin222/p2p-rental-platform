## 요약
- open-issues #7 해결. 새 API 없음. `GET /api/rentals/{id}` 응답에 `previousRentalOverdue`(boolean) 추가, `PATCH /api/rentals/{id}/cancel`은 시작일 ≤ 오늘이고 앞 대여 연체 중인 대여자면 수수료 0으로 허용(시작일 전은 기존 수수료 표).
- 새 알림 타입 `PREVIOUS_RENTAL_OVERDUE`: 연체 배치(OverdueDetectJob, 00:05·매시)가 RESERVED·시작일 ≤ 오늘·같은 물품 앞 대여 연체인 건의 대여자에게만 1회.
- 판정: Rental.isPreviousOverdueExemptionCandidate(RESERVED·시작일 ≤ 오늘), RentalRepository.existsPreviousOverdue / findPreviousOverdueConflicts, Rental.validateCancelable(type, today, previousRentalOverdue), CancelFeeCalculator.calculate(..., previousRentalOverdue). 이력 reason `PREVIOUS_RENTAL_OVERDUE`.
- 테스트: PreviousRentalOverdueIntegrationTest(배치 알림·취소·환불·상세 필드). 마이그레이션 없음(notifications.type VARCHAR).
- 남은 일: 프론트 대여 상세에서 previousRentalOverdue면 시작일 뒤에도 취소 버튼 표시, 취소 확인 모달에서 수수료 재표시. REQ-062 구현 시 trust.md 확장 지점(면제 취소·연체발 NO_HANDOVER 제외) 반영.

## 결정
- 감지 시점은 연체 배치 같은 회차(전환 뒤). 시작일 전날 미리 알림은 새 정책 상수가 필요해 하지 않았다(사용자 결정).
- "앞 대여 연체" = 같은 물품의 다른 대여가 RENTING·OVERDUE이고 endDate < 오늘. 배치 전 RENTING도 넣는 것은 #55 점유 구간 정의와 맞추기 위함이다.
- 결제 대기 다음 예약은 알림 대상이 아니다. 원래 수수료 없이 취소되고, 결제가 승인되면 다음 회차에 잡힌다.
- 알림은 다음 예약자만. 제공자는 앞 대여의 OVERDUE를 이미 받는다(사용자 결정). 문구는 NotificationContentTemplate 임시 문구(#50).
- 면제는 새 CancelReason 없이 기존 단독 취소 API에서 서버가 판정한다. 판정은 대여 행 락 안의 조회이고 앞 대여의 반납과는 락을 나누지 않는다. 경합 시 어느 쪽 판정이든 허용 범위로 봤다.
- 사용자가 고른 사유는 rentals.cancel_reason에 남기고 이력 reason만 PREVIOUS_RENTAL_OVERDUE로 바꿨다. 062가 이 이력으로 면제 취소를 가려낸다.
- 새 전이는 없고, 기존 RESERVED → CANCELED 단독 취소의 가드에 예외를 더했다(사용자 승인). 제공자 단독 취소는 그대로다(동의 취소 사용).
- 인계 없음 자동 취소는 바꾸지 않았다. 대여자가 그 전에 직접 면제 취소할 수 있어서다.
- 면제 범위는 알림 대상과 같게 "RESERVED이고 시작일 ≤ 오늘"로 통일했다(리뷰 뒤 사용자 결정). 처음엔 취소 면제만 시작일 전에도 줬지만 알림·플래그·면제가 서로 다른 범위면 화면과 안내가 어긋난다. 조건은 Rental.isPreviousOverdueExemptionCandidate 한 곳에 두고 배치 쿼리만 JPQL에 같은 조건을 쓴다.
- previousRentalOverdue는 쿼리 수를 일정하게 두려고 상태와 상관없이 항상 조회하고, 값은 RESERVED·시작일 ≤ 오늘일 때만 쓴다. 양측 뷰에 같은 값을 주고, 취소 가능 여부는 대여자만이라 화면이 역할로 가른다.
- 면제 취소는 진행 중인 동의 취소 요청(REQUESTED)이 있어도 막지 않는다. 남은 요청은 기존 구조대로 수락 시 409 RENTAL_INVALID_TRANSITION, 상세에서 숨겨지고 만료 배치가 닫는다.
- 기존 OverdueDetectJobTest.skipsNotDue는 같은 물품에 지난 RESERVED와 OVERDUE를 함께 만들어 새 알림이 나간다. 그 테스트는 OVERDUE 알림만 보도록 좁혔다.

## 넘긴 것
- 반납·면제 취소 경합은 락 없이 둔다. 최악은 같은 순간 반납과 취소가 엇갈려 정책 표 범위 안의 30% 수수료가 붙거나 빠지는 정도라 락을 나누는 비용보다 작다고 봤다.
- 프론트(대여 상세): 취소 확인 모달에서 서버 수수료 재표시, previousRentalOverdue=true일 때 시작일 뒤에도 취소 버튼 노출은 프론트 브랜치 몫.
