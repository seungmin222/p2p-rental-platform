# feat/rental-return 결정 기록

REQ-016 1순위 전이 마무리 + 017 인계 + 018 반납 요청·상태 확인·자동 확인 배치. 019 빌린 내역은 변경 파일이 60개를 넘어 `feat/rental-history`로 분리했다(이 PR 머지 후 develop에서 새 브랜치).

## 범위
- **016은 이번에 1순위 전이를 완성한다**(handover·requestReturn·confirmNormal·markAbnormal·complete). markOverdue(023)·reportLost(018 2순위)·extend(038)·confirmPayment·expire(024)는 각 2순위 브랜치에서 추가한다.
- **인계 리마인더·인계 없음 자동 취소(2순위), 미반납 신고 API, 연체료, 보증금·정산, AI 비교 큐, 알림을 만들지 않는다.** 리스너 없는 이벤트는 쓰임이 정해질 때(REQ-032) 만든다. 자동 확인 배치 조건의 `lost_reported = false`는 미반납 신고가 생겨도 조건이 바뀌지 않게 지금 넣어 둔다.
- **반납 요청은 overdue_fee를 건드리지 않는다.** 연체료 확정은 REQ-023(`feat/rental-overdue`)에서 `requestReturn`에 붙인다.

## 전이
- **전이 메서드는 상태 가드만 보고 `now`만 받는다.** 권한(당사자·제공자 전용)은 서비스가 검사하고, 이력은 서비스가 같은 트랜잭션에 저장한다(cancel과 같음: 대리 접수처럼 changedBy를 엔티티가 알 수 없음). rental.md의 `handover(by)` 표기와 달리 by를 받지 않는다.
- **ABNORMAL은 1순위에서 `markAbnormal()`(RETURNED 가드만) → `complete()`, 이력은 RETURNED→COMPLETED 1건(reason `RETURN_CONFIRM_ABNORMAL`).** REQ-049 브랜치에서 markAbnormal이 RETURNED 유지 + depositStatus HELD + RETURNED→RETURNED 이력으로 바뀌고 complete는 차감 확정 때 부른다.
- **`confirmNormal`은 1순위에서 `complete`와 같다.** REQ-025에서 보증금 반환 뒤 완료로 바뀐다.
- **이력 reason은 영문 코드 상수**(`RentalStatusHistory.REASON_*`): `HANDOVER`, `RETURN_REQUEST`(대리 접수도 같음, changedBy가 제공자), `RETURN_CONFIRM_NORMAL`, `RETURN_CONFIRM_ABNORMAL`, `RETURN_AUTO_CONFIRM_BATCH`(changedBy null, rental.md의 `OVERDUE_BATCH` 형식).

## item_inspections
- **V6, ai_result는 빼고 REQ-040에서 컬럼 추가.** 2순위 컬럼이라서다(erd.md에 표시).
- **사진은 URL 배열**(item_images·StoredFile 규칙과 같음). 원본만 저장하고 썸네일은 버린다(컬럼이 image_urls 하나).
- **JSON 컬럼은 `@JdbcTypeCode(SqlTypes.JSON)`**(프로젝트 첫 JSON 매핑). Hibernate 7.4가 classpath의 Jackson 2로 직렬화한다. 체크리스트는 enum 키 맵이고, 다시 읽어도 enum 키로 돌아오는지 통합 테스트로 고정했다.
- **UK(rental_id, phase)는 무결성 보호용이다.** 같은 (rental_id, phase) 두 번 저장 → DataIntegrityViolationException을 리포지토리 테스트로 고정했다. 동시 요청은 UK까지 가지 않게 한다(아래 동시성).
