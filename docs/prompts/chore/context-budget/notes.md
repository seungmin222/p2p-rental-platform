# chore/context-budget 결정 기록

- **Gradle 전체 테스트 예시에 `echo "exit=${PIPESTATUS[0]}"`를 붙였다.** `| tail`만 쓰면 종료 코드가 tail의 0이 되어 실패가 가려진다. PIPESTATUS는 bash 전용이라 bash 명령으로 표시했다.
- **Gradle 실패 확인처를 `build/reports`에서 `build/test-results/test/TEST-*.xml`의 `grep -A 15 '<failure'`로 바꿨다.** `build/reports`는 HTML이라 "리포트 HTML을 읽지 않는다"와 겹치고, XML 전체는 표준 출력 로그까지 담아 길다.
- **`backend/CLAUDE.md` 체크리스트 3번을 "바꾼 부분 테스트 통과, 전체는 PR 직전 한 번"으로 고쳤다.** REQ마다 전체 테스트를 요구하던 문구가 새 규칙과 충돌했다.
- **`/clear` 제안은 코드와 notes.md를 함께 커밋한 뒤로 적고, "브랜치 하나 = 세션 하나"의 예외로 명시했다.** notes.md는 코드와 같은 커밋에 넣는 규칙이고, `/clear`는 같은 브랜치를 이어 가는 것이다. REQ 중간에 대화가 길어진 경우의 새 세션 제안 항목은 그대로 둔다.
- **openapi 명령 예시는 루트 `CLAUDE.md` 규칙 6에만 적고 `frontend/CLAUDE.md`는 가리키기만 한다.** 두 파일이 함께 로드되므로 중복하면 컨텍스트만 늘어난다.
- **프론트 테스트 규칙은 176행이 아니라 PR 조건이 있는 줄 바로 아래에 넣었다.** develop 기준 176행은 목 계정 설명이다.
