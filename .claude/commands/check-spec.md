---
description: 브랜치(기본: 현재 브랜치)의 변경 사항, 또는 지정 REQ가 명세·문서와 일치하는지 검토한다
argument-hint: "[브랜치명] [REQ-xxx]"
---

먼저 `git fetch origin`을 하고, 인자로 브랜치가 주어지면 그 브랜치를, 없으면 현재 브랜치를 대상으로 `git diff origin/develop...origin/<브랜치>`를 기준으로 검토한다(REQ가 주어지면 해당 REQ 관련 코드만). 체크아웃하지 않는다. 파일 내용은 `git show origin/<브랜치>:<경로>`로 읽는다. 아직 푸시하지 않은 현재 브랜치를 검토할 때만 `origin/` 없이 로컬 브랜치를 쓴다. 코드는 수정하지 말고 보고만 한다.

확인 항목:
문서는 diff에 해당하는 섹션만 읽는다. 상태 전이를 건드리지 않았으면 state-machines.md는 읽지 않는다.
1. 상태 전이가 `docs/state-machines.md` 전이 표와 일치하는가 (없는 전이, 누락된 가드, 이력 기록 누락)
2. 정책 수치가 `docs/policies.md`와 같고 하드코딩되지 않았는가
3. 에러 응답의 HTTP 상태·에러 코드가 `docs/architecture.md` 규칙과 도메인 문서의 예외 항목을 따르는가
4. 소유자·당사자 권한 검증이 서버에 있는가
5. 외부 API 호출이 트랜잭션 밖에 있는가, 분산 락과 트랜잭션 순서가 맞는가
6. `docs/api.md`, `docs/erd.md`가 코드와 일치하는가. API가 바뀌었는데 `docs/openapi.json`이 재생성되지 않았거나, 새 컨트롤러에 `@Tag`·`@Operation`(REQ ID 포함)·`@ApiErrorCodes`·DTO `@Schema`가 빠졌는가
7. 도메인 문서의 "테스트 포인트"가 테스트로 커버되는가
8. 현재 단계보다 높은 순위의 기능을 미리 구현하지 않았는가
9. `docs/prompts/<브랜치명>/notes.md`에 `## 요약`이 있으면 diff와 맞는가(구현한 API, 주요 파일 경로, 남은 일)

프론트(`frontend/`) 변경이면 추가로 확인한다 (기준: `frontend/CLAUDE.md`):
- 토큰을 localStorage·sessionStorage에 저장하지 않는가 (AT는 authStore 메모리만)
- 401 재발급: single-flight, 원 요청 1회만 재시도, 재발급 호출은 인터셉터 밖, `/auth/login`·`/auth/logout`·`/auth/reissue` 401은 재발급하지 않음
- 코드에 절대 주소(`http://localhost` 등)가 없는가
- MSW가 운영 번들에 포함되지 않는가 (`import.meta.env.DEV` + `VITE_USE_MOCK` 분기 안의 동적 import)
- 현재 순위보다 높은 순위 화면을 "준비 중" 자리표시 이상으로 구현하지 않았는가
- 이름 규칙(`docs/conventions.md` §5)을 지키는가

결과는 [불일치 / 누락 / 제안] 세 구분으로 파일:라인과 함께 짧게 정리한다.
