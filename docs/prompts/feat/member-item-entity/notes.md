# feat/member-item-entity 결정 기록

B 담당(REQ-001~011) 범위 중 대여 작업이 기대는 엔티티·마이그레이션만 A가 대신 만든다. API·서비스·검증 로직은 B 몫.

## 마이그레이션
- **V1 회원(members·regions·member_regions), V2 물품(categories·items·item_images·item_available_periods), V3 카테고리 시드.** develop에 마이그레이션이 하나도 없어 V1부터. 시드를 따로 둔 것은 스키마와 데이터를 나눠 보려고(conventions.md 예시 `V2__insert_categories`와 같은 방식).
- **erd.md에 길이가 없던 VARCHAR 확정**:
  - 상태·종류(provider·role·status·region_range·item_condition): 30 — conventions.md §4 "상태·종류 VARCHAR(30)".
  - regions.code 10(행정안전부 행정동 코드 10자리), name 30, sido 20, sigungu 30 — 가장 긴 이름("강원특별자치도", "창원시 마산합포구")에 여유를 둔 값. 적재(REQ-004) 때 넘치는 행이 있으면 새 마이그레이션으로 늘린다.
  - categories.code·name 30 — 시드 최장이 code 9자, name 8자.
  - items.address 200 — 카카오 도로명 주소 + 건물명이 보통 50자 안팎이라 4배 여유. 상세 주소를 붙여도 넘지 않게.
  - item_images.url·thumbnail_url 500 — profile_image_url과 같게(S3 키 + 도메인).
- **item_images.thumbnail_url은 NOT NULL.** REQ-006이 모든 이미지에 썸네일을 만든다.
- **categories.parent_id에도 FK(자기 참조)를 건다.** 미사용 컬럼이지만 나중에 쓸 때 정합성이 깨지지 않게(conventions.md "FK 제약은 건다").
- **FK 컬럼의 단독 인덱스는 따로 만들지 않는다.** owner_id·category_id·item_id(가능 기간)·member_id는 복합 인덱스/UK의 첫 컬럼이 대신하고, 나머지(region_id, item_images.item_id 등)는 MySQL이 FK 제약에 맞춰 자동 생성한다.

## 엔티티
- **FK는 모두 `Long xxxId` 필드, 연관관계 매핑 없음 — Item ↔ ItemImage·ItemAvailablePeriod도 마찬가지.** 이유:
  1) 대여 쪽은 가능 기간만 날짜 조건으로 읽는다(REQ-012 캘린더, REQ-013 포함 검사). 물품을 거치지 않고 `itemId`로 바로 조회하는 게 단순하다.
  2) `@OneToMany` + cascade/orphanRemoval을 지금 정하면 수정 API(REQ-007: 가능 기간 교체를 락 안에서 검사·저장, 이미지 유지 목록 + 새 파일)의 설계를 B 대신 미리 정하게 된다.
  3) 팀 QueryDSL 예시(`item.categoryId`)와 방식이 같다.
  B가 REQ-006·007에서 컬렉션 매핑이 낫다고 판단하면 바꿔도 스키마는 그대로다.
- **도메인 메서드는 대여 검증에 쓸 판단 3개만**: `MemberRegion.isVerified(now, validDays)`, `ItemAvailablePeriod.isCovering(start, end)`, `Item.isOwnedBy(memberId)`. 이름은 conventions.md의 boolean `is` 접두사 규칙에 맞췄다(`contains` 대신 `isCovering`).
- **인증 유효 경계: `verifiedAt + 30일`이 되는 시각부터 만료**(erd.md `> now`). 29일 유효, 정확히 30일 만료, 31일 만료.
- **인증 처리(`verify`) 메서드는 만들지 않았다.** REQ-004(B) 범위라서. 테스트·픽스처는 `ReflectionTestUtils`로 `verifiedAt`을 넣고, B가 메서드를 만들면 픽스처를 그 메서드로 바꾼다.
- **신뢰 점수 초기값은 `PolicyProperties.trust.initialScore`(50.0)를 `Member.createLocal`에 넘긴다.** DB 기본값(50.0)만 두면 저장 직후 엔티티 필드가 null이고, 엔티티에 50을 적으면 "정책 수치 하드코딩 금지"에 걸려서. DB 기본값은 SQL로 직접 넣는 경우(더미 적재)를 위해 그대로 둔다.
- **카테고리는 팩토리 없음.** 시드로만 생기고 관리 기능은 3순위(REQ-057).
- **회원 팩토리는 `createLocal`만.** 카카오(REQ-041)는 3순위.

## 테스트
- **`MemberItemFixture`(`support/`)를 `IntegrationTestSupport`의 `@Import`에 넣고 `fixture` 필드로 노출.** 하위 클래스마다 `@Import`하면 컨텍스트가 갈라지므로 기반 클래스에 한 번. 이메일·닉네임·행정동 코드는 JVM 전역 번호로 만들어 UK 충돌이 없게 했다.
- **시드가 정리 뒤에도 남는지는 `IntegrationTestSupportTest`에서 검사.** `DataCleaner`가 패키지 전용이라 같은 패키지에서만 직접 호출할 수 있어서.
