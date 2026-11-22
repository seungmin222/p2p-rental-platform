# feat/fe-ai-compare 결정 기록 (REQ-040 프론트)

## 요약
- 백엔드 API 변경 없음. `GET /api/rentals/{id}/inspections` 반납 기록의 `aiResult`를 SCR-08에 표시: 반납 기록 아래 `AiCompareCard`(대여자·제공자), 제공자 상태 확인 모달·이상 시트·차감 제안 시트 맨 위 `AiCompareSummary`.
- 새 파일: `features/rental/aiCompare.js`(표시 판단·문구 순수 함수, 테스트), `components/AiCompareCard.jsx`, 목 `mocks/aiCompare.js`(응답 변환·진행 흉내). 상수 `AI_CHANGE_TYPE`·`AI_COMPARE_PROCESSING_TIMEOUT_MINUTES`.
- 결과 대기 판단은 임시(open-issues #59): aiResult null + 반납 후 10분 안 = "비교 준비 중". 백엔드가 적재 시점에 상태를 기록하면 `getAiCompareView` 한 곳만 고친다.
- 확인 못 한 것: 브라우저 확인은 못 했다(앱 브라우저 패널이 MSW 서비스 워커를 등록하지 못함, 앞 브랜치와 같음). 단위 테스트·lint·build만 통과.
- 남은 일: 검증 실험(`docs/experiments/ai-compare.md`)은 여전히 미실행. 확신도는 표시하지 않음(실험 뒤 필요하면 단계 표시로).

## 결정
- 확신도(confidence) 숫자는 보이지 않는다: 검증 실험 전이라 값의 의미가 확인되지 않았고, 숫자는 "판정"처럼 읽힌다(사용자 결정).
- 대여자에게도 상세 카드를 보인다: API가 당사자 모두에게 주고 `AI_COMPARE_DONE` 알림도 양측에 간다(#48). 요약(근거 보기)은 원래 제공자만 여는 모달·시트에만 둔다(사용자 결정).
- aiResult null 처리는 안 A(사용자 결정): 반납 직후 응답이 거의 항상 null이라 바로 "결과 없음"이면 틀린 화면이 보이고 재조회도 안 돈다. 시간창은 새 값을 만들지 않으려고 `ai.compare.processing-timeout-minutes`(10분)를 빌려 썼다 — 그 안에 워커가 맡지 못하면 백엔드도 사실상 결과가 없다. 근본 해결은 백엔드 제안으로 #59에 적었다.
- 갱신: 준비 중·비교 중이면 inspections를 5초(기존 `SHORT_STALE_TIME`)마다 다시 받는다. DONE은 `AI_COMPARE_DONE` 실시간 알림이 `['rentals', id]` 무효화로 inspections도 다시 받게 하지만, FAILED·LIMIT_EXCEEDED는 알림이 없어(#48) 폴링이 필요하다. 백엔드가 10분 뒤 FAILED로 내리므로(#46) 폴링은 저절로 끝난다.
- 고지("판정이 아니라 참고 자료 · 조명·각도 차이로 잘못 볼 수 있어요")는 모든 상태에 붙인다: 결과 없음에서도 AI의 역할을 같은 문장으로 알린다.
- 변화 있음 배지는 amber(danger 아님): 판정이 아니라서 위험 색을 쓰지 않는다.
- 미반납 신고 기록(반납 사진 없음, aiResult null)에는 카드를 그리지 않고 미반납 건 차감 시트에도 요약을 넣지 않는다: 비교할 반납 사진이 없다.
- 사진별 위치(regions)는 상세 카드에만, 요약에는 유형·설명만: 모달·시트 안이 좁고, 사진은 상세 반납 기록에서 이미 크게 볼 수 있다. `imageIndex`는 반납 사진 순서(백엔드 ComparePrompt·AiCompareParser 확인), 범위 밖이면 메모만.
