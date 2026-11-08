# AI 물품 상태 비교 검증 (REQ-040)

`docs/domains/ai.md` REQ-040 "검증(목표)"의 결과표다. **아직 실행하지 않았다**(feat/ai-compare 브랜치는 표 틀과 실행 도구만 만들었다). 샘플 사진과 API 키가 준비되면 아래 절차로 돌려 채운다.

## 목표

| 지표 | 목표 | 결과 |
| --- | --- | --- |
| 스키마 준수율 (재시도 없이 첫 응답 기준) | ≥ 99% | 미측정 |
| 파손 재현율 (파손 쌍 중 `changed=true`) | ≥ 80% | 미측정 |
| 응답 시간 (쌍당) | 30초 이내 | 미측정 |
| 조명 변화 오판율 (조명 쌍 중 `changed=true`, 참고) | 낮을수록 좋음 | 미측정 |

## 샘플 세트

| 묶음 | 쌍 수 | 내용 |
| --- | --- | --- |
| 정상 | 20 | 인계·반납 사진이 같은 상태 |
| 파손 | 20 | 반납 사진에 흠집·얼룩·부품 누락·파손 |
| 조명 변화 | 20 | 상태는 같고 조명·각도만 다름 |

폴더 구조: `<샘플 폴더>/<쌍 이름>/handover-*.jpg|png`, `<쌍 이름>/return-*.jpg|png`. 쌍 이름 앞에 묶음을 붙인다(예: `normal-01`, `damage-03`, `light-12`). 사진은 저장소에 올리지 않는다.

## 실행

```bash
cd backend
ANTHROPIC_API_KEY=... AI_COMPARE_SAMPLE_DIR=/path/to/samples ./gradlew test -PaiLive --tests '*ClaudeAiClientLiveTest'
```

- `ClaudeAiClientLiveTest`(`@Tag("ai-live")`)가 쌍마다 `쌍 이름 / 소요 ms / 스키마 준수 / 결과`를 출력한다. 기본 `./gradlew test`에서는 제외된다.
- 과금된다: 60쌍 × 호출 1회. 월 한도 카운터(`ai:usage:*`)는 건드리지 않는다(실험은 서버를 거치지 않음).
- 모델·프롬프트: `app.ai.model`(claude-haiku-4-5), `ai/client/ComparePrompt`. 사진은 서버와 달리 축소하지 않고 그대로 보낸다.

## 결과 기록

| 날짜 | 모델 | 쌍 수 | 스키마 준수 | 파손 재현율 | 조명 오판 | 평균/최대 응답 | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |
