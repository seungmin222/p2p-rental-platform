---
description: 현재 브랜치 작업을 마무리하고 PR을 준비한다
---

`docs/git-workflow.md` §6 PR 규칙에 따라 진행한다. 각 단계에서 실행한 명령과 의미를 한 줄씩 설명한다.

1. 현재 브랜치가 `main`/`develop`이면 중단하고 알린다.
2. 포맷터 실행(backend: `./gradlew spotlessApply`, frontend: `npm run format`) — 변경이 생기면 `style(...)` 커밋으로 분리. 이어서 테스트 실행(backend: `./gradlew test`, frontend: `npm run lint && npm run test && npm run build`). 실패하면 원인을 보고하고 멈춘다. (도구 설정이 아직 없으면 건너뛰고 알린다)
3. PR 체크리스트(`.github/pull_request_template.md`)를 하나씩 확인한다. API가 바뀌었으면 `docs/openapi.json` 재생성, 문서 갱신 누락이 있으면 반영해 커밋한다.
4. `git fetch origin && git rebase origin/develop`. 충돌이 나면 멈추고 설명한다. 이미 push한 브랜치라 강제 push가 필요하면 이유를 설명하고 승인받는다.
5. `git push -u origin <브랜치>`.
6. PR 제목과 본문(템플릿 채운 것)을 보여주고 승인받은 뒤 `gh pr create --base develop`으로 생성한다. `gh`가 없거나 로그인 안 되어 있으면 GitHub 웹에서 PR 만드는 링크와 붙여 넣을 본문을 준다.
7. 이 PR이 `docs/git-workflow.md` §6의 "확인 요청 예외"(API 변경, 공통 코드, Flyway 추가)에 해당하는지 판단해 알린다. 해당하면 누구에게 확인을 요청할지 안내하고, 아니면 "변경 파일을 한 번 훑어본 뒤 GitHub에서 Squash and merge를 누르면 됩니다"라고 안내한다. 머지 버튼은 사용자가 직접 누른다. 머지 후 팀 채팅에 공유하라고 덧붙인다.
8. 마지막으로 안내한다: "머지(와 확인 요청 반영)가 끝나면 이 세션은 닫고, 다음 작업은 새 세션에서 `/start-work`로 시작하세요. 리뷰 반영이 필요하면 이 세션에서 계속하면 됩니다."
