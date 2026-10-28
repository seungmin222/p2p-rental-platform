# feat/rental-reserve 결정 기록

REQ-016(생성·상세) → 013/014 → 012. 취소(015)는 변경 파일이 60개를 넘어 `feat/rental-cancel`로 분리했다(PR1 머지 후 develop에서 새 브랜치).

## 범위
- **016은 이번에 생성(이력 from=null)과 상세 조회만.** 취소 전이는 rental-cancel, 인계·반납 전이는 017·018, 결제 승인·만료는 024 브랜치에서 엔티티에 추가한다. 그래서 roadmap 016은 ⬜로 둔다.
- **알림 이벤트·결제 만료 배치·rental_dates를 만들지 않는다.** 리스너 없는 이벤트는 쓰임이 정해질 때(REQ-032) 만든다. 결제 플래그 true 분기(PENDING_PAYMENT + now + payment.expire-minutes)만 기존 팩토리로 만들어 둔다. 결제 금액 0원이면 플래그와 상관없이 RESERVED.

## 다른 도메인 접근 (B 확인 필요)
- **rental은 item·member 리포지토리를 직접 부르지 않고 읽기 서비스를 최소로 만들었다**: `ItemQueryService`(예약 대상·가능 기간·요약), `MemberRegionQueryService.isPrimaryRegionVerified`, `MemberQueryService.getNickname`. 반환은 item·member 쪽 record라 엔티티가 rental로 넘어가지 않는다. B가 item-crud·member-region에서 같은 클래스를 키운다.
- **ErrorCode `ITEM_NOT_FOUND`·`REGION_NOT_VERIFIED`도 B 도메인 코드로 먼저 추가했다.** 같은 상황에 B가 다른 이름을 만들지 않게 PR에서 확인받는다.

## 물품 상태
- **HIDDEN·DELETED는 둘 다 404 `ITEM_NOT_FOUND`, HIDDEN이고 요청자가 소유자면 다음 검사(`RENTAL_OWN_ITEM` 400)로 넘긴다.** 상세 조회(REQ-011) "HIDDEN은 소유자만(그 외 404)"과 맞추려고. 409를 주면 숨긴 물품의 존재가 드러난다. rental.md의 "404/409" 중 409는 쓰지 않는다.
- **캘린더도 같은 규칙**: 비회원 허용 API라 `@LoginMember(required = false)`로 받아 소유자만 HIDDEN 물품 캘린더를 본다.

## 예약
- **락은 `RentalLockStrategy.executeWithItemLock(itemId, Supplier)`**, 기본 구현 `RedissonRentalLockStrategy`. 겹침 검사·INSERT·이력은 별도 빈 `RentalReservationTxService`(READ_COMMITTED)에 둬 커밋 후 락이 풀린다. 사전 검증(기간·물품·동네·가능 기간)은 락 밖에서 해 락 점유 시간을 줄인다.
- **점유 기간 조회를 `RentalOccupancyReader` 한 곳에 모았다.** 겹침 검사와 캘린더가 같이 쓰고, REQ-038 결제 대기 연장 기간도 여기에 합친다(주석 표시). 겹침 검사도 같은 목록 조회를 쓴다(존재 여부만 보는 별도 쿼리를 만들면 두 경로의 조건이 갈라질 수 있음).
- **일수 계산은 `Rental.countDays`를 공개해 재사용**(양 끝 포함). 기간 형식·오늘 이후·1~30일 위반은 모두 `RENTAL_INVALID_PERIOD` 400.
- **설정 키는 `app.policy.rental.lock-wait`**(policies.md `rental.lock.wait`). `wait`는 `Object.wait()`과 겹쳐 record 컴포넌트 이름으로 쓸 수 없어 중첩 record 대신 펼쳤다.

## 응답
- **예약 생성(201)도 대여 상세 응답을 돌려준다.** 1순위는 예약 후 바로 대여 상세로 이동한다(frontend/CLAUDE.md).
- **상세 필드는 SCR-08 스텝바·상태×역할 버튼에 필요한 만큼**: 상태, myRole, 물품 요약(삭제 여부 포함), 상대 닉네임, 기간·일수·금액, 결제 만료·전이 시각, 취소 정보. 인계·반납 사진, 결제, 연체료, 진행 중 동의 취소는 각 REQ 브랜치에서 붙인다.
- **403은 `RENTAL_NOT_PARTICIPANT`.** 공통 `AUTH_FORBIDDEN`보다 프론트가 원인을 구분하기 쉽다.
- **중첩 응답 record 이름에 도메인을 붙였다**(`RentalItem`, `RentalCounterpart`, `CalendarDay`). springdoc은 단순 클래스명으로 스키마를 만들어, `Day`·`ItemInfo` 같은 이름은 다른 도메인 DTO와 겹치면 서로 덮어쓴다.

## 테스트
- **결제 플래그 분기는 Mockito 단위 테스트**(`RentalReservationTxServiceTest`). 플래그만 바꾸려고 스프링 컨텍스트를 하나 더 띄우지 않는다.
- **락이 커밋 전에 풀리지 않는지(`RentalLockReleaseTest`)**: 이 클래스 전용 설정으로 watchdog을 0.5초로 줄이고, INSERT 뒤 커밋 전에 1.5초 잠드는 하위 클래스를 `@Primary`로 끼운다. A 예약 중 B가 같은 기간을 예약하면 B는 `RENTAL_PERIOD_CONFLICT`여야 한다(`RENTAL_LOCK_TIMEOUT`을 허용하면 잘못된 구현도 통과). 지연은 watchdog보다 충분히 길고 lock.wait(3초)보다 충분히 짧게 잡았다. 잠드는 곳이 트랜잭션 안인지 테스트에서 확인한다.
- 이 테스트가 잘못된 구현을 잡는지 확인했다: 구현에 임대 시간 0.5초를 넣으면 실패한다(확인 후 원복).
- **2개 인스턴스 동시성 검증은 락 실험 브랜치로 미룬다.** 이번에는 한 JVM의 100 스레드만.
- **`SecurityConfigSwaggerTest`의 `@WebMvcTest`에 빈 컨트롤러를 지정했다.** 지정하지 않으면 모든 컨트롤러를 올리는데, 처음 생긴 실제 컨트롤러(Rental)의 서비스 빈이 슬라이스에 없어 컨텍스트가 뜨지 않았다. 경로 공개 여부만 보는 테스트라 컨트롤러가 필요 없다.
