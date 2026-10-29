# feat/rental-history 결정 기록

REQ-019 빌린 내역. `feat/rental-return`에서 분리했다(그쪽 notes "019" 항목의 canWriteReview 결정을 따른다). 후기 작성 여부·연체료 표시·결제 내역·제공자 목록(020)·마이그레이션은 범위 밖.

## 요청
- **size 파라미터를 받지 않고 `rental-history.page`(20) 고정.** architecture.md 페이지네이션은 "size 기본 20"이지만, 빌린 내역은 화면 하나(SCR-12)만 쓰고 크기를 바꿀 이유가 없어 상한 검사를 따로 두지 않으려고 받지 않는다.
- **`role`은 필수이고 `renter`만 허용**(`@Pattern`). 경로는 api.md 그대로 두고 제공자 쪽은 REQ-020이 따로 맡는다.
- **파라미터는 `@ParameterObject @Valid @ModelAttribute` record, 기본값(tab ONGOING, page 0)은 compact constructor에서 채운다.** 잘못된 tab("ALL", "ongoing")은 타입 변환 실패가 BindException으로 와서 400 `COMMON_INVALID_INPUT`(field tab)이 된다(통합 테스트로 고정).
- **잘못된 tab의 `reason`에 스프링 변환 메시지(내부 클래스 이름 포함)가 그대로 나간다.** GlobalExceptionHandler.handleBind가 typeMismatch FieldError의 기본 메시지를 쓰기 때문이다. 공통 코드라 이 브랜치에서 고치지 않고 PR에서 확인받는다.

## 쿼리
- **QueryDSL은 이번에 처음 도입한다**: `QuerydslConfig`의 `JPAQueryFactory(EntityManager)` 빈 하나. OpenFeign 7.x 문서에 Spring 등록 예시가 없어 7.7 jar의 생성자(`com.querydsl.jpa.impl.JPAQueryFactory(EntityManager)`)를 확인했다.
- **목록은 `RentalHistoryRow` projection(필요한 9개 컬럼)으로 읽고 물품·닉네임은 다른 도메인 Service로 일괄 조회한다.** 쿼리 수는 항목 수와 상관없이 대여 1 + 물품 1 + 대표 이미지 1 + 닉네임 1 (+ count 1)이다. 빈 페이지는 일괄 조회 메서드가 빈 입력에서 쿼리를 내지 않아 1개.
- **count에는 정렬을 넣지 않고 `PageableExecutionUtils.getPage`로 필요할 때만 실행한다.** 21건의 page 1(마지막 쪽)은 count를 생략해 4개다(테스트로 고정).
- **대표 이미지 동률은 id가 작은 것.** `item_images`에 (item_id, sort_order) 유니크 제약이 없어 sort_order 최소 행이 여럿일 수 있다. 쿼리를 id 순으로 받아 물품별 첫 행을 쓴다. 기존 `getSummary`(findFirstByItemIdOrderBySortOrder)는 동률일 때 순서를 정하지 않으며 이번에 바꾸지 않았다.
- **`getSummaries`·`getNicknames`는 없는 ID를 Map에서 뺀다.** 없는 경우의 처리는 호출하는 쪽이 정한다. 빌린 내역은 FK로 참조한 행이라 없으면 `IllegalStateException`(기존 `getNickname`과 같은 판단).

## 응답
- **`PageResponse<T>`를 global/response에 범용으로 두었다**(`of(Page)`, `of(Page, mapper)`). 프로젝트의 첫 페이지 응답이다.
- **항목의 물품·제공자는 `RentalDetailResponse.RentalItem`·`RentalCounterpart`를 재사용한다.** 모양이 같고, springdoc이 단순 클래스명으로 스키마를 만들어 같은 이름의 새 record를 두면 서로 덮어쓴다. 제공자 필드 이름은 `owner`.
- **canWriteReview 판정은 `Rental.canWriteReview(status, completedAt, now, deadlineDays)` static 하나에 두고, 인스턴스 메서드 `canWriteReview(now, deadlineDays)`가 위임한다.** 목록은 엔티티 대신 projection을 읽어 static을 쓰고, REQ-034 후기 작성 가드는 엔티티로 부른다. COMPLETED인데 completedAt이 null인 깨진 행은 false(`isOccupyingPeriod`와 같은 쪽). now는 요청당 한 번만 읽는다.
- **결제 시간이 지난 PENDING_PAYMENT도 UPCOMING에 상태 그대로 보인다.** 만료 전환은 REQ-024 배치 몫이고, 목록이 상태를 다르게 보여 주면 상세와 어긋난다.
