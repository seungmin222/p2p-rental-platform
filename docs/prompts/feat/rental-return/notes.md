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

## 인계 (017)
- **권한 오류: 당사자 아님 → `RENTAL_NOT_PARTICIPANT`, 대여자가 제공자 전용 API(인계·상태 확인) → 새 코드 `RENTAL_OWNER_ONLY`(403).** NOT_PARTICIPANT 메시지("당사자만")가 대여자에게 맞지 않는다.
- **검사 순서: request part 검증 400 → 404 → 403(당사자 → 제공자) → 409(상태 → TOO_EARLY) → 사진 400.** part 검증은 컨트롤러 인자 변환에서 먼저 일어난다. 사진은 처리 비용이 커서 대여 검사 뒤에 둔다.
- **`IMAGE_COUNT_INVALID`(400)를 이미지 묶음에 새로 뒀다.** 장수 상한이 설정값이라 @Size로 못 쓰고, BusinessException에 필드 상세가 없어 전용 코드로 구분한다. item-crud(`item.images`)에서도 같은 코드를 쓴다(B 확인).
- **multipart는 `request` part(application/json) + `images` 파일들.** 인계는 메모가 선택이라 request part도 생략 가능.
- **request part의 Content-Type이 없거나 text/plain이면 415 `COMMON_UNSUPPORTED_MEDIA_TYPE`.** 원래 HttpMediaTypeNotSupportedException이 500으로 나갔다. 공통 핸들러를 추가했고(JSON API에 다른 Content-Type을 보내도 같은 415), 프론트는 request part를 `application/json` Blob으로 보내야 한다. 필수 part가 없을 때(MissingServletRequestPartException)도 500이었어서 400 COMMON_INVALID_INPUT(필드 = part 이름)으로 바꿨다.
- **글자 수 검증은 클래스 수준 `@ValidInspectionText` + 요청 타입별 검증기.** 앞뒤 공백을 지우고 비면 null(cancel과 같음), codePointCount.

## 사진 업로드·정리 (item-crud가 아직 없어 이 방식을 제안, B 확인)
- **순서: 사전 검사(읽기 트랜잭션) → 장수 검사·전부 처리 → 업로드(트랜잭션 밖) → TxService. TxService가 예외를 던지면 업로드한 키를 지우고 다시 던진다.** 처리 단계에서 실패하면 업로드한 파일이 없다. 업로드 중간 실패도 그때까지 올린 키를 지운다. 삭제 실패는 WARN 로그만(원래 예외를 가리지 않게, 남는 파일은 DB가 참조하지 않는 고아).
- **사전 검사는 사진 처리를 줄이는 최적화일 뿐이다.** TxService 쓰기 메서드가 트랜잭션 안에서 대여를 다시 읽고 404·403·409를 전부 다시 검사한다.
- **디렉터리 상수 `StorageKeys.INSPECTIONS`**(StorageKeys를 public으로, 메서드는 package-private 그대로). 용도별 디렉터리 상수를 한곳에 모은다.

## 동시성
- **인계·반납 요청·상태 확인은 행 락을 잡지 않는다.** 사진 업로드는 트랜잭션 밖이라 쓰기 트랜잭션이 짧고, 업로드 중 행을 잠그면 같은 대여의 취소·조회가 그만큼 기다린다. Rental `@Version`이 막고 세 API의 `@ApiErrorCodes`에 `COMMON_CONCURRENT_UPDATE`를 선언한다(cancel notes의 결정).
- **전이 직후 `rentalRepository.flush()`로 버전 검사 UPDATE를 기록 INSERT보다 먼저 보낸다.** 늦은 쪽은 rentals 행 X 락을 기다렸다가 버전 충돌 → 409.
- **flush를 빼고 확인한 결과(원복함):** TxService 직접 테스트(두 트랜잭션이 같은 버전을 읽은 뒤 동시 진행)에서 늦은 쪽이 예상한 DataIntegrityViolationException이 아니라 **데드락(CannotAcquireLockException)**으로 끝났고, API 동시 인계 테스트는 500이 나왔다. item_inspections INSERT의 FK 검사가 rentals 행에 S 락을 잡은 채 UK를 기다리고, 앞선 쪽은 커밋 시 rentals UPDATE에 X 락이 필요해 서로 막는다. flush-first는 rentals X 락을 먼저 잡아 이 순환을 없앤다.
- **동시 인계 API 테스트는 "200 1건 + 409 1건(COMMON_CONCURRENT_UPDATE 또는 RENTAL_INVALID_TRANSITION)"으로 단언한다.** 사전 검사와 사진 처리 사이가 길어 늦은 쪽은 대부분 앞선 쪽 커밋 뒤에 다시 읽고 INVALID_TRANSITION을 받는다. 버전 충돌 경로 자체는 TxService 직접 테스트로 고정했다.
- **인계와 경쟁시킨 취소는 양측 동의 취소 수락이다.** 단독 취소는 시작일 전, 인계는 시작일부터라 같은 시각에 둘 다 가능한 경우가 없다. 수락은 행 락(FOR UPDATE)을 잡으므로 인계가 이기면 수락이 RENTING을 읽어 INVALID_TRANSITION, 수락이 이기면 인계가 버전 충돌이나 사전 검사에서 409.
