# 로드맵·우선순위·진행 현황

## 우선순위 원칙

| 단계 | 정의 | 대상 |
| --- | --- | --- |
| **1순위** (20) | 등록 → 검색 → 예약 → 인계 → 반납이 한 바퀴 도는 최소 서비스. 결제 없음. 중복 예약 방지 포함. **축소 불가** | REQ-001~020 |
| **2순위 필수** | 과제 목표와 직결. **축소 불가** | REQ-023~027, 031, 034, 049 + 관리자 보증금 확정 API 2개(057에서 앞당김) |
| **2순위 최소 구현 필수** | 필수 기능이 기대고 있어 빼면 흐름이 끊김. 부가 기능만 축소 | 028·029(채팅 목록·안 읽은 수 — 채팅방 진입점), 032(알림 목록·안 읽은 수 — 연체 알림 031 등 모든 알림의 확인 경로) |
| **2순위 기타** | 축소 가능. 축소 순서: 035 → 022 → 040 | REQ-021, 022, 030, 033, 035~040 |
| **3순위** (27) | 일정 여유 시. 미구현은 보고서에 향후 과제로 | REQ-041~068 (049 제외) |

일정이 밀리면 3순위부터 제외한다.

## 완료 판정 기준

- **1순위 완료**: 테스트 계정 2개로 동네 인증 → 물품 등록 → 검색 → 예약 → 인계 → 반납 요청 → 상태 확인 → 거래 완료가 끊김 없이 진행되고, 동일 물품·기간 동시 예약 시 중복 0건.
- **2순위 완료**: 결제·보증금 반환·보류·차감이 PG 테스트 환경에서 정상 처리, 연체 배치 동작, 채팅·알림·후기가 전체 흐름에 연결.

## 일정 (주차)

| 주차 | 단계 | 백엔드 A (이승민) | 백엔드 B (신수정) | 프론트 (정혜련) |
| --- | --- | --- | --- | --- |
| 4~7 | 설계 | 아키텍처, 상태·예약 설계, 인프라 | ERD, 도메인 모델 | 화면 설계, 컴포넌트 기초 |
| 8 | 중간 발표 | | | |
| 9~11 | 1순위 | 예약·취소·상태·인계·반납, 빌린 내역 (012–019) | 회원·인증·동네, 물품 CRUD·피드·검색·상세, 내 물품 (001–011, 020) | Swagger + mock으로 1순위 화면 |
| 11~12 | 동시성 + 2순위 착수 | 락 실험, PG 결제·보증금(024·025·049), 연체 배치(023·031) | 채팅·RabbitMQ(026–029) | 결제·대여 상세·채팅 화면 |
| 12~13 | 2순위 | 연장(038), 정산(039), AI 비교(040) | 후기(034), 알림(032), 관심·최근(022·035), 프로필(021), 비번·약관(036·037) | 나머지 화면, 카카오맵(030) |
| 14 | 테스트·통합 | 부하·장애 주입·정합성 | 통합 테스트 | QA, 반응형 |
| 15 | 최종 발표 | | | |

## 1순위 착수 순서 (의존성 기준)

1. 공통 기반: 프로젝트 스캐폴딩, 공통 응답·예외, Security+JWT, Flyway, docker-compose.local, 파일 저장소(`FileStorage`: 로컬 디스크/S3), CI → **B·A 공동, 가장 먼저**
2. 회원·인증(001–003) → 동네(004) → 프로필(005)
3. 카테고리 초기 데이터 → 물품 등록(006) → 상세(011) → 수정·삭제(007·008)
   - **`feat/member-item-entity`(A가 B 대행)**: members·regions·member_regions·categories(시드 포함)·items·item_images·item_available_periods 엔티티·마이그레이션만 먼저 머지. 대여 작업(012–019)이 이 테이블에 의존하므로 B의 API 브랜치를 기다리지 않게 분리했다. API·서비스는 B 담당 그대로
   - **`feat/member-item-entity` 머지 직후, `feat/item-feed-search` 착수 전에 `feat/rental-entity`(A)**: `rentals`·`rental_status_histories` 엔티티·마이그레이션·`ACTIVE_FOR_PERIOD` 상수만 먼저 머지. `rentals`가 `items`·`members`를 FK로 참조하므로 그 뒤에, B의 피드 "대여 중" 배지(009), 기간 검색(010), 내 물품 관리(020)가 의존하므로 그 앞에 둔다
4. 피드(009)·검색(010) — 병행 가능
5. 캘린더(012) → 예약+중복 방지(013·014) → 상태 머신(016) → 취소(015)
6. 인계(017) → 반납·상태 확인(018)
7. 빌린 내역(019)·내 물품 관리(020)
8. 동시성 테스트(2인스턴스) → 1순위 완료 판정 (✅ `chore/concurrency-lock-experiment`: 기본 경로 중복 예약 0건, `docs/experiments/concurrency.md`)

## 진행 현황

브랜치 단위 계획은 `docs/git-workflow.md` §2.

상태: ⬜ 미착수 · ✅ 완료 · ⏭ 제외(축소·향후 과제). "⬜ 계약 완료(로직 미구현)"는 컨트롤러·DTO·에러 코드만 머지돼 openapi.json에 있고 서비스는 미구현(`chore/member-item-api-contract`)이라는 뜻이다. **완료 PR에서 해당 행을 ✅로 바꿔 함께 머지**한다.
"진행 중"은 이 표에 적지 않는다(머지 전에는 develop에 안 보이므로 의미 없음) — 원격 브랜치·열린 PR로 확인한다. 표와 코드가 다르면 코드가 기준이며 `/check-spec`으로 확인 후 표를 고친다.

### 1순위
| REQ | 이름 | 담당 | 상태 |
| --- | --- | --- | --- |
| 001 | 회원가입 | B | ✅ |
| 002 | 로그인·로그아웃 | B | ✅ |
| 003 | 토큰 재발급 | B | ✅ |
| 004 | 동네 설정·인증 | B | ✅ 백엔드 구현 완료 (#33) |
| 005 | 프로필 수정 | B | ✅ 백엔드 구현 완료 (#33) |
| 006 | 물품 등록 | B | ✅ 백엔드 구현·통합 테스트 완료 |
| 007 | 물품 수정 | B | ✅ 백엔드 구현·통합 테스트 완료 |
| 008 | 물품 삭제 | B | ✅ 백엔드 구현·통합 테스트 완료 |
| 009 | 동네 피드 | B | ✅ 백엔드 구현·통합 테스트 완료 |
| 010 | 필터링 검색 | B | ✅ 백엔드 구현·통합 테스트 완료 |
| 011 | 상세 조회 | B | ✅ 백엔드 구현·통합 테스트 완료 |
| 012 | 대여 캘린더 | A | ✅ |
| 013/014 | 예약·중복 방지 | A | ✅ |
| 015 | 예약 취소 | A | ✅ |
| 016 | 대여 상태 관리 | A | ✅ |
| 017 | 인계 확인 | A | ✅ (인계 리마인더 배치 [2] 포함) |
| 018 | 반납·상태 확인 | A | ✅ |
| 019 | 빌린 내역 | A | ✅ |
| 020 | 내 물품 관리 | B | ✅ 백엔드 구현·통합 테스트 완료 |

### 2순위
| REQ | 이름 | 담당 | 필수 | 상태 |
| --- | --- | --- | --- | --- |
| 021 | 사용자 프로필 | B | | ✅ 백엔드(`feat/member-extra`). 신뢰 점수·칭찬 항목별 횟수는 054, 차단 시 물품 숨김은 056. 프론트 SCR-13 `/users/:memberId` ✅(`feat/fe-member-extra`, 등록 물품·받은 후기 탭, 상세·채팅·대여 상세·후기에서 진입) |
| 022 | 관심 | B | | ✅ 백엔드(`feat/item-wish-recent`). 추가·해제 멱등, 상세 isWished 연결. 관심 목록은 삭제·숨김 물품도 status와 함께 남김. 피드·검색 카드에는 isWished 없음. ✅ 프론트(`feat/fe-item-wish-recent`): 상세 하트(낙관적 토글), SCR-12 `/me/wishes`(삭제·숨김 표시, 바로 해제). 추천 캐시 무효화는 060 |
| 023/031 | 연체 감지·알림 | A | ✔ | ✅ (031 저장은 032 구현으로 연결, 실시간 전달은 채팅 STOMP로 연결) |
| 024/025 | 결제·보증금 반환 | A | ✔ | ✅ |
| 026/027 | 실시간 채팅 | B | ✔ | ✅ 방 정보 조회(`GET /api/chat-rooms/{id}`)는 028에서 구현. 대여 상태 SYSTEM 메시지·차단(056) 시 생성 불가는 미구현. 프론트 SCR-10 채팅방·상세 "채팅하기" ✅(`feat/fe-chat`, SYSTEM 메시지는 오면 안내 줄로 표시) |
| 028/029 | 채팅 목록·읽음 | B | | ✅ 읽음은 STOMP `/pub/chat-rooms/{id}/read`, 상대에게 `/user/queue/chat-reads`. 프론트 SCR-09 채팅 목록·안 읽은 수 배지·'읽음' 표시 ✅(`feat/fe-chat`) |
| 030 | 거래 장소 지도 | FE | | ✅ |
| 032 | 거래 활동 알림 | B | | ✅ 실시간 전달(STOMP `/user/queue/notifications`)은 채팅 027에서 연결, 인계 리마인더(`HANDOVER_REMINDER`)는 017 배치로 발송, 후기 알림은 034. 문구 임시(open-issues #50). 프론트 SCR-11 알림 목록·안 읽은 수 배지·실시간 갱신 ✅(`feat/fe-notification`) |
| 033 | 결제·보증금 내역 | A | | ✅ type 의미는 임시 해석(open-issues #44). 프론트 SCR-12 `/me/payments` 결제·보증금 탭(구분 칩·기간 시트) ✅(`feat/fe-payment-settlement`) |
| 034 | 거래 후기 | B | ✔ | ✅ 칭찬 항목은 공통 목록(open-issues #15). 빌린 내역·대여 상세의 canWriteReview에 미작성 조건 반영, 대여 상세·빌린 내역에 reviewWritten(open-issues #54). 신뢰 점수 반영은 054. 프론트 SCR-14 후기 작성·대여 상세/빌린 내역 진입 ✅(`feat/fe-review`), 받은 후기 목록은 SCR-13 프로필 탭에 붙임(`feat/fe-member-extra`) |
| 035 | 최근 본 상품 | B | | ✅ 백엔드(`feat/item-wish-recent`). 상세 조회 시 기록(본인 물품 제외), 회원당 20건 유지, 목록은 피드 카드 배열(삭제·숨김 제외). ✅ 프론트(`feat/fe-item-wish-recent`): SCR-12 `/me/recent`. 추천 활용은 060 |
| 036 | 비밀번호 재설정 | B | | ✅ 백엔드(`feat/member-extra`). 메일은 백그라운드 발송(실패는 로그만), 링크 `{app.frontend.base-url}/password-reset?token=`. 프론트 SCR-01 `/password-reset/request`·`/password-reset?token=` ✅(`feat/fe-member-extra`) |
| 037 | 약관 동의 | B | | ✅ 백엔드(`feat/member-extra`). 약관 본문은 초안. 프론트 SCR-01 가입 체크박스·재동의 모달·SCR-15 `/terms/:slug` ✅(`feat/fe-member-extra`). 거부 시 서버 차단은 open-issues #56 |
| 038 | 대여 연장 | A | | ✅ 대여 상세에 결제 대기 연장(`pendingExtension`)·연장 횟수(`extensionCount`). 프론트 SCR-08 연장 시트·연장료 결제·결제 복귀 분기 ✅(`feat/fe-rental-extension`) |
| 039 | 제공자 정산 | A | | ✅ HELD 전이는 063에서 구현(분쟁 접수 → HELD, 목록 heldTotal). 프론트 SCR-12 `/me/payments?tab=settlements` 정산 탭(합계·상태·지급 예정일) ✅(`feat/fe-payment-settlement`) |
| 040 | AI 상태 비교 | A | | ✅ 검증 실험 미실행 (`docs/experiments/ai-compare.md`). 프론트 SCR-08 반납 기록 AI 비교 카드·상태 확인/차감 제안 요약 ✅(`feat/fe-ai-compare`, 결과 대기 판단은 임시 open-issues #59) |
| 049 | 보증금 보류·차감 | A | ✔ | ✅ 프론트 SCR-08 보증금 카드·차감 제안 시트·수락/거절·미반납 신고(018) ✅(`feat/fe-deposit-deduction`, 신고 건 판단은 임시 open-issues #57) |

### 프론트 정리
| 브랜치 | 내용 | 상태 |
| --- | --- | --- |
| `docs/fe-design-system` | 현재 디자인 문서화(`docs/design/design-system.md`)와 정리 대상 목록 | ✅ |
| `chore/fe-design-refactor` | 정리 대상 리팩터링 | ✅ (#43) |

### 인프라·배포
| 브랜치 | 내용 | 상태 |
| --- | --- | --- |
| `chore/infra-deploy` | AWS 운영 배포 구성(저장소 쪽): 이미지·운영 compose·Nginx·GHCR 워크플로·배포 스크립트·런북 | ✅ 저장소 구성. AWS 리소스 생성과 운영망 검증(REQ-011 실제 IP, 서버 간 차단)은 런북대로 별도 진행 |
| `docs/infra-shared-account` | 런북·`.env.prod.example`을 학교 공용 AWS 계정 제약에 맞춤: 운영진 인스턴스 프로필, 보안 그룹 1개, `inha-capstone-19-` 이름, RDS 기본 파라미터 그룹 대안, Budgets 제외 | ✅ 문서 |

### 테스트·성능 실험 (14주차)
| 브랜치 | 내용 | 상태 |
| --- | --- | --- |
| `chore/load-test` | 부하 테스트: 로컬 docker 앱 2개 + k6, 더미 회원 1만·물품 3만·대여 10만 (`docs/experiments/load-test.md`, `scripts/load/`) | ✅ 실행. 조회 p95·예약 중복 0건은 목표 충족(200명까지). 예약 200명에서 알림 저장의 커넥션 풀 교착으로 멈춤 → `fix/notification-async-listener`에서 수정. 미실행(별도 브랜치): 운영 환경 짧은 확인, 피드 전후 비교(§8-4), 장애 주입(§8-2), 락 전략 비교(§8-1, → `chore/concurrency-lock-experiment`) |
| `fix/notification-async-listener` | 커넥션 풀 교착 수정: 알림 저장 AFTER_COMMIT 리스너를 전용 실행기(`NotificationListenerExecutor`)에서 실행, 작은 풀 회귀 테스트 | ✅ 개선 후 예약 200명 실패 0·p99 38.1ms, 100명 쉼 0 실패 0·p95 약 350ms. 쉼 0에서는 알림 약 65%를 큐 포화로 버림(받아들인 한계, `docs/experiments/load-test.md`). 남은 일: 알림 묶어 저장(배치 INSERT), 운영 dropped 로그 경보 |
| `chore/query-index-experiment` | 피드(009)·검색(010) 조회 인덱스 비교: 피드 F0~F4(단일·복합·위경도 포함·SPATIAL), 검색 S0~S2(LIKE·복합 B-tree·FULLTEXT ngram), 물품 3만·30만, 버퍼 풀 128MB·768MB (`docs/experiments/query-index.md`, `scripts/load/query-index/`) | ✅ V22 채택: 피드 `(status, bumped_at, id, latitude, longitude)`(30만·128MB p95 −48~75%, 그 밖 동일), 검색 FULLTEXT ngram(한글·숫자 2~100글자만, 3만 p95 −40%·30만 768MB −73%). 남은 일: 30만에서 검색 p95 2.4s — 개수 쿼리 상한·캐시(API 변경), 피드 Redis 캐시 |
| `chore/concurrency-lock-experiment` | 예약(013) 동시성 비교(§8-1): 격리 수준 RC·RR × {락 없음·낙관적·비관적(물품 행·활성 대여 범위)·Redisson·`rental_dates` UK·Redisson+UK} 14개 구성, 앱 2개 + k6 (`docs/experiments/concurrency.md`, `scripts/load/concurrency/`, 실험 전략 `global/lock/experiment/`는 프로퍼티로만 켬) | ✅ **1순위 완료 판정 근거**: 기본 경로(Redisson + READ COMMITTED) 중복 예약 0건·교착 0·기타 실패 0(같은 기간 100명 60라운드, 부분 겹침, 겹치지 않는 기간 800건, 경합 없음 약 10.7만 건). 기본 전략 유지. 락 없음·RC 범위 잠금은 중복, 낙관적·RR 범위 잠금·RR UK는 교착·잘못된 거절로 기각. 물품 행 `FOR UPDATE`는 정확하고 경합 p95 약 40% 짧지만 대기 중 커넥션 점유 위험(미측정). 남은 일: 장애 주입(§8-2)에서 UK 2차 방어 판단 |

### 3순위 (착수 시 추가)
| REQ | 이름 | 담당 | 필수 | 상태 |
| --- | --- | --- | --- | --- |
| 063 | 문의·분쟁 접수 | A | | ✅ 백엔드·테스트(동시성은 성능 실험 때), 분쟁 종료(HELD → SCHEDULED)·관리자 처리는 057. 프론트 SCR-15 `/me/inquiries`(내 문의)·`/me/inquiries/new`(접수), SCR-08 진입 링크 ✅(`feat/fe-inquiry`, 열린 분쟁 표시 필드는 open-issues #60) |
| 057 | 관리자 (문의 처리·분쟁 종료만) | A | | 🟡 문의 처리 백엔드·테스트 ✅(`feat/admin-process`: `GET/PATCH /api/admin/inquiries`, 분쟁 종료 시 정산 HELD → SCHEDULED). 회원·물품·신고·카테고리·증빙 조회·관리자 화면(SCR-15·16)은 미착수. 보증금 확정 API 2개는 049에서 ✅ |
| 067 | AI 등록 보조 | A | | ✅ 백엔드(`feat/ai-category` → `feat/ai-draft`, `POST /api/items/ai-draft`). 프론트 SCR-06 등록 "AI로 채우기" ✅(`feat/fe-ai-draft`, 보증금 칸 채우기 `feat/fe-ai-draft-deposit`) |

남은 3순위: 041 소셜 로그인 · 042 동네 범위 · 043 탈퇴 · 044 숨기기 · 045 끌어올리기 · 046 검색어 · 047 관련 물품 · 048 공유 · 050 채팅 사진 · 051 약속 · 052 키워드 알림 · 053 알림 설정 · 054 신뢰 점수 · 055 신고 · 056 차단 · 057 관리자(문의 처리 외) · 058 대여 요청 게시 · 059 배송 대여 · 060 추천 · 061 웹 푸시 · 062 노쇼 페널티 · 064 자동완성 · 065 거리순·지도 탐색 · 066 금지어 필터 · 068 AI 금지 품목 판별

## 향후 확장 (범위 외)
대여 → 판매 전환, AI 결과 기반 분쟁 판단 보조·보증금 보험, 협업 필터링 추천·수요 예측·가격 추천.
