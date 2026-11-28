# claude/project-thread-s4zk23 결정 기록 (추적성 키트 설치)

## 요약
- agent-traceability-kit 설치: `.claude/settings.json`(플러그인), `.github/workflows/traceability.yml`, `.traceability.json`, CLAUDE.md "결정 추적성", git-workflow.md §5, `/check-spec`·`/finish-work`·PR 템플릿 연결.
- 다음 브랜치(`feat/admin-process`, REQ-057)부터 새 결정 형식을 쓴다.

## 설치 범위
- **D-project-thread-s4zk23-01** [사용자] 키트는 p2p 저장소에 규칙을 직접 넣지 않고 별도 저장소의 플러그인·Action으로 설치한다. 다른 저장소에서도 같은 규칙을 쓰기 위해서다.
- **D-project-thread-s4zk23-02** [에이전트] 예전 notes.md는 고칠 때만 새 형식으로 맞춘다(`scope: changed`). 기존 결정 항목 239건을 한꺼번에 바꾸면 ID·주체를 사후에 추측해 붙이게 되어 기록의 뜻이 흐려진다.
- **D-project-thread-s4zk23-03** [에이전트] REQ 검사 예외는 머지·되돌리기와 `chore(traceability)` 커밋뿐이다. 키트 설정 변경은 요구사항에 대응하지 않고, `style` 커밋까지 예외로 두면 기준 2("PR의 모든 커밋")가 약해진다.
- **D-project-thread-s4zk23-04** [에이전트] Action은 키트 `@main`을 쓴다. 아직 릴리스 태그가 없다. 태그를 만들면 고정 버전으로 바꾼다.
- **D-project-thread-s4zk23-05** [에이전트] 기존 `/check-spec`·`/finish-work`는 그대로 두고 키트 커맨드를 부르는 한 줄만 더한다. 프로젝트 고유 검토 항목과 키트 규칙을 한 파일에 섞지 않기 위해서다.
