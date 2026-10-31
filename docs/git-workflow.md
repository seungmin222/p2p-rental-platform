# Git·GitHub 작업 흐름

**에이전트는 git 작업을 대신 실행하되, 매번 무엇을 왜 했는지 한 줄로 알린다.** 작업 기록을 사람이 따라갈 수 있게 하기 위함이다.

## 1. 브랜치 구조

| 브랜치 | 용도 | 누가 머지 |
| --- | --- | --- |
| `main` | 배포·발표용. 항상 데모 가능 | 마일스톤마다 팀장이 `develop` → `main` PR 머지 |
| `develop` | 통합. 모든 작업 브랜치는 여기로 PR | PR 작성자가 체크리스트 확인 후 직접 머지 (승인 불필요) |
| `feat/{도메인}-{묶음}` | 기능 묶음 (2~5일 크기) | |
| `fix/{도메인}-{내용}` | 버그 수정 | |
| `docs/{내용}`, `chore/{내용}` | 문서, 설정·의존성 | |
| `exp/{내용}` | 버려도 되는 실험 (머지 안 할 수 있음) | |

**핵심 원칙: 어떤 브랜치든 일주일 안에 `develop`으로 머지되는 크기.** 넘어갈 것 같으면 완성된 부분만 먼저 PR(미완성 API는 Swagger에 "미구현" 표시).

## 2. 브랜치 목록 (계획)

진행하면서 `docs/roadmap.md` 진행 표와 함께 갱신한다.

### 1순위
| 브랜치 | REQ | 담당 |
| --- | --- | --- |
| `chore/setup-backend` | 공통 기반: 응답·예외·Security·JWT 골격·Flyway·Swagger·QueryDSL·ShedLock·Spotless 설정, `.env` 로딩, 파일 저장소 | A+B |
| `chore/setup-frontend` | Vite·Tailwind·라우터·TanStack Query·axios·MSW 골격 | FE |
| `chore/fe-shared-ui` | 공통 UI·레이아웃·상태 라벨·포맷 | FE |
| `chore/fe-shared-ui-input` | 공통 입력 컴포넌트: Calendar(범위 선택)·ImageUploader (`chore/fe-shared-ui`에서 분리) | FE |
| `chore/setup-storage-shedlock` | `chore/setup-backend` 잔여분: 파일 저장소(`FileStorage`·`ImageProcessor`) + ShedLock 설정 | A |
| `feat/member-item-entity` | members·regions·member_regions·categories·items·item_images·item_available_periods 엔티티·마이그레이션만 | A(B 대행) |
| `feat/rental-entity` | rentals·rental_status_histories 엔티티·마이그레이션만 (`items`·`members` FK 때문에 `feat/member-item-entity` 머지 직후, B의 `feat/item-feed-search` 착수 전에 머지) | A |
| `chore/member-item-api-contract` | 001–011·020 API 계약만: 컨트롤러·요청·응답 DTO·Swagger·ErrorCode·서비스 껍데기(`UnsupportedOperationException`). 프론트가 openapi.json으로 목을 만들 수 있게 먼저 머지. 로직은 아래 4개 브랜치가 채운다 | A(B와 합의) |
| `feat/member-auth` | 001–003 (엔티티는 `feat/member-item-entity`에 있음) | B |
| `feat/member-region` | 004, 005 (엔티티는 `feat/member-item-entity`에 있음) | B |
| `feat/item-crud` | 006–008, 011 (엔티티는 `feat/member-item-entity`에 있음) | B |
| `feat/item-feed-search` | 009, 010, 020 | B |
| `feat/rental-reserve` | 012–014, 016(생성·상세) | A |
| `feat/rental-cancel` | 015 (단독·양측 동의 취소, `feat/rental-reserve` 머지 후) | A |
| `feat/rental-return` | 016(1순위 전이 완성), 017, 018 | A |
| `feat/rental-history` | 019 (`feat/rental-return`에서 분리, 머지 후 착수) | A |
| `feat/fe-auth-region` | SCR-01, `/me` 프로필 요약·로그아웃 (SCR-02는 `feat/fe-region`으로 분리) | FE |
| `feat/fe-item-pages` | 공통(물품 api·queries·목·공개 API) + SCR-03, 04, 05 (SCR-06 등록·수정·삭제는 `feat/fe-item-form`으로 분리) | FE |
| `feat/fe-item-form` | SCR-06 물품 등록·수정 + 물품 삭제(REQ-008, 상세·`/me/items` 삭제 메뉴) + 좌표 입력·권장 보증금 (`feat/fe-item-pages`에서 분리, 머지 후 착수) | FE |
| `feat/fe-rental-pages` | SCR-07, 08 | FE |
| `feat/fe-my-pages` | SCR-12 (`feat/fe-rental-pages`에서 분리, 머지 후 착수) | FE |
| `feat/fe-region` | SCR-02 (`feat/fe-auth-region`에서 분리, 머지 후 착수) | FE |

### 2순위 (착수 시 확정)
| 브랜치 | REQ | 담당 |
| --- | --- | --- |
| `feat/payment-core` | 024, 025, 취소 시 환불(015 연계), 017 인계 없음 자동 취소, 결제 만료·환불 재시도·조정 배치 | A |

`feat/payment-deposit`(049 + 관리자 보증금 확정 API) · `feat/rental-overdue`(023·031) · `feat/chat-core`(026·027) · `feat/chat-list`(028·029) · `feat/notification`(032) · `feat/review`(034) · `feat/rental-extension`(038) · `feat/payment-settlement`(039·033) · `feat/ai-compare`(040) · `feat/member-extra`(021·036·037) · `feat/item-wish-recent`(022·035) · 프론트 `feat/fe-*`

## 3. 작업 한 사이클

```
[시작]  develop 최신화 → 작업 브랜치 생성
[작업]  작은 단위로 커밋 (테스트 통과할 때마다)
[중간]  develop에 새 머지가 생기면 내 브랜치에 반영
[마무리] 테스트 → openapi.json·문서 갱신 → push → PR 생성
[확인]  작성자가 PR 화면에서 변경 파일·체크리스트 확인 (API 변경 PR만 관련 팀원에게 확인 요청)
[머지]  작성자가 GitHub에서 Squash merge → 팀 채팅에 머지 사실 공유 → 로컬 정리
```

### 명령어 대응표 (에이전트가 실행하는 것)

| 단계 | 명령 | 설명 |
| --- | --- | --- |
| 시작 | `git switch develop && git pull` | 최신 develop 받기 |
| 시작 | `git switch -c feat/rental-reserve` | 새 브랜치 만들고 이동 |
| 작업 | `git add <파일들>` → `git commit -m "feat(rental): ... (REQ-013)"` | 변경 저장 |
| 중간 | `git fetch origin && git rebase origin/develop` | develop 변경을 내 브랜치 밑으로 깔기 |
| 마무리 | `git push -u origin feat/rental-reserve` | 내 브랜치를 GitHub에 올리기 |
| 마무리 | `gh pr create --base develop --fill` (또는 PR 링크 안내) | PR 만들기 |
| 머지 후 | `git switch develop && git pull && git branch -d feat/rental-reserve` | 정리 |

## 4. 에이전트 행동 규칙

### 스스로 실행하고 한 줄로 알린다
- `git status`, `git diff`, `git log`, `git fetch` (조회)
- 작업 시작 시 브랜치 확인 후, `develop`이나 `main`에 있으면 **위 계획표에서 맞는 브랜치를 골라 생성·이동** (계획표에 없으면 이름을 제안하고 물어본다)
- 테스트가 통과한 의미 있는 단위마다 커밋 (커밋 메시지 규칙 준수)
- 내 작업 브랜치를 origin에 push (일반 push만)
- 작업 브랜치에서 `git rebase origin/develop` — **충돌이 없을 때만**

### 실행 전에 멈추고 사용자에게 묻는다
- 충돌 발생 시: 충돌 파일과 양쪽 변경 내용을 설명하고 어떻게 합칠지 제안 → 승인 후 해결. 충돌 중 상태로 방치하지 않는다(`git rebase --abort`로 되돌리는 방법도 안내)
- 이미 push한 브랜치를 rebase한 뒤의 push (`--force-with-lease` 필요) — 왜 필요한지 설명 후 승인받기
- PR 생성 (내용 미리 보여주고 승인받기)
- 커밋되지 않은 변경이 있는 상태에서 브랜치 전환
- 남이 만든 브랜치에 커밋

### 절대 하지 않는다
- `main`, `develop`에 직접 커밋·push
- `git push --force` (옵션 없는 강제 push), `git reset --hard`, `git clean -fd`, 원격 브랜치 삭제
- PR 머지 (작성자가 GitHub에서 직접 누른다)
- `.env`, 키, `build/`, `node_modules/`, Q클래스 등 `.gitignore` 대상 커밋. `git add .` 대신 파일을 지정하고, 커밋 전 `git status`로 확인

### 사용자에게 먼저 언질을 줄 타이밍
- 작업을 시작했는데 마지막 `develop` 반영 후 하루 이상 지났을 때: "develop에 새 변경이 있어 먼저 반영하겠습니다"
- 브랜치가 3일 이상 열려 있거나 변경 파일이 30개를 넘을 때: "PR로 나눠 올리는 게 좋겠습니다"
- 작업 묶음의 REQ가 다 끝났을 때: "이제 PR을 올릴 때입니다" + 체크리스트 확인
- Flyway 마이그레이션을 추가할 때: `docs/erd.md` "마이그레이션 번호" 표를 develop과 원격 작업 브랜치(`git fetch origin` 후 각 `origin/*`의 erd.md) 양쪽에서 확인한 결과를 알린다. 고른 번호는 **브랜치 첫 커밋에서 표에 행(상태 "작업 중")으로 넣고 바로 push**해 선점한다. PR을 만들 때 상태를 "머지"로 바꾸는 커밋을 넣는다(develop에 들어간 행은 곧 머지된 것이다)

## 5. 커밋 메시지

```
{type}({scope}): {요약} ({REQ ID})

type:  feat | fix | refactor | test | docs | chore | perf | style(포맷만 변경)
scope: member | item | rental | payment | chat | notification | review | ai | admin | global | fe
예:    feat(rental): 기간 겹침 검사와 분산 락 적용 (REQ-013, REQ-014)
       fix(item): 피드 커서 경계 중복 수정 (REQ-009)
       chore(global): QueryDSL 설정 추가
```
요약은 한국어, 50자 내외. 한 커밋에는 한 가지 변경.

## 6. PR 규칙

- 제목 = 대표 커밋 메시지 형식. 본문은 `.github/pull_request_template.md`를 채운다.
- base는 항상 `develop` (마일스톤 머지만 `develop` → `main`).
- **승인(Approve) 없이 작성자가 직접 머지한다.** GitHub은 자기 PR을 승인할 수 없으므로 보호 규칙에서 승인 요구를 끈다. PR 자체는 반드시 거친다(변경 기록·체크리스트·blame 추적용).
- **예외 — 다른 사람에게 영향이 가는 PR은 머지 전에 확인 요청**: API 경로·응답 형태 변경(→ 프론트 담당), 공통 코드(`global/`, `ErrorCode`, Security 설정) 변경(→ 다른 백엔드), Flyway 마이그레이션 추가(→ 다른 백엔드). Reviewers에 지정하고 팀 채팅에 링크를 공유한 뒤, 반나절 안에 이견이 없으면 머지한다.
- 머지 방식: **Squash and merge**. 머지 후 원격 브랜치는 자동 삭제(설정), 팀 채팅에 "○○ 머지했어요" 한 줄 공유.
- PR 올리기 전 체크리스트: 테스트 통과, `docs/openapi.json` 재생성(API 변경 시), `docs/api.md`·`erd.md`·`roadmap.md` 갱신, Flyway 번호 확인(erd.md 번호 표 상태를 "머지"로), 비밀값 미포함.

## 7. GitHub 초기 설정 (팀장 1회)

1. 레포 생성 → `main` 브랜치에 이 문서 세트 push → `develop` 브랜치 생성·push
2. Settings → General → 기본 브랜치를 `develop`으로
3. Settings → Rules → Rulesets → New branch ruleset: 이름 `protect-main-develop`, **Enforcement status `Active`**, Bypass list 비움, Target에 `main`·`develop` 패턴 추가. Rules: Restrict deletions, Block force pushes, Require a pull request before merging(Required approvals `0`, Allowed merge methods `Merge, Squash`). Require linear history는 끈다(`develop`→`main` 일반 merge가 막힘). 무료 계정은 Public 레포에서만 적용된다.
4. Settings → General → Pull Requests: "Allow merge commits"와 "Allow squash merging" 켜고 "Allow rebase merging"은 끔(작업 PR은 Squash, `develop`→`main`은 Merge), "Automatically delete head branches" 켜기
5. 팀원 2명 Collaborator 초대
5-1. CI(`.github/workflows/ci.yml`)가 한 번 돈 뒤, 룰셋에 "Require status checks to pass"를 켜고 `backend`, `frontend` 체크를 추가한다. 승인 없이 작성자가 머지하는 대신 테스트가 깨진 PR은 머지되지 않게 하는 장치다(검사 대상이 없어 건너뛴 체크는 통과로 처리된다)
6. 각자 PC: `git config --global user.name`/`user.email` 설정, `gh auth login`(GitHub CLI, PR 생성용)
