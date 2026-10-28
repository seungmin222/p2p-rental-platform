## 관련 요구사항
<!-- 예: REQ-013, REQ-014 / SCR-07 -->

## 변경 내용
<!-- 무엇을 왜 바꿨는지 3줄 이내 -->

## 명세와 다르게 구현한 부분
<!-- 없으면 "없음". 있으면 이유와 docs/open-issues.md 반영 여부 -->

## 체크리스트
- [ ] 테스트 통과 (`./gradlew test` / `npm run lint && npm run test && npm run build`)
- [ ] 도메인 문서의 "테스트 포인트" 커버
- [ ] API 변경 시 `docs/openapi.json` 재생성
- [ ] `docs/api.md` · `docs/erd.md` · `docs/roadmap.md` 갱신
- [ ] Flyway 마이그레이션 번호가 develop 최신 다음 번호
- [ ] 비밀값·`.env`·빌드 산출물 미포함

## 확인 요청 필요 여부
- [ ] API 경로·응답 형태 변경 → 프론트 담당 확인
- [ ] 공통 코드(global/, ErrorCode, Security) 변경 → 다른 백엔드 확인
- [ ] Flyway 마이그레이션 추가 → 다른 백엔드 확인
<!-- 하나도 해당 없으면 작성자가 바로 머지 -->

## 메모
<!-- 특히 공유하고 싶은 부분 -->
