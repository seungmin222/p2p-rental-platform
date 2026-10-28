# 코드 컨벤션

원칙: **형식은 도구가 강제하고, 이 문서는 도구가 못 잡는 이름·구조 규칙만 다룬다.** 설계 규칙(계층, 트랜잭션, 상태 전이 등)은 `backend/CLAUDE.md`, `frontend/CLAUDE.md`에 있다.

## 1. 포맷 (자동화)

| 대상 | 도구 | 실행 |
| --- | --- | --- |
| Java | **네이버 캠퍼스 핵데이 Java 컨벤션** — Spotless + 네이버 Eclipse formatter XML (+ 선택: Checkstyle 네이버 규칙) | `./gradlew spotlessApply` (수정), `./gradlew spotlessCheck` (검사) |
| JS/JSX/CSS | ESLint + Prettier | `npm run lint`, `npm run format` |
| 공통 | `.editorconfig` (루트) | IDE가 자동 적용 |

- 네이버 컨벤션 요지: **들여쓰기 탭(크기 4)**, 한 줄 최대 120자, 중괄호 K&R 스타일, import에 `*` 사용 금지, 파일 끝 개행.
- 설정 파일 위치: `backend/config/naver-eclipse-formatter.xml`(필수), `backend/config/naver-checkstyle-rules.xml`(선택). 원본은 GitHub `naver/hackday-conventions-java` 저장소에서 받는다. 버전·Spotless 설정법은 도입 시점의 저장소 README를 확인한다.
- IntelliJ: 같은 XML을 Code Style에 import하고 "Reformat on save"를 켜면 저장할 때 맞춰진다.
- Prettier 설정(제안): `singleQuote: true`, `semi: true`, `printWidth: 100`, `tabWidth: 2`(프론트는 JS 생태계 관례대로 스페이스 2칸).
- **커밋 전 포맷터 실행.** 에이전트도 커밋 전에 `spotlessApply` / `npm run format`을 돌린다. 포맷 변경만 있는 커밋은 `style(...)`로 분리.

## 2. Java 이름 규칙

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 패키지 | 소문자, 단수 | `rental`, `chat` |
| 컨트롤러 / 서비스 / 리포지토리 | `{Domain}Controller`, `{Domain}Service`, `{Domain}QueryService`(읽기 분리 시), `{Domain}Repository` | `RentalController` |
| QueryDSL 커스텀 리포지토리 | `{Domain}SearchRepository` + `Impl` | `ItemSearchRepositoryImpl` |
| 요청 DTO | `{Domain}{Action}Request` | `RentalCreateRequest`, `ItemUpdateRequest` |
| 응답 DTO | `{Domain}{용도}Response` | `RentalResponse`, `ItemSummaryResponse`, `ItemDetailResponse` |
| 검색 조건 | `{Domain}SearchCondition` | `ItemSearchCondition` |
| 도메인 이벤트 | `{Domain}{과거분사}Event` | `RentalReturnedEvent` |
| 예외 | `BusinessException` + `ErrorCode` enum 하나로 통일. **도메인별·상황별 예외 클래스를 만들지 않는다** (상태 전이 위반도 `ErrorCode.RENTAL_INVALID_TRANSITION`) | |
| Enum | 타입 PascalCase, 값 UPPER_SNAKE | `RentalStatus.PENDING_PAYMENT` |
| 상수 | `static final` UPPER_SNAKE | `MAX_EXTENSION_COUNT` |
| boolean 필드·메서드 | `is`/`has`/`can` 접두사 | `isPrimary`, `hasActiveRental()` |

### 메서드 동사
| 동사 | 의미 |
| --- | --- |
| `get{X}` | 반드시 있어야 함, 없으면 예외 (`getRental(id)` → 없으면 404) |
| `find{X}` | 없을 수 있음, `Optional` 또는 빈 목록 반환 |
| `exists{X}` / `is{X}` | boolean 확인 |
| `create` / `update` / `delete` | 서비스의 쓰기 |
| `validate{X}` | 검증, 실패 시 예외, 반환값 없음 |
| 엔티티 상태 전이 | 업무 동사 그대로: `handover()`, `requestReturn()`, `cancel()`, `markOverdue()` |

### 테스트
- 클래스: `{대상}Test`(단위), `{대상}IntegrationTest`(통합), `{대상}ConcurrencyTest`(동시성)
- 메서드: 영어 snake 또는 camel 아무거나 + **`@DisplayName("같은 기간 동시 예약 시 1건만 성공한다")` 필수**
- given / when / then 주석으로 구분

## 3. REST API 규칙

- 경로: `/api/` + **복수형 명사, kebab-case** — `/api/items`, `/api/chat-rooms`, `/api/members/me/regions`
- 자기 자신은 `me`: `/api/members/me`
- 상태 전이·동작은 하위 경로 동사(명사형): `PATCH /api/rentals/{id}/handover`, `/cancel`, `/return`
- 쿼리 파라미터·JSON 필드: camelCase (`startDate`, `pricePerDay`)
- 날짜 `YYYY-MM-DD`, 일시 ISO-8601 (`2026-10-07T15:30:00`, 서버 타임존 Asia/Seoul)
- 생성은 201, 조회·수정은 200, 내용 없는 삭제는 200 + `data: null`(팀 공통 응답 형식 유지)
- Enum 값은 응답에도 영문 코드 그대로. 한글 라벨은 프론트가 매핑

## 4. DB 스키마 규칙

### 이름
| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 테이블 | snake_case, **복수형** | `rentals`, `item_images`, `chat_messages` |
| 컬럼 | snake_case (JPA 필드 camelCase가 Spring 기본 전략으로 자동 변환되므로 `@Column(name)`은 예외적으로만) | `price_per_day` ← `pricePerDay` |
| PK | `id` BIGINT AUTO_INCREMENT | |
| FK 컬럼 | `{참조 대상 단수}_id`. 역할이 다르면 역할명 | `item_id`, `owner_id`, `renter_id` |
| boolean | `is_` 접두사, TINYINT(1)/BOOLEAN | `is_primary`, `is_read`, `is_active` |
| 시각(DATETIME) | `_at` 접미사 | `created_at`, `verified_at`, `returned_at` |
| 날짜(DATE) | `_date` 접미사 (단, 대여 기간은 `start_date`/`end_date`) | `scheduled_date` |
| 금액 | `_amount`, `_fee`, `_price` 등 의미가 드러나게, INT(원) | `deposit_amount`, `rental_fee`, `item_price` |
| 개수 | `_count` | `view_count`, `extension_count` |
| URL | `_url` / 여러 개면 `_urls`(JSON) | `profile_image_url` |
| 상태·종류 | `status`, `type`, `phase` 등 VARCHAR(30), 값은 Enum 영문 코드 | |

### 제약·인덱스 이름
| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 유니크 | `uk_{테이블}_{컬럼들}` | `uk_wishes_member_id_item_id` |
| 일반 인덱스 | `idx_{테이블}_{컬럼들}` | `idx_rentals_item_id_status` |
| FK 제약 | `fk_{테이블}_{참조테이블}` | `fk_rentals_items` |
| FK 제약 (같은 테이블을 두 번 이상 참조) | `fk_{테이블}_{참조테이블}_{역할}`. 역할은 FK 컬럼명에서 `_id`를 뺀 것. 한 번만 참조하면 역할명을 붙이지 않는다 | `fk_rentals_members_owner`, `fk_rentals_members_canceled_by` |

### 기타
- **예약어 금지**: `condition`, `range`, `order`, `rank`, `key`, `group`, `desc` 등 MySQL 예약어를 컬럼명으로 쓰지 않는다 (예: `item_condition`, `region_range`).
- 모든 테이블에 `created_at`, `updated_at` (BaseTimeEntity, JPA Auditing).
- FK 제약은 **건다**(데이터 정합성 우선). 단, 로그성 테이블(`item_view_logs`, `notifications`, `payment_transactions`)은 생략 가능. (실서비스는 샤딩·온라인 스키마 변경 때문에 생략하기도 하지만, 단일 MySQL인 이 프로젝트엔 해당하지 않음. 동시성 실험 시 FK가 부모 행에 공유 잠금을 거는 점은 결과 해석에 반영)
- 더미 데이터 대량 적재 스크립트에서만 `SET FOREIGN_KEY_CHECKS = 0` 허용, 끝나면 반드시 1로 복구.

### 정규화
- **기본은 3정규형.** 반정규화는 아래 경우만 하고, `docs/erd.md`의 해당 컬럼에 **원본과 갱신 시점**을 적는다.
  1. **스냅샷**(필수): 과거 시점 값을 보존해야 하는 것 — 대여 시점의 `price_per_day`·`deposit`, 동의한 약관 버전
  2. **집계 캐시**: 목록에서 매번 집계하면 느린 것 — `items.*_count`, `chat_rooms.last_message_*`, `members.trust_score`(원본: `trust_score_events`)
- **바뀌는 값은 복사하지 않는다**: 닉네임·프로필 사진·동네명은 조인으로 조회.
- **JSON 컬럼**은 통째로 저장·조회만 하는 값에만(`image_urls`, `checklist`, `ai_result`). 그 안의 값으로 검색·조인해야 하면 테이블로 분리.
- 새 반정규화가 필요해 보이면 구현 전에 이유를 설명하고 확인받는다.
- 엔진 InnoDB, 문자셋 `utf8mb4` / `utf8mb4_unicode_ci`.
- 삭제는 소프트 삭제(`status`), 물리 삭제는 연결 테이블(`wishes` 등)만.
- Flyway 파일명: `V{번호}__{동사}_{대상}.sql` — `V3__create_rental_tables.sql`, `V7__add_region_range_to_member_regions.sql`. 초기 데이터도 `V`로 넣는다(예: `V2__insert_categories.sql`). 반복 마이그레이션 `R__`은 쓰지 않는다(`backend/CLAUDE.md` DB 마이그레이션).

## 5. 프론트엔드 이름 규칙

| 대상 | 규칙 | 예 |
| --- | --- | --- |
| 컴포넌트 파일·이름 | PascalCase `.jsx` | `RentalStatusBadge.jsx` |
| 페이지 | `{이름}Page.jsx` | `RentalDetailPage.jsx` |
| 훅 | `use` + PascalCase | `useRentalDetail`, `useKakaoMap` |
| 일반 함수·변수 | camelCase | `formatPrice` |
| 상수 | UPPER_SNAKE | `RENTAL_STATUS` |
| API 함수 | 동사 + 대상 | `getRental`, `createRental`, `cancelRental` |
| 이벤트 핸들러 | `handle` + 대상 + 동작 / props는 `on` + … | `handleCancelClick`, `onCancel` |
| boolean | `is`/`has`/`can` | `isOwner`, `canWriteReview` |
| 폴더 | kebab-case 또는 소문자 | `features/rental`, `shared/ui` |

## 6. 주석·로그

- 주석은 "무엇"이 아니라 **"왜"**. 명세 근거가 있으면 REQ ID: `// REQ-015: 시작일 이후 단독 취소 불가`
- TODO 형식: `// TODO(이름): 내용` — 미완성 표시용. 머지 전 의도된 것만 남긴다.
- 로그 레벨: 정상 흐름 `info`(배치 시작·종료, 결제 승인), 재시도 가능한 실패 `warn`, 개입이 필요한 실패 `error`. 개인정보·토큰·키 로그 금지.
