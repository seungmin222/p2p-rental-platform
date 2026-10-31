# chore/gitattributes-eol 결정 기록

## 요약

- 구현한 API: 없음 (설정 파일과 문서만 변경).
- 새 파일: 루트 `.gitattributes`.
- 남은 일: 다른 팀원도 pull 후 CRLF 파일을 같은 방법으로 다시 받아야 함. `git status`가 깨끗한 상태에서 `git ls-files --eol`의 `w/crlf` 파일(`backend/gradlew.bat` 제외)을 지우고 `git checkout --`으로 받는다.

## 결정

- **`backend/.gitattributes`는 그대로 둔다**: 루트 규칙과 겹치지만 Gradle wrapper를 갱신할 때 다시 생길 수 있어, 지워도 이득이 적다.
- **`git add --renormalize .` 결과: 변경 없음**: develop 인덱스의 텍스트 파일이 이미 전부 LF였다.
- **binary 확장자는 `jar`·`png`·`woff2`만**: `git ls-files --eol`에서 `-text`로 나온 확장자만 넣었다.
- **`*.cmd`도 CRLF로 지정**: 지금 저장소에는 `backend/gradlew.bat`뿐이지만, Windows 스크립트라는 같은 이유로 함께 둔다.
