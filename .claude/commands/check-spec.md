---
description: 현재 변경 사항(또는 지정 REQ)이 명세·문서와 일치하는지 검토한다
argument-hint: [REQ-xxx]
---

`git diff develop...HEAD`(인자로 REQ가 주어지면 해당 REQ 관련 코드)를 기준으로 검토한다. 코드는 수정하지 말고 보고만 한다.

확인 항목:
1. 상태 전이가 `docs/state-machines.md` 전이 표와 일치하는가 (없는 전이, 누락된 가드, 이력 기록 누락)
2. 정책 수치가 `docs/policies.md`와 같고 하드코딩되지 않았는가
3. 에러 응답의 HTTP 상태·에러 코드가 `docs/architecture.md` 규칙과 도메인 문서의 예외 항목을 따르는가
4. 소유자·당사자 권한 검증이 서버에 있는가
5. 외부 API 호출이 트랜잭션 밖에 있는가, 분산 락과 트랜잭션 순서가 맞는가
6. `docs/api.md`, `docs/erd.md`가 코드와 일치하는가. API가 바뀌었는데 `docs/openapi.json`이 재생성되지 않았거나, 새 컨트롤러에 `@Tag`·`@Operation`(REQ ID 포함)·`@ApiErrorCodes`·DTO `@Schema`가 빠졌는가
7. 도메인 문서의 "테스트 포인트"가 테스트로 커버되는가
8. 현재 단계보다 높은 순위의 기능을 미리 구현하지 않았는가

결과는 [불일치 / 누락 / 제안] 세 구분으로 파일:라인과 함께 짧게 정리한다.
