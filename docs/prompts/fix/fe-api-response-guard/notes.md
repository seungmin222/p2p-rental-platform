# fix/fe-api-response-guard 결정 기록

## 요약
- open-issues #30 해결: `apiClient`가 2xx에서 `success === true`만 통과시키고 나머지는 `CLIENT_INVALID_RESPONSE`로 거부한다. 새 API 없음(프론트만).
- 바꾼 파일: `frontend/src/shared/api/client.js`, `ApiError.js`(`createInvalidResponseError`), `client.test.js`. 새 파일은 이 notes.md뿐이다.
- 남은 일: open-issues #31(일시적 응답 이상에도 로그아웃할지 결정), 실제 백엔드·프록시 응답 확인.
- 다음 브랜치: 204 등 본문 없는 성공 응답 API를 만들면 이 인터셉터가 거부하므로 함께 고친다.

## 결정

- **2xx `success: false`**: 본문의 코드를 쓰지 않고 `CLIENT_INVALID_RESPONSE`로 거부한다. 백엔드 실패 응답은 `ErrorCode`의 상태 코드로만 나가므로(4xx·5xx) 2xx 실패 본문은 규약 위반이고, 우리 서버 응답이라고 믿고 화면 분기를 태우지 않는다.
- **본문 없는 성공 응답(204 등)**: 지금은 막는다. openapi.json의 2xx 36개와 컨트롤러가 모두 `ApiResponse`를 쓰고 `noContent`가 없다. 204 API가 생기면 이 인터셉터도 같이 고쳐야 한다.
- **`{ success: true }`(data 없음)**: `undefined`로 통과하며 테스트로 고정하지 않는다. 백엔드는 null 필드를 생략하는 설정이 없어 항상 `data`를 보내므로 생기지 않는 입력이고, 테스트로 고정하면 계약에 없는 형태를 허용 동작처럼 보이게 한다.
- **조회 재시도**: 거부된 조회는 status가 200이라 `NO_RETRY_STATUSES`에 걸리지 않아 1회 재시도한다. 일시적인 프록시 오류에 맞는 동작이라 그대로 둔다(사용자 확인).
- **재발급**: `reissue.js`는 이번 범위에서 고치지 않고 open-issues #31에 올렸다.
- **테스트 판별력**: 성공 핸들러의 오류를 실패 핸들러로 넘기기만 한 변이는 테스트가 통과한다. ApiError에 `response`·`config`가 없어 재발급을 타지 않고 그대로 나가 동작이 같아서, 테스트를 다시 설계하지 않았다. 넘어간 오류로 재발급하는 변이는 거부 4건, 토큰을 지우고 로그인으로 보내는 변이는 재시도 사례까지 5건이 실패했다.

## 브라우저 확인 결과

- **조건**: 2026-10-31, 목 모드. API 호출은 화면 조작이 아니라 페이지에서 `apiClient`를 직접 불렀고, 실제 백엔드·프록시 응답으로는 확인하지 않았다.
- **회귀**: 재발급으로 로그인 복원, 홈 피드, 물품 상세 표시에 이상이 없다.
- **거부**: 개발 서버의 실제 200 HTML을 GET·POST → 둘 다 `CLIENT_INVALID_RESPONSE`, status 200.
- **통과·실패 응답**: 동네 삭제(`data: null`) → null, 동네 추가(201) → data, 없는 물품 → `ITEM_NOT_FOUND` 404.
