# chore/gitattributes-eol 결정 기록

- **`backend/.gitattributes`는 그대로 둔다**: 루트 규칙과 겹치지만 Gradle wrapper를 갱신할 때 다시 생길 수 있어, 지워도 이득이 적다.
- **`git add --renormalize .` 결과: 변경 없음**: develop 인덱스의 텍스트 파일이 이미 전부 LF였다.
- **binary 확장자는 `jar`·`png`·`woff2`만**: `git ls-files --eol`에서 `-text`로 나온 확장자만 넣었다.
- **`*.cmd`도 CRLF로 지정**: 지금 저장소에는 `backend/gradlew.bat`뿐이지만, Windows 스크립트라는 같은 이유로 함께 둔다.
