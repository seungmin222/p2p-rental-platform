# feat/rental-entity 결정 기록

rentals·rental_status_histories 엔티티·마이그레이션·`ACTIVE_FOR_PERIOD` 상수만 먼저 머지한다. 예약·전이 로직은 REQ-012~019 브랜치 몫.

## 마이그레이션
- **V4 사용.** feat/member-item-entity(#8) 머지 후 develop 최신이 V3(카테고리 시드)였다.
- **owner_id를 넣는다(NOT NULL, FK→members).** erd.md가 허용한 비정규화(물품 소유자는 바뀌지 않음). 빌려준 내역·제공자 권한 확인(인계·상태 확인)을 items 조인 없이 하려고. 생성 시 items.owner_id에서 1회 복사하고 이후 갱신하지 않는다.
- **인덱스 `(owner_id, status)` 추가.** 빌려준 내역 조회용으로 `(renter_id, status)`와 짝을 맞췄다. erd의 나머지 5개는 그대로. FK 단독 인덱스는 따로 만들지 않는다(선행 브랜치와 같은 이유 — 복합 인덱스 첫 컬럼이 대신하고 canceled_by·changed_by는 MySQL이 자동 생성).
- **FK 이름(members 세 번 참조)은 역할명을 붙인다** — conventions §4에 규칙으로 올림.
- **VARCHAR 길이**: 상태·종류(status·cancel_type·cancel_reason·from/to_status) 30(conventions §4). cancel_reason_text 200(`cancel.reason-text.max`). rental_status_histories.reason 255 — 배치명(ShedLock 이름 수준, 수십 자)이나 취소 사유(코드 + 텍스트 200자)를 담을 수 있게. NULL 허용(인계처럼 사유 없는 전이).

## 엔티티
- **FK는 `Long xxxId` 필드만, 연관관계 매핑 없음.** 선행 브랜치(Item·MemberRegion)와 같은 방식. 겹침 검사·배치 쿼리가 itemId·status 조건만으로 조회하고, 다른 도메인 엔티티를 rental이 들고 있지 않게(다른 도메인은 Service로 접근하는 의존 규칙).
- **`@Version`을 기본 경로에서도 켠다.** JPA는 경로별로 @Version을 끄고 켤 수 없고, 동시성 실험의 낙관적 락 전략이 이 필드를 쓴다. 기본 경로(분산 락)에서도 같은 대여를 사용자 취소와 배치(결제 만료·자동 취소)가 동시에 바꾸는 갱신 손실을 막아 준다. 필드는 `Long`이라 Spring Data가 version null로 새 엔티티를 판단한다.
- **전이 메서드(handover·cancel 등)와 `RENTAL_INVALID_TRANSITION`은 미룬다.** 각 전이의 가드·기록 필드(cancelType·cancelFee 등)가 REQ-015~018에서 정해지므로, 지금 만들면 그 설계를 미리 확정하게 된다. 이번에는 생성 팩토리 2개(`createPendingPayment`·`createReserved`)와 판정 `isOccupyingPeriod`만.
- **엔티티는 불변식만, 입력 검증은 서비스.** 팩토리는 startDate·endDate null, endDate < startDate, (결제 대기) paymentExpiresAt null이면 예외를 던진다. 이런 값은 서비스가 검증을 빠뜨린 프로그래밍 오류이고, 그대로 두면 0 이하 대여료나 만료 배치(`payment_expires_at <= now`)가 끝내 잡지 못하는 결제 대기 같은 깨진 행이 저장되기 때문이다. 오늘 이후 시작·일수 1~30 같은 사용자 입력 규칙(400, ErrorCode)은 정책값·Clock이 필요하므로 REQ-013 서비스가 한다. `isOccupyingPeriod`의 paymentExpiresAt null 처리는 DB에서 읽은 비정상 행을 대비해 남겨 둔다(점유 안 함 쪽이 안전).
- **rentalFee 일수는 시작일·종료일을 모두 포함한다(같은 날 = 1일).** `DAYS.between + 1`. 1~30일 범위 검증은 서비스(REQ-013). 곱셈은 `Math.multiplyExact`로 넘침 시 조용히 틀리지 않게(정책 최대 1,000,000 × 30은 int 안).
- **만료 경계: `paymentExpiresAt > now`일 때만 점유, 정확히 같은 시각은 만료.** 결제 만료 배치 조건(`payment_expires_at <= now`)과 정확히 반대가 되도록. 15분은 서비스가 정책값으로 계산해 넘긴다.

## 테스트
- **`RentalFixture`(`support/`)를 `IntegrationTestSupport`의 `@Import`에 추가하고 `rentalFixture` 필드로 노출.** B의 피드 배지(009)·기간 검색(010) 테스트용. 전이 메서드가 없어 상태는 `ReflectionTestUtils`로 넣고(생기면 바꾼다), PENDING_PAYMENT는 만료 시각이 필요해 `createPendingPayment`로 분리했다. 시각 컬럼(handedOverAt 등)은 채우지 않는다. 새 테이블은 시드가 아니라 `DataCleaner.EXCLUDED_TABLES`에 넣지 않았다.
