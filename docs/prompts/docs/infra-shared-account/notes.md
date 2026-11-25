# docs/infra-shared-account 결정 기록

## 요약

- API·코드 변경 없음. 문서만: `infra/README.md` 2·3·4·6·9절, `infra/.env.prod.example`, 브랜치 표(roadmap·git-workflow).
- 학교 공용 AWS 계정 기준으로 바꿈: 운영진 인스턴스 프로필 `SafeInstanceProfile-inha-capstone-19`(버킷 권한은 운영진 요청), EC2 `inha-capstone-19-1`·`-2`, 보안 그룹 `inha-capstone-19-sg` 1개, S3 `inha-capstone-19-rental-files`, RDS 프리 티어.
- 남은 일: 운영진에게 버킷 권한 요청, CloudFront 생성 시도(막히면 운영진 요청), 이후 런북 6·8절대로 첫 배포·확인.

## 결정

- **보안 그룹 1개 + 자기 참조 "모든 TCP" 한 줄**: 계정에 그룹이 하나만 있고, 포트별 5줄이면 CloudFront 접두사 목록 55 + SSH 1 + 5 = 61로 기본 한도 60을 넘는다. 그룹을 단 리소스가 서버 2대·RDS뿐이라 열리는 포트는 나눈 경우와 사실상 같다.
- **RDS 기본 파라미터 그룹 허용**: 코드 확인 결과 마이그레이션·코드에 `DEFAULT CURRENT_TIMESTAMP`·`NOW()`가 없고 시각은 JPA Auditing(`Clock`)이 채운다. 테이블 28개 모두 utf8mb4_unicode_ci 지정. 그래서 `forceConnectionTimeZoneToSession=true`(`.env`의 `SPRING_DATASOURCE_URL`)는 필수가 아닌 선택 항목으로 적었다. DB 기본 collation·`flyway_schema_history`는 0900_ai_ci가 되지만 앱 테이블엔 영향 없다.
- **새 리소스 이름 접두사 `inha-capstone-19-`**: S3는 필수 규칙이고, CloudFront 함수·파라미터 그룹도 공용 계정에서 구분되도록 같은 접두사로 통일.
- **AWS Budgets 항목 삭제**: 계정 단위 설정은 권한이 없다. EIP 해제·IAM 정리는 운영진 요청으로.
