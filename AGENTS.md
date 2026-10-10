# AGENTS.md

이 저장소의 에이전트 규칙은 Claude Code 기준으로 작성되어 있다. 사용하는 에이전트와 관계없이 동일하게 따른다.

1. 작업 전에 루트 `CLAUDE.md`를 읽는다. 문서 지도와 필수 규칙이 있다.
2. `backend/` 작업 시 `backend/CLAUDE.md`, `frontend/` 작업 시 `frontend/CLAUDE.md`를 함께 읽는다.
3. `CLAUDE.md`에 나오는 슬래시 커맨드(`/start-work`, `/implement-req`, `/check-spec`, `/finish-work`)는 `.claude/commands/<이름>.md`에 적힌 절차를 그대로 수행하는 것으로 대신한다. 파일 안의 `$ARGUMENTS`는 사용자가 함께 준 값(브랜치명, REQ ID 등)으로 바꿔 읽는다.
4. 규칙을 바꿀 때는 이 파일이 아니라 `CLAUDE.md`와 `docs/`를 수정한다. 이 파일에 규칙을 복사하지 않는다.
