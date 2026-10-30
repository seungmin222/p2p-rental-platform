---
description: REQ ID 하나를 명세에 맞춰 구현한다 (예: /implement-req REQ-013)
argument-hint: REQ-xxx
---

$ARGUMENTS 를 구현한다.

1. 루트 `CLAUDE.md`의 매핑표에서 $ARGUMENTS 가 속한 도메인 문서를 찾아 해당 REQ 섹션을 읽는다. 함께 `docs/state-machines.md`, `docs/policies.md`, `docs/roadmap.md`(현재 단계·의존 REQ 완료 여부), `docs/open-issues.md`(관련 미결정 사항)를 확인한다.
2. 의존하는 선행 REQ가 미완료이거나, 관련 미결정 사항 중 "제안대로 진행" 표시가 없는 것이 있으면 **구현 전에 나에게 질문**한다.
3. 작업 위치에 따라 `backend/CLAUDE.md` 또는 `frontend/CLAUDE.md`의 규칙과 작업 체크리스트를 따른다.
4. 구현 계획(변경할 파일, 엔티티·API, 테스트 목록)을 짧게 보여주고 진행한다.
5. 도메인 문서의 "테스트 포인트"를 테스트로 작성하고 통과시킨다. 이 단계에서는 바꾼 부분의 테스트만 돌린다(backend: `./gradlew test --tests '<클래스>'`, frontend: `npx vitest run <파일>`). 전체 테스트는 `/finish-work`에서 한 번 돌린다. 빌드 로그·테스트 리포트 HTML을 통째로 읽지 않는다.
6. 끝나면 API가 바뀐 경우 `docs/openapi.json`을 재생성하고(`./gradlew generateOpenApiDocs`, 실행이 안 되면 나에게 알린다), `docs/api.md`, `docs/erd.md`, `docs/roadmap.md` 진행 상태를 갱신하고, 명세와 다르게 구현한 부분이 있으면 요약해 보고한다.
