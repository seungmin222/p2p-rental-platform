# CLAUDE.md — 개인 간 물품 대여 플랫폼 (7팀)

동네 기반 P2P 물품 대여 웹 서비스. "동네 중고거래 UX + 렌탈의 예약·보증금·반납 관리"가 핵심 컨셉이다.
제공자가 물품·대여 가능 기간·가격·보증금을 등록하고, 대여자가 캘린더로 기간을 골라 예약 → 결제 → 인계 → 반납 → 보증금 반환 → 후기까지 한 플랫폼에서 처리한다.

## 이 저장소에서 작업할 때 반드시 지킬 것

1. **작업 전에 해당 도메인 문서를 먼저 읽는다.** 아래 문서 지도에서 REQ ID로 찾는다. 문서에 없는 정책 값(시간, 금액, 횟수, 글자 수)을 지어내지 않는다 — 수치는 `docs/policies.md`가 기준이며, 다른 문서의 수치와 다르면 `policies.md`를 따른다.
2. **상태값은 `docs/state-machines.md`의 영문 Enum 코드만 쓴다.** 한글 상태명은 UI 표시용이다. 문서에 없는 전이를 추가하지 않는다.
3. **우선순위 단계를 확인한다.** 현재 단계보다 높은 순위(2·3순위) 기능을 미리 구현하지 않는다. 단, 문서에 "구현 시 적용"으로 표시된 확장 지점은 막지 않도록 설계한다. → `docs/roadmap.md`
4. **명세와 코드가 다르면 코드를 맞추지 말고 먼저 알린다.** 명세 자체가 모순이면 `docs/open-issues.md`에 추가하고 사용자에게 확인한다.
5. **문서도 함께 갱신한다.** API·엔티티·정책이 바뀌면 `docs/api.md`, `docs/erd.md`, `docs/policies.md`를 같은 커밋에서 수정한다.
6. **API 필드는 추측하지 않는다.** 구현된 API의 요청·응답 필드는 백엔드 DTO 코드(백엔드 작업 시) 또는 `docs/openapi.json`(프론트 작업 시)으로 확인한다. `docs/api.md`는 목차일 뿐 필드를 담지 않는다. 자세한 역할 분담은 아래 "API 계약".

## 문서 지도

| 문서 | 내용 | 언제 읽나 |
| --- | --- | --- |
| `docs/architecture.md` | 기술 스택, 시스템 구성, 공통 규약(응답 형식·에러·인증·시간) | 처음, 그리고 인프라·공통 코드 작업 시 |
| `docs/policies.md` | 모든 정책 상수와 보증금 차감 정책 표 | 숫자가 필요한 모든 작업 |
| `docs/state-machines.md` | 대여·보증금·정산 상태 전이와 Enum 코드 | 대여·결제 관련 모든 작업 |
| `docs/erd.md` | 엔티티·컬럼·인덱스·제약 | 엔티티/마이그레이션 작업 |
| `docs/api.md` | 전체 API 목차(메서드·경로·인증·REQ·순위, 미구현 포함) | 무슨 API가 있는지·만들지 볼 때 |
| `docs/openapi.json` | Swagger에서 내보낸 구현된 API의 필드 상세 (자동 생성, 직접 수정 금지) | 프론트에서 API 연동·목 데이터 작성 시 |
| `docs/roadmap.md` | 우선순위(1·2·3순위), 주차별 일정, 완료 판정 기준, 담당자 | 무엇을 할지 정할 때 |
| `docs/git-workflow.md` | 브랜치 계획표, 작업 사이클, 에이전트 git 행동 규칙, PR 규칙 | git 명령 실행 전 |
| `docs/conventions.md` | 포맷 도구(네이버 Java 컨벤션·Prettier), 이름 규칙(Java·REST·DB·프론트) | 새 클래스·API·테이블·컴포넌트를 만들 때 |
| `docs/open-issues.md` | 명세 간 불일치·미결정 사항 | 해당 영역 작업 전 |
| `docs/domains/*.md` | 도메인별 상세 요구사항(REQ 단위) | 기능 구현 시 |
| `docs/reference/` | 원본 요구사항명세서 v1.0, 원본 다이어그램 | 위 문서로 판단이 안 될 때만. 위 문서와 다르면 `open-issues.md`에 기록 |
| `docs/experiments/` | 동시성·결제·피드·AI 실험 결과표 | 성능·정합성 실험 시 기록 |
| `docs/prompts/<브랜치명>/notes.md` | 브랜치별 결정 기록(무엇을·왜) | 브랜치 작업 중 결정할 때, 이어서 작업할 때 |
| `backend/CLAUDE.md` | 백엔드 패키지 구조·코딩 규칙·테스트 규칙 | `backend/` 작업 시 자동 로드 |
| `frontend/CLAUDE.md` | 프론트 구조·라우팅·화면(SCR) 목록·UI 규칙 | `frontend/` 작업 시 자동 로드 |

### 도메인 문서 ↔ REQ 매핑

| 도메인 문서 | REQ |
| --- | --- |
| `domains/member.md` 회원·인증·동네 | 001–005, 021, 036, 037, 041, 042, 043 |
| `domains/item.md` 물품·피드·검색·카테고리·지도 | 006–011, 022, 030, 035, 044–048, 060, 064, 065 |
| `domains/rental.md` 예약·대여·반납·연체·연장 | 012–020, 023, 038 |
| `domains/payment.md` 결제·보증금·차감·정산 | 024, 025, 033, 039, 049 |
| `domains/chat.md` 채팅 | 026–029, 050, 051 |
| `domains/notification.md` 알림 | 031, 032, 052, 053, 061 |
| `domains/trust.md` 후기·신뢰·신고·차단·제재 | 034, 054–056, 062, 066 |
| `domains/ai.md` AI 상태 비교·등록 보조·금지 품목 | 040, 067, 068 |
| `domains/admin.md` 관리자·문의·확장 기능 | 057, 058, 059, 063 |

## API 계약 (Swagger)

| 알고 싶은 것 | 기준 | 비고 |
| --- | --- | --- |
| 어떤 API가 있고 몇 순위인지 | `docs/api.md` | 경로·권한·REQ만. **필드를 쓰지 않는다** |
| 구현된 API의 요청·응답 필드 | 백엔드 컨트롤러·DTO 코드 | Swagger는 이 코드에서 자동 생성됨 |
| 위 내용을 파일로 | `docs/openapi.json` | 백엔드 API 변경 PR에서 재생성해 함께 커밋 |
| 화면 동작·검증 규칙·에러 코드별 처리 | `docs/domains/*.md`, `state-machines.md`, `policies.md` | Swagger로는 알 수 없음 |

- 사람은 로컬 서버의 `http://localhost:8080/swagger-ui.html`에서 확인·호출한다. Claude는 서버를 띄워 Swagger를 조회하지 말고 위 파일을 읽는다.
- 아직 구현되지 않은 API는 Swagger에 없다. 그 경우 `docs/api.md` + 도메인 문서를 기준으로 하고, 프론트는 필드 구조를 백엔드와 합의하기 전까지 임의로 확정하지 않는다.

## 저장소 구조

```
/
├── CLAUDE.md            ← 이 파일 (에이전트용 규칙)
├── README.md            ← 사람용 소개·설치 안내 (에이전트 규칙은 여기 쓰지 않는다)
├── AGENTS.md            ← 다른 에이전트용 안내. CLAUDE.md를 가리키기만 함 (규칙 복사 금지)
├── docs/                ← 설계 문서 (단일 진실 공급원)
├── backend/             ← Spring Boot (Java 21, Gradle)
├── frontend/            ← React + Vite + Tailwind (JavaScript)
├── infra/               ← docker-compose, nginx, 배포 스크립트
├── .github/             ← PR 템플릿 (CI 워크플로 추가 예정)
└── .claude/commands/    ← 팀 공용 슬래시 커맨드
```

## 자주 쓰는 명령

```bash
# 로컬 인프라 (MySQL, Redis, RabbitMQ)
cp infra/.env.example infra/.env   # 최초 1회
docker compose -f infra/docker-compose.local.yml --env-file infra/.env up -d

# 백엔드
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'
cd backend && ./gradlew test
cd backend && ./gradlew spotlessApply          # 네이버 컨벤션 포맷 (커밋 전)
cd backend && ./gradlew generateOpenApiDocs   # docs/openapi.json 재생성 (로컬 인프라 실행 필요)

# 프론트엔드
cd frontend && npm install && npm run dev
cd frontend && npm run format && npm run lint && npm run build
```

## 팀 공용 슬래시 커맨드

- `/start-work [브랜치 또는 REQ]` — develop 최신화 → 작업 브랜치 생성·이동 → 할 일 요약
- `/implement-req REQ-013` — 명세 확인 → 질문 → 구현 → 테스트 → 문서 갱신
- `/check-spec [REQ-xxx]` — 변경 사항이 명세와 맞는지 검토(수정 없음)
- `/finish-work` — 테스트 → 체크리스트 → develop 반영 → push → PR 생성

보통 하루 작업은 `/start-work` → `/implement-req`(여러 번) → `/finish-work` 순서다.

## 세션 관리

원칙: **브랜치 하나 = 세션 하나.** 작업 상태는 git(커밋·브랜치)과 `docs/roadmap.md`에 남으므로 세션을 닫아도 잃는 것이 없다. 아래 시점에 사용자에게 **새 세션을 열자고 한 줄로 제안**한다(강요하지 않는다).

- `/finish-work`로 PR을 올린 뒤: "PR 머지(및 확인 요청 반영)가 끝나면 이 세션을 닫고, 다음 브랜치는 새 세션에서 `/start-work`로 시작하세요."
- 이 세션에서 다른 브랜치로 넘어가려 할 때(`/start-work`를 이 세션에서 두 번째로 부를 때 포함): 새 세션을 권한다.
- 같은 수정을 되돌렸다 다시 하는 등 방향이 크게 바뀌었을 때, 또는 앞에서 정한 내용을 다시 확인해야 할 만큼 대화가 길어졌다고 느낄 때: "여기까지 커밋하고 새 세션에서 이어 가는 게 좋겠습니다"라고 제안한다. 제안 전에 커밋되지 않은 변경이 없도록 정리한다.
- 코드 리뷰는 별도 세션에서 `/check-spec`으로 하고, 리뷰 세션은 파일을 수정하지 않는다. 지적사항은 원래 작업 세션에서 반영한다.

### 결정 기록 (`docs/prompts/<브랜치명>/notes.md`)

세션을 닫아도 "왜 이렇게 했는지"가 남도록, 브랜치 작업 중 내린 결정을 이 파일에 짧게 기록한다. 경로는 브랜치명 그대로 쓴다(예: `docs/prompts/feat/rental-reserve/notes.md`).

- 결정할 때마다 한 항목: **무엇을** 정했는지 + **왜** 그렇게 정했는지 한두 줄. 명세에 이미 있는 내용은 쓰지 않는다.
- 결정이 바뀌면 기존 항목을 고치고 바뀐 이유를 덧붙인다. 지난 결정을 따로 쌓아 두지 않는다.
- 대화 원본·프롬프트 전문은 저장하지 않는다.
- 코드와 같은 커밋에 포함한다.

## 팀

| 이름 | 역할 | 주 담당 도메인 |
| --- | --- | --- |
| 이승민 (팀장) | 백엔드 | 아키텍처, rental, payment, 성능 검증, 배포 |
| 신수정 | 백엔드 | DB 모델링, member, item, chat |
| 정혜련 | 프론트엔드 | 전 화면, 카카오맵 |

## Git 작업 (상세: `docs/git-workflow.md` — git 명령을 실행하기 전에 반드시 읽는다)

- 브랜치: `main`(배포) ← `develop`(통합) ← `feat/{도메인}-{묶음}` (예: `feat/rental-reserve`). 계획표는 git-workflow.md §2.
- **git 명령은 에이전트가 실행하되 매번 무엇을 왜 했는지 한 줄로 알리고**, git-workflow.md §4의 "묻는다/절대 하지 않는다" 목록을 지킨다.
- 코드 작업을 시작하기 전에 현재 브랜치를 확인한다. `main`·`develop`이면 작업 브랜치를 먼저 만든다.
- 커밋 메시지: `feat(rental): 예약 중복 검사 구현 (REQ-013)`.
- 비밀값(PG·카카오·Claude API 키, DB 비밀번호)은 절대 커밋하지 않는다. `.env`는 `.gitignore` 대상, `.env.example`만 커밋한다. (결과물은 오픈소스로 공개된다.)
