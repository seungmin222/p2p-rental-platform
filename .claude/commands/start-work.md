---
description: 작업 브랜치를 준비한다 (예: /start-work feat/rental-reserve 또는 /start-work REQ-013)
argument-hint: [브랜치명 또는 REQ ID]
---

`docs/git-workflow.md`의 규칙에 따라 작업을 시작할 준비를 한다. 각 단계에서 실행한 명령과 의미를 한 줄씩 설명한다.

0. 이 세션에서 이미 다른 브랜치 작업을 했다면, 새 세션에서 시작하는 게 좋다고 한 줄로 제안한다. 사용자가 그대로 진행하라고 하면 계속한다.
1. `git status`로 커밋 안 된 변경이 있는지 확인한다. 있으면 멈추고 어떻게 할지(커밋 / 임시 보관 stash / 취소) 묻는다.
2. `$ARGUMENTS`가 브랜치 이름이면 그 브랜치로, REQ ID면 `docs/git-workflow.md` §2 계획표에서 해당 브랜치를 찾는다. 비어 있으면 다음 작업을 제안하지 않고 멈춘 뒤, 프로젝트 채팅에서 브랜치를 정해 `/start-work <브랜치명>`으로 다시 부르라고 안내한다.
3. 그 브랜치가 이미 있으면 이동 후 `git fetch origin && git rebase origin/develop`(충돌 시 멈추고 설명). 없으면 `git switch develop && git pull` 후 새로 만든다.
4. 진행 상황을 파악한다: 완료는 `docs/roadmap.md`(develop 기준), 진행 중은 `git fetch origin` 후 `git branch -r`과 `gh pr list`(gh가 없으면 원격 브랜치만). 이 브랜치가 마이그레이션을 만들 예정이면 `docs/erd.md` "마이그레이션 번호" 표를 develop과 각 원격 작업 브랜치(`git show origin/<브랜치>:docs/erd.md`)에서 확인해 다음 번호를 알린다. 코드를 전부 스캔하지 않는다.
5. 이 브랜치에 포함된 REQ 목록, 각 도메인 문서 위치, 의존하는 선행 작업의 상태(완료 / 누가 진행 중 / 미착수)를 요약한다. 완료된 선행 작업은 그 브랜치의 `docs/prompts/<브랜치명>/notes.md` 맨 위 `## 요약`으로 무엇이 구현됐는지 확인한다. 선행 작업이 다른 사람 담당이고 미머지라면 `backend/CLAUDE.md`의 "테이블·엔티티가 필요할 때" 선택지를 함께 안내한다.
