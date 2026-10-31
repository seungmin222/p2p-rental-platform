# backend/CLAUDE.md — Spring Boot 규칙

루트 `CLAUDE.md`의 문서 지도를 따라 작업에 필요한 문서를 먼저 읽는다(`docs/` 전체나 `docs/reference/`를 미리 읽지 않는다). 이 파일은 백엔드 코드 작성 규칙만 다룬다.

## 스택
Java 21 · **Spring Boot 4.1.x** · Gradle · Spring Data JPA + **QueryDSL**(OpenFeign 포크) · Spring Security(JWT) · Flyway · Redisson · Spring AMQP / STOMP relay · Spring Scheduling + **ShedLock** · **springdoc-openapi** · JUnit5 + Testcontainers
(굵게 표시한 것은 팀 확정 사항)

**Spring Boot 4 주의**: 학습 자료·기억 속 예제는 대부분 Boot 3 기준이다. Boot 4는 스타터·자동 설정 패키지가 모듈별로 나뉘고 Jackson 3, Hibernate 7을 쓰는 등 import 경로와 설정 키가 다를 수 있다. 의존성·설정을 추가할 때는 기억에 의존하지 말고 해당 라이브러리의 Boot 4 호환 버전과 공식 문서를 확인한다(예: springdoc 3.x, QueryDSL OpenFeign 7.x, Redisson은 Boot 버전에 맞는 `redisson-spring-data-4x` 모듈).

## 패키지 구조 (도메인 우선)

```
backend/src/main/java/com/team7/rental/   ← 기본 패키지 (팀 확정 시 변경)
├── global/
│   ├── config/        Security, WebSocket, Redis, RabbitMQ, S3, OpenApiConfig, Async
│   ├── security/      JwtProvider, JwtFilter, StompAuthInterceptor, @LoginMember
│   ├── response/      ApiResponse<T>, PageResponse, CursorResponse
│   ├── error/         ErrorCode(enum), BusinessException, GlobalExceptionHandler, @ApiErrorCodes
│   ├── entity/        BaseTimeEntity
│   ├── lock/          RentalLockStrategy + 구현체들 (Redisson / Pessimistic / Optimistic)
│   ├── storage/       FileStorage 인터페이스 + S3FileStorage(prod) / LocalFileStorage(local·test, 디스크 저장), ImageProcessor(검증·EXIF 회전·재인코딩·썸네일)
│   └── policy/        PolicyProperties (@ConfigurationProperties "app.policy")
├── member/            region 포함
├── item/              category, wish, viewlog 포함
├── rental/            inspection, extension, history
├── payment/           settlement, deduction, PaymentGateway(토스)
├── chat/
├── notification/
├── review/            trust score 포함
├── ai/                AiClient, compare worker
└── admin/

backend/src/main/resources/
├── application*.yml
└── db/migration/      Flyway SQL (V1__..., V2__...)
```

각 도메인 내부 (모든 도메인 동일):
```
rental/
├── controller/     RentalController — 요청 받기, 검증, 서비스 호출, Swagger 어노테이션
├── service/        RentalService(쓰기), RentalQueryService(읽기), 이벤트 리스너
├── repository/     RentalRepository (Spring Data JPA)
│                   RentalSearchRepository + RentalSearchRepositoryImpl (QueryDSL, 동적 조회가 있을 때만)
├── entity/         Rental, RentalStatus(Enum), RentalStatusHistory
├── dto/            RentalCreateRequest, RentalResponse … (record)
├── event/          도메인 이벤트 (RentalReturnedEvent 등) — 필요할 때만
├── client/         외부 연동 구현 (PaymentGateway, AiClient 등) — 필요할 때만
└── batch/          스케줄러 잡 — 필요할 때만
```
`event/`, `client/`, `batch/`는 필요한 도메인에만 만든다. 빈 폴더를 미리 만들지 않는다.

## 의존 규칙
- `controller → service → repository / entity`. entity는 다른 계층을 모른다.
- **다른 도메인의 Repository를 직접 호출하지 않는다.** 다른 도메인의 Service(또는 조회용 Facade)를 통해서만. 예: rental이 item 소유자를 알아야 하면 `ItemQueryService.getOwnerId(itemId)`.
- 도메인 간 후속 동작(알림, AI 큐, 정산 생성)은 **도메인 이벤트 + `@TransactionalEventListener(phase = AFTER_COMMIT)`**로. 직접 호출로 트랜잭션을 묶지 않는다.
- 순환 의존이 생기면 이벤트로 끊는다.

## 코딩 규칙
- 포맷: **네이버 핵데이 Java 컨벤션**(탭 4, 120자). 손으로 맞추지 말고 커밋 전 `./gradlew spotlessApply`. 이름 규칙(클래스·DTO·메서드 동사·테스트)은 `docs/conventions.md` §2, DB 이름·제약·인덱스는 §4.
- 컨트롤러는 얇게: 검증(`@Valid`) → 서비스 호출 → `ApiResponse.ok(...)`. 비즈니스 로직 금지.
- DTO는 `record`. 엔티티를 응답으로 직접 반환하지 않는다.
- 엔티티: `@Setter` 금지. 상태 변경은 의미 있는 메서드(`rental.handover(now)`)로. 생성은 정적 팩토리 또는 `@Builder(access = PRIVATE)` + 팩토리.
- `@Enumerated(EnumType.STRING)` 필수. Enum 코드는 `docs/state-machines.md`와 정확히 일치.
- 연관관계: 기본 `LAZY`. 조회 성능 문제는 fetch join / DTO projection으로 해결. 양방향은 꼭 필요할 때만.
- 정책 수치는 하드코딩 금지 → `PolicyProperties` 주입.
- 예외: `throw new BusinessException(ErrorCode.RENTAL_PERIOD_CONFLICT)`. ErrorCode는 HTTP 상태 + code + 기본 메시지를 가진다.
- 현재 로그인 회원: `@LoginMember Long memberId` 아규먼트 리졸버.
- 시간: `Clock` 빈을 주입해 `LocalDate.now(clock)` 사용 → 테스트에서 날짜 고정 가능. `LocalDate.now()` 직접 호출 금지.
- 금액: `int`(원). 계산 결과가 커질 수 있는 합계는 `long`.
- 외부 API(토스, 카카오 로컬, Claude, S3)는 인터페이스 뒤에 숨기고 `client/`(공통은 `global/`)에 구현. 테스트용 Fake 구현 제공.
- 로그: 개인정보(이메일, 토큰, 카드정보) 로그 금지. PG 응답은 필요한 필드만.

## 쿼리 작성 방식 (QueryDSL 사용 범위)

| 쿼리 종류 | 방식 | 해당 예 |
| --- | --- | --- |
| 필터·정렬 조건이 **선택적으로 조합**되는 목록 조회 | **QueryDSL** | 검색(010), 피드(009), 빌린 내역 탭(019), 내 물품 관리(020), 결제 내역(033), 관리자 목록(057) |
| 조건이 고정된 단순 조회 | Spring Data 메서드 이름 | `findByEmail`, `existsByNickname`, `findAllByItemIdOrderByStartDate` |
| 조건이 고정된 복잡 조회, 성능 실험 대상 | `@Query` (JPQL 또는 nativeQuery) | 기간 겹침 검사, 연체·만료 배치 대상 조회 |
| 쓰기·벌크 수정 | 엔티티 메서드 + 더티 체킹 / `@Modifying @Query` | 상태 전이, `wish_count = wish_count + 1`, 연체 일괄 전환 |

규칙:
- **QueryDSL은 조회(select) 전용.** `update`/`delete`를 QueryDSL로 쓰지 않는다 (영속성 컨텍스트 우회로 상태 머신 규칙이 깨지기 쉬움).
- 구조: 커스텀 리포지토리 패턴. `ItemSearchRepository`(인터페이스) + `ItemSearchRepositoryImpl`(`JPAQueryFactory` 주입), `ItemRepository extends JpaRepository<Item, Long>, ItemSearchRepository`.
- 검색 조건은 `ItemSearchCondition` record로 받고, 조건마다 `BooleanExpression` 반환 메서드(null이면 조건 생략)로 만든다:
  ```java
  private BooleanExpression categoryEq(Long categoryId) {
      return categoryId == null ? null : item.categoryId.eq(categoryId);
  }
  ```
- 목록 응답은 엔티티 대신 `Projections.constructor(...)` 또는 `@QueryProjection` DTO로 필요한 컬럼만 조회한다.
- 페이징: 검색은 `offset/limit` + 별도 count 쿼리(`PageableExecutionUtils.getPage`로 불필요한 count 생략), 피드는 커서(`bumpedAt < :t OR (bumpedAt = :t AND id < :id)`) — offset 사용 금지.
- 거리 계산 같은 DB 함수는 `Expressions.numberTemplate(Double.class, "ST_Distance_Sphere(...)", ...)`로. 이것만으로 표현이 어려우면 해당 쿼리는 nativeQuery로 분리해도 된다(이유를 주석에).
- `JPAQueryFactory`는 `global/config/QuerydslConfig`에 빈 1개로 등록.
- Q클래스는 빌드 시 생성(`build/generated`), **커밋하지 않는다**.
- OpenFeign 포크(`io.github.openfeign.querydsl`)를 쓴다. 원조(`com.querydsl`)와 섞지 않는다. 버전과 Gradle 설정은 도입 시점의 공식 README를 확인한다.

## 배치 단일 실행 (ShedLock)
- 모든 `@Scheduled` 메서드에 `@SchedulerLock(name = "도메인.배치명", lockAtMostFor = "...", lockAtLeastFor = "...")`. 이름은 배치마다 고유.
- 락 저장소: Redis(이미 사용 중) — `shedlock-provider-redis-spring`. JDBC로 바꾸면 `shedlock` 테이블 마이그레이션 추가.
- 설정은 `global/config/SchedulingConfig`(`defaultLockAtMostFor = 10m`). 락 키는 `job-lock:{app.shedlock.environment}:{name}`이고 환경 값은 프로필 파일마다 다르다(local·test·prod).
- `lockAtMostFor`는 배치가 정상적으로 걸리는 최대 시간보다 넉넉히(서버가 죽어도 락이 풀리도록), `lockAtLeastFor`는 서버 간 시계 차이를 흡수할 만큼 짧게(예: 30s).
- ShedLock은 "같은 회차를 두 서버가 동시에 돌리는 것"만 막는다. **배치 자체의 멱등성은 별도로 보장**해야 한다(아래 "배치" 참고).

## API 문서화 (springdoc-openapi / Swagger)

컨트롤러와 DTO가 곧 API 명세다. Swagger UI(`/swagger-ui.html`)와 `docs/openapi.json`은 여기서 자동 생성되므로, **명세를 고치려면 코드를 고친다.**

### 필수 어노테이션
- 컨트롤러: `@Tag(name = "Rental", description = "예약·대여·반납")` — 태그명은 도메인 문서 이름과 맞춘다(Member, Item, Rental, Payment, Chat, Notification, Review, Admin).
- 메서드: `@Operation(summary = "대여 예약 요청 (REQ-013)")` — **summary 끝에 REQ ID**를 붙여 명세와 추적되게 한다. 인증이 필요한 API는 `@SecurityRequirement(name = "bearerAuth")`.
- 에러: 이 API가 낼 수 있는 에러 코드를 `@ApiErrorCodes({RENTAL_PERIOD_CONFLICT, RENTAL_OWN_ITEM, ...})`(공통 커스텀 어노테이션 + `OperationCustomizer`)로 선언 → Swagger 응답 예시에 code·status·message가 표시된다. 도메인 문서의 "예외" 항목과 일치해야 한다.
  - 경로·쿼리 파라미터에 타입 변환이 있는 API(`@PathVariable Long id`, `YearMonth month` 등)는 형식 오류 400이 나므로 `COMMON_INVALID_INPUT`도 선언한다.
- DTO(record) 필드: `@Schema(description = "대여 시작일", example = "2026-10-03")`. Enum 필드는 허용 값이 자동 노출되므로 설명만. 검증 어노테이션(`@NotNull`, `@Size`, `@Min/@Max`)은 Swagger에도 반영되니 빠뜨리지 않는다.
- 응답 래퍼: `ApiResponse<RentalResponse>`처럼 제네릭 타입을 그대로 반환해야 Swagger에 `data` 내부 구조가 나온다. `ApiResponse<?>`, `Object`, `Map` 반환 금지.
- multipart: `@RequestPart` + `consumes = MULTIPART_FORM_DATA_VALUE`를 명시해야 Swagger에서 파일 업로드 테스트가 된다.

### 설정
- `OpenApiConfig`: 제목·버전, JWT `bearerAuth` SecurityScheme, 서버 URL.
- `application-prod.yml`: `springdoc.swagger-ui.enabled=false`, `springdoc.api-docs.enabled=false` (또는 관리자만 접근). local·dev에서는 공개.
- Security 설정에서 `/swagger-ui/**`, `/v3/api-docs/**`는 local 프로필에서만 permitAll.

### openapi.json 내보내기
- `springdoc-openapi-gradle-plugin` 사용, 출력 경로 `../docs/openapi.json`, `local` 프로필로 기동(로컬 인프라 필요).
- **API(경로·DTO·에러 코드)를 바꾼 PR에서는 `./gradlew generateOpenApiDocs`를 실행해 `docs/openapi.json`을 같이 커밋한다.** 손으로 편집하지 않는다.
- 아직 로직이 없어도 컨트롤러·DTO만 먼저 만들어 머지하면(서비스는 `UnsupportedOperationException`) 프론트가 openapi.json으로 선개발할 수 있다. 이때 `@Operation(description = "미구현: 응답 형태만 확정")`으로 표시.

## 트랜잭션·동시성
- 서비스 클래스 기본 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`.
- **외부 API 호출을 트랜잭션 안에 넣지 않는다** (PG, Claude, 카카오, S3 업로드). 필요하면 트랜잭션을 나눈다.
- 분산 락 + 트랜잭션: **락 획득 → 트랜잭션 시작 → 커밋 → 락 해제** 순서. 같은 클래스 내부 호출은 프록시가 안 걸리니 트랜잭션 메서드는 별도 빈에.
- 예약 생성, 연장, 대여 가능 기간 수정, 물품 삭제는 모두 `lock:item:{itemId}`를 사용.
- 카운터(wishCount, chatCount)는 `UPDATE ... SET x = x + 1` 벌크 쿼리로 원자적 갱신.

## 배치
- `@Scheduled` + `@SchedulerLock` (위 ShedLock 규칙). 인스턴스 2개 이상에서 중복 실행 금지.
- 배치는 **멱등**해야 한다(두 번 돌아도 결과 동일). 알림은 event_id 유니크로 중복 차단.
- 배치가 바꾼 상태 이력은 `changedBy = null`, `reason = 배치명`.

## DB 마이그레이션
- Flyway `src/main/resources/db/migration/V{n}__{설명}.sql`. 이미 머지된 마이그레이션 파일은 **수정하지 않는다** — 새 버전 추가.
- 시드: 카테고리, damage_policies는 **버전 마이그레이션(`V`)으로 한 번만** 넣는다. 반복 마이그레이션(`R__`)은 파일이 바뀔 때마다 다시 실행돼 관리자가 고친 값을 덮어쓰므로 쓰지 않는다. 행정동 마스터는 데이터가 커서 별도 CSV + 적재 마이그레이션(`V`)으로 넣는다(출처는 `policies.md` §2 동네 데이터).
- 번호 충돌 방지: 번호는 `docs/erd.md` "마이그레이션 번호" 표로 관리한다. develop의 표와 원격 작업 브랜치의 표를 둘 다 보고 가장 큰 번호 + 1을 고른 뒤, **브랜치 첫 커밋에서 표에 행(상태 "작업 중")을 넣고 바로 push**한다. PR을 만들 때 상태를 "머지"로 바꾼다(develop에 들어간 행은 곧 머지된 것이다)(`docs/git-workflow.md` §4).
- `ddl-auto`는 `validate`(local 포함). 엔티티와 스키마가 다르면 기동 실패하도록.
- 테이블·컬럼명은 `docs/erd.md` 기준. 바꾸면 erd.md도 수정. 새 이름은 `docs/conventions.md` §4(접미사, `uk_`/`idx_`/`fk_` 이름, 예약어 금지)를 따른다.

### 테이블·엔티티가 필요할 때
| 상황 | 할 일 |
| --- | --- |
| 이미 있음 (develop에 머지됨) | 그대로 사용. 다른 도메인 엔티티면 그 도메인의 Service를 통해 접근 |
| 없음, `erd.md`에 정의됨, **내 담당 도메인** | 엔티티 + 새 Flyway 파일 생성. 번호는 위 "번호 충돌 방지"대로 erd.md 표에서 정하고 첫 커밋에 표 행을 넣어 push |
| 있음, 컬럼 추가 필요 | 새 마이그레이션(`V{n}__add_{컬럼}_to_{테이블}.sql`). 머지된 파일 수정 금지. `erd.md` 갱신 |
| `erd.md`에 없는 테이블·컬럼 | **만들기 전에 멈추고** 이유와 설계안을 제시해 확인받은 뒤, `erd.md`에 먼저 추가하고 구현 |
| 없음, **다른 사람 담당 도메인** (예: rental 작업 중 `items` 미머지) | 내 브랜치에서 그 엔티티를 만들지 않는다. 사용자에게 알리고 선택지 제시: ① 담당자 브랜치 머지를 기다림 ② 담당자와 합의해 엔티티·마이그레이션만 먼저 작은 PR로 머지 ③ 테스트에서만 필요한 최소 데이터로 진행 |
| 현재 순위보다 높은 순위 테이블 (`erd.md`의 [2]·[3]) | 만들지 않는다. 필요해 보이면 알린다 |

도메인 담당은 루트 `CLAUDE.md`의 팀 표를 따른다.

## 테스트
- 단위 테스트(필수): 기간 겹침 판정, 대여료 계산, 상태 전이(허용/불허 전부), 차감 상한 계산, 동네 범위 계산, 신뢰 점수 산정, 정산 금액.
- 통합 테스트: `@SpringBootTest` + Testcontainers(MySQL, Redis, RabbitMQ). 이미지 버전은 `infra/docker-compose.local.yml`과 같게(`mysql:8.4`, `rabbitmq:4.x`). H2 사용 금지(MySQL 문법·락 동작이 다름).
- 통합 테스트 기반 클래스 `support/IntegrationTestSupport`를 상속한다(`@SpringBootTest` + test 프로필 + `MockMvc` 주입). 컨테이너는 JVM당 한 번 떠서 모든 클래스가 공유한다.
  - 시각 고정: `testClock.fixAt(LocalDateTime.of(...))`, `testClock.advance(Duration)`. `Clock`을 주입받는 모든 빈에 적용되고 테스트마다 실제 시각으로 돌아온다.
  - 데이터 정리: 테스트마다 끝나면 MySQL 전 테이블 TRUNCATE + Redis FLUSHDB(동시성 테스트 때문에 롤백 대신 이 방식). 시드 테이블은 `DataCleaner.EXCLUDED_TABLES`에 등록해 지워지지 않게 한다.
  - 하위 클래스에 `@MockitoBean`·`@TestPropertySource`·`@Import`를 붙이면 컨텍스트가 새로 만들어져 느려진다. 꼭 필요할 때만.
- 동시성 테스트: `ExecutorService` + `CountDownLatch`로 N개 동시 예약 → 성공 1건 검증.
- 외부 API는 Fake/WireMock. 토스 테스트 환경 실호출 테스트는 `@Tag("external")`로 분리해 기본 빌드에서 제외.
- 테스트 이름은 한국어 `@DisplayName` 허용: `"같은 기간 동시 예약 시 1건만 성공한다"`.
- 새 기능 PR은 해당 REQ의 "테스트 포인트"(도메인 문서)를 커버해야 한다.
- 작업 중에는 바꾼 부분의 테스트만 돌린다(`./gradlew test --tests '<클래스>'`). 전체 테스트는 PR 직전 한 번, 출력은 실패 요약만 본다(bash: `./gradlew test -q 2>&1 | tail -n 80; echo "exit=${PIPESTATUS[0]}"`). `-q`는 실패 개수만 보여 주므로, 실패한 테스트 이름과 내용은 `grep -B 1 -A 15 '<failure' build/test-results/test/TEST-*.xml`로 해당 테스트만 읽는다. 빌드 로그·테스트 리포트 HTML을 통째로 읽지 않는다.

## 설정·프로필
- `application.yml`(공통), `application-local.yml`, `application-test.yml`, `application-prod.yml`.
- 비밀값은 `${ENV_VAR}`로만 참조. 필요한 변수는 `infra/.env.example`에 추가.
- **로컬 `.env` 로딩**: Spring은 `.env` 파일을 자동으로 읽지 않는다. `application-local.yml`에 아래를 둬서 `infra/.env`를 설정값으로 가져온다(경로는 `backend/`에서 실행하는 기준).
  ```yaml
  spring:
    config:
      import: optional:file:../infra/.env[.properties]
  ```
  `.env`의 키(`DB_HOST` 등)가 그대로 속성이 되므로 `${DB_HOST}`로 참조할 수 있다. 운영은 실제 환경 변수로 주입한다.
- local 프로필: 파일 저장은 `LocalFileStorage`(디스크 `./uploads`, `/files/**`로 서빙), 메일은 Mailpit(`localhost:1025`, 웹 UI 8025). AWS 키 없이 전체 기능이 동작해야 한다.
- 쿠키: Refresh Token 쿠키 속성은 `policies.md` `auth.refresh-cookie`. local 프로필에서만 `Secure=false`.
- CORS: 기본 차단. 로컬은 Vite 프록시, 운영은 Nginx 같은 도메인 서빙이라 필요 없다(`docs/architecture.md` §4).
- `app.feature.payment-enabled`: 기본 false, prod는 REQ-049 머지 전까지 false (`docs/architecture.md` §9).

## 작업 체크리스트 (REQ 하나 구현할 때)
1. 도메인 문서의 해당 REQ, `state-machines.md`, `policies.md` 확인
2. 엔티티/마이그레이션 → 도메인 로직 + 단위 테스트 → 서비스 → 컨트롤러 + Swagger 어노테이션 → 통합 테스트
3. 바꾼 부분의 테스트 통과(`./gradlew test --tests '<클래스>'`). 전체 `./gradlew test`는 PR 직전 한 번
4. API가 바뀌었으면 `./gradlew generateOpenApiDocs`로 `docs/openapi.json` 재생성
5. `docs/api.md`(새 API 한 줄), `docs/erd.md`(스키마 변경), `docs/roadmap.md`(진행 상태) 갱신
