# 아키텍처 및 공통 규약

## 1. 기술 스택

| 영역 | 기술 | 비고 |
| --- | --- | --- |
| 프론트엔드 | React(SPA) + Vite + Tailwind CSS, JavaScript | 반응형(모바일 360px 우선, PC 1280px). 네이티브 앱 없음 |
| 프론트 서버 상태 | **TanStack Query** (확정) | API 데이터 캐싱·로딩/에러·무한 스크롤·뮤테이션 후 갱신 |
| 백엔드 | Java 21, **Spring Boot 4.1.x** (확정, Web MVC, Data JPA, Security, Validation, WebSocket, Scheduling) | Gradle. 3.5는 2026-06 OSS 지원 종료, 4.0은 2026-12 종료라 4.1 선택 |
| 동적 조회 | **QueryDSL** (확정, OpenFeign 포크 `io.github.openfeign.querydsl`) | 필터·정렬이 선택적으로 조합되는 **조회에만** 사용. 범위는 `backend/CLAUDE.md` |
| 배치 단일 실행 | **ShedLock** (확정, Redis 또는 JDBC 락 저장소) | 서버 2대 이상에서 `@Scheduled`가 한 번만 실행되게 |
| DB | MySQL 8.4 LTS | 주 저장소. 마이그레이션은 Flyway (8.0은 2026-04 지원 종료) |
| 캐시·락 | Redis + Redisson | 분산 락, Refresh Token, 조회수, 인기 검색어, 추천 캐시, 비밀번호 재설정 토큰. **Pub/Sub 용도로 쓰지 않는다** |
| 메시지 브로커 | RabbitMQ 4.x | STOMP 브로커 릴레이(채팅·알림), AI 비교 작업 큐 (3.13은 커뮤니티 지원 종료) |
| 파일 | AWS S3 (운영) / 로컬 디스크 (local 프로필) | `FileStorage` 인터페이스 뒤에 두고 프로필로 교체. 업로드 시 서버에서 가로 400px 썸네일 생성, DB에는 URL만. 로컬 개발에 AWS 키가 필요 없게 함 |
| 배포 | AWS EC2 + Docker Compose | Spring Boot 2개 이상 인스턴스로 실행 가능해야 함 |
| 외부 API | 토스페이먼츠(테스트 키, 카드만), 카카오맵 JS API, 카카오 로컬 API(좌표→행정동), Claude API(`claude-haiku-4-5`, 이미지 입력) | |
| 문서화 | **springdoc-openapi(Swagger)** (확정) | 필드 명세는 DTO 코드 → Swagger → `docs/openapi.json` |
| 테스트 | JUnit5, Testcontainers(MySQL·Redis·RabbitMQ), JMeter | |

> "(확정)" 표시가 없는 세부 선택(Java 21, Flyway, Testcontainers, 프론트의 Zustand·axios 등)은 기획서에 없는 **구현 제안**이다. 팀이 바꾸면 이 표를 수정한다. 라이브러리 버전은 프로젝트 생성 시점의 최신 안정 버전을 확인해 `build.gradle`/`package.json`에 고정한다.

## 2. 시스템 구성

```
[브라우저: React SPA] ──REST /api/** ──┐
        │   └──WebSocket /ws (STOMP)──┤
        └── 카카오맵 JS API            ▼
                         ┌─────────── Nginx (HTTPS, LB) ───────────┐
                         │  Spring Boot #1      Spring Boot #2     │
                         └──┬───────┬────────┬────────┬──────┬─────┘
                          MySQL   Redis   RabbitMQ    S3   외부 API
                                                         (토스·카카오 로컬·Claude)
```

- 서버는 **무상태(stateless)**. 세션 없음, 인증은 JWT. 어느 인스턴스로 요청이 가도 동작해야 한다.
- 서버 간 공유 상태는 반드시 MySQL/Redis/RabbitMQ를 거친다. JVM 메모리 캐시는 카테고리 목록처럼 변경이 드문 것만 허용(변경 시 무효화).
- 스케줄러(연체 감지, 결제 만료, 자동 상태 확인, 정산, 재시도, 조정 배치)는 인스턴스가 여러 개여도 **한 번만** 실행되게 한다 → 모든 `@Scheduled`에 ShedLock `@SchedulerLock` 필수.

## 3. 비동기·실시간

| 용도 | 경로 |
| --- | --- |
| 채팅 메시지 | 클라이언트 → STOMP `/pub/chat-rooms/{roomId}` → 서버(저장) → RabbitMQ 브로커 릴레이 → `/sub/chat-rooms/{roomId}` 구독자 |
| 알림 | DB `notifications`에 **먼저 저장** → 접속 중이면 `/user/queue/notifications`로 전달. 실시간 전달 실패 시 재전송하지 않음(목록 API로 확인) |
| AI 상태 비교 | 반납 요청 시 RabbitMQ 작업 큐에 적재 → 워커가 Claude API 호출 → 결과 저장 → 알림 |

STOMP 목적지 접두사(`/pub`, `/sub`)는 구현 제안이며 백엔드·프론트가 함께 정한다. 확정 시 `docs/api.md`에 기록.

## 4. API 공통 규약

### 응답 형식 (팀 공통)

```json
// 성공
{ "success": true, "data": { ... }, "error": null }
// 실패
{ "success": false, "data": null, "error": { "code": "RENTAL_PERIOD_CONFLICT", "message": "이미 예약된 기간입니다.", "details": [...] } }
```

- HTTP 상태 코드와 `error.code`를 **둘 다** 반환한다. 프론트는 분기를 `error.code`로 한다.
- `details`: 필드 검증 오류 시 `[{ "field": "email", "reason": "..." }]`.

### HTTP 상태 코드 사용 규칙

| 코드 | 의미 | 예 |
| --- | --- | --- |
| 400 | 형식·검증 오류, 정책 범위 초과, 요청 자체가 성립하지 않음 | 대여 기간 31일, 차감 상한 초과, **본인 물품에 대한 예약·관심·채팅** (`*_OWN_ITEM`) |
| 401 | 인증 실패·토큰 만료 | 로그인 실패, Access Token 만료 |
| 403 | 권한 없음(소유자·당사자 아님, 인증·제재 조건 미충족) | 남의 물품 수정, 동네 미인증 상태로 예약 |
| 404 | 없음 또는 비공개 | 삭제된 물품, 남의 숨김 물품 |
| 409 | 상태 충돌·중복 | 기간 중복, 허용되지 않은 상태 전이, 이메일 중복 |

### 에러 코드 네이밍
`{DOMAIN}_{REASON}` 대문자 스네이크. 예: `MEMBER_EMAIL_DUPLICATED`, `ITEM_NOT_OWNER`, `RENTAL_INVALID_TRANSITION`, `PAYMENT_AMOUNT_MISMATCH`. 백엔드 `ErrorCode` enum 한 곳에서 관리한다.

### 페이지네이션
- 무한 스크롤(피드, 채팅 내역, 알림): **커서 기반**. 요청 `cursor`, 응답 `{ items, nextCursor, hasNext }`.
- 검색·목록: 페이지 기반. 요청 `page`(0부터), `size`(기본 20). 응답 `{ items, page, size, totalElements, hasNext }`.
  단, 다음은 size를 받지 않고 정책 키로 고정한다(보낸 size는 무시): 검색(REQ-010) `search.page`, 빌린 내역(REQ-019) `rental-history.page`, 내 물품 관리(REQ-020) `my-items.page`, 물품별 예약 현황(REQ-020) `item-rentals.page`. 값은 `policies.md`.

### 인증
- Access Token: `Authorization: Bearer <JWT>`, 30분.
- Refresh Token: HttpOnly 쿠키(`SameSite=Lax`, `Path=/api/auth`, local 외 `Secure`), 14일. 로그인마다 토큰 묶음(family)을 새로 만들어 Redis `rt:{memberId}:{familyId}`에 저장하고 재발급마다 교체(Rotation). 이미 교체된 토큰을 재사용하면 **그 family만** 무효화(다른 기기는 유지). 상세는 `domains/member.md` REQ-002·003.
- 프론트·백엔드는 브라우저 기준 **같은 출처**로 동작시킨다: 로컬은 Vite 개발 서버 프록시(`/api`, `/ws` → `localhost:8080`), 운영은 Nginx가 정적 파일과 API를 같은 도메인으로 서빙. 그래서 CORS 설정 없이 쿠키가 전달된다. 백엔드 CORS는 기본 차단으로 둔다.
- WebSocket: STOMP CONNECT 프레임 헤더의 JWT로 인증.

## 5. 데이터 규약

- **금액**: 정수, 원 단위 (`int`/`long`, DB `INT`/`BIGINT`). `double`·`float` 금지. 수수료 계산은 원 단위 미만 절사.
- **날짜**: 대여 기간은 `LocalDate`(일 단위, 시작·종료일 모두 포함). 이벤트 시각은 `LocalDateTime`(서버·DB 타임존 `Asia/Seoul`).
- **대여 일수** = `endDate - startDate + 1`. (예: 10/3~10/4 = 2일)
- **기간 겹침 조건**: `new.start <= existing.end AND new.end >= existing.start`.
- **삭제**: 물품·회원은 소프트 삭제(status). 거래 이력은 삭제하지 않는다.
- **Enum**: DB에는 `@Enumerated(STRING)`으로 영문 코드 저장.
- **위치 개인정보**: 사용자의 현재 위치 좌표는 저장하지 않는다(동네 인증 결과만). 프로필·목록에는 행정동명까지만 노출.

## 6. 보안

- 비밀번호 BCrypt.
- 소유자·거래 당사자 검증은 **항상 서버에서**(물품 수정·삭제, 대여 상태 변경, 채팅방 접근, 후기).
- 결제 금액은 서버에서 계산·검증. 클라이언트 금액을 신뢰하지 않는다.
- 업로드 파일: 확장자 + MIME + 용량 검증.
- 비밀값은 환경 변수(`.env`), 저장소에 포함 금지.
- 운영 환경 HTTPS.

## 7. 목표 성능 (검증 지표)

| 항목 | 목표 |
| --- | --- |
| 조회 API(피드·검색·상세) | p95 ≤ 500ms |
| 예약·결제 API | p95 ≤ 1s |
| 동시 사용자 | 가상 사용자 100명+ 에서 검색·예약 정상 |
| 예약 정합성 | 동일 물품·기간 동시 예약 시 중복 0건 |
| 피드 성능 | 인덱스·캐시 적용 전 대비 p95 50% 이상 단축 |
| 결제 정합성 | 중복 승인·중복 환불 0건, PG↔내부 상태 불일치 0건 |
| AI 비교 | JSON 스키마 준수율 ≥ 99%, 파손 탐지 재현율 ≥ 80%, 30초 이내 |

더미 데이터 기준: 회원 1만, 물품 3만, 대여 10만.

## 8. 기술적 도전 과제 (보고서 핵심)

모든 과제는 **적용 전후 비교 결과를 표로 남긴다** (`docs/experiments/`에 기록 권장).

1. **예약 동시성** — 격리 수준(READ COMMITTED, REPEATABLE READ) × {낙관적 락(version), 비관적 락(`SELECT ... FOR UPDATE`), Redisson 분산 락, DB 유니크 인덱스(날짜별 점유 테이블 `rental_dates`)}를 2대 이상 인스턴스에서 JMeter로 비교. TPS·평균 응답·오류율·중복 건수. 락 획득 실패·타임아웃 시 처리와 재시도 정책도 정의한다. 기본값은 **Redisson 분산 락(대기 3초, 임대 시간 미지정 → watchdog 자동 연장) + READ COMMITTED**. 임대 시간을 고정하면 watchdog이 꺼져 긴 트랜잭션 중 락이 풀릴 수 있으므로 지정하지 않는다.
2. **결제 정합성** — 멱등성 키, 결제 상태와 대여 상태 전이 분리, 실패 이력, PG↔내부 상태 조정(reconciliation) 배치. 지연·실패 주입 테스트.
3. **AI 비교 신뢰성** — 큐 비동기, JSON 스키마 고정·검증 실패 재시도, 캐싱, 호출 한도, "판정이 아닌 참고".
4. **동네 피드 성능** — 위경도 bounding box 선필터 → 거리 계산, 공간 인덱스 또는 Geohash, 커서 페이징, Redis 캐시·무효화.

## 9. 단계별 구현 스위치

1순위 단계에는 결제가 없다. 결제 유무로 흐름이 갈리는 지점은 설정 플래그로 분기한다.

```yaml
app:
  feature:
    payment-enabled: false   # 1순위: false → 예약 즉시 RESERVED, 보증금 처리 생략
```

플래그는 예약 생성의 초기 상태만 정한다. 취소 환불·보증금 처리는 플래그가 아니라 **승인된 결제 행이 있는지**로 갈린다. 플래그를 나중에 꺼도 이미 받은 돈은 돌려주기 위해서다.

값: 기본 `false`, local은 `infra/.env`의 `APP_FEATURE_PAYMENT_ENABLED`, test는 `false`(켜야 하는 테스트만 `@TestPropertySource`). **prod는 REQ-049가 머지될 때까지 `false`로 둔다.** 그 전에는 상태 확인 '이상' 건의 동결 보증금을 처리할 방법이 없다.
