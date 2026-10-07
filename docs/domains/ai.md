# AI 기능 (ai)

담당: 이승민(A) · 모델: Claude API `claude-haiku-4-5` (이미지 입력) · 화면: SCR-08, 06, 16
**원칙: AI 결과는 판정이 아니라 참고 자료다. AI가 실패해도 거래 흐름은 절대 멈추지 않는다.**

## 공통
- API 키는 환경 변수 `ANTHROPIC_API_KEY`. 서버에서만 호출(프론트 직접 호출 금지)
- 월 1,000건 한도(Redis 카운터 `ai:usage:{yyyyMM}`), 초과 시 호출하지 않고 "AI 결과 없음"으로 진행
- 이미지는 S3 URL → 서버가 다운로드해 base64로 전달하거나 URL 입력 사용(SDK 지원 방식 확인 후 결정). 호출 전 리사이즈해 토큰 절약
- 클라이언트는 `AiClient` 인터페이스로 감싸 테스트에서 가짜 구현으로 교체
- 모델·SDK 사용법은 구현 시점의 Anthropic 공식 문서를 확인한다(기억에 의존하지 않는다)

## REQ-040 AI 물품 상태 비교 [2] (축소 순서 마지막)
- 트리거: 반납 요청(REQ-018) 커밋 후 → RabbitMQ 큐 `ai.compare` 적재 `{rentalId}`
- 워커: 인계(HANDOVER) 사진 vs 반납(RETURN) 사진 → Claude 호출
- **출력 JSON 스키마 고정**
  ```json
  {
    "changed": true,
    "changeTypes": ["SCRATCH", "STAIN", "MISSING_PART", "BROKEN", "OTHER"],
    "confidence": 0.0,
    "description": "한국어 설명(근거)",
    "regions": [{"imageIndex": 0, "note": "왼쪽 하단 모서리"}]
  }
  ```
  - 시스템 프롬프트에 스키마·허용 값·"조명·각도 차이는 변화로 보지 말 것"을 명시
  - 응답을 Jackson + 스키마 검증 → 실패 시 **최대 2회 재시도** → 그래도 실패면 결과 없음
- 결과를 item_inspections(RETURN).ai_result에 저장 → 양측 `AI_COMPARE_DONE` 알림
- 대여 건당 1회. 같은 사진 조합 결과 캐싱(중복 호출 방지)
- 화면: 제공자 상태 확인 화면·보증금 보류 화면에 근거로 표시 + "조명·각도 차이로 오판 가능" 고지
- 결과가 늦게 와도 제공자는 판정 가능(대기 강제 금지)
- 검증(목표): 샘플 세트(정상·파손·조명 변화 각 20쌍) → 스키마 준수율 ≥99%, 파손 재현율 ≥80%, 30초 이내. 결과 `docs/experiments/ai-compare.md`

## REQ-067 AI 물품 등록 보조 [3]
사진 업로드 → 카테고리(**활성 카테고리 code 중에서만**, 없으면 `ETC` + category_suggestions에 후보 기록), 제목·설명 초안, 유사 물품 기준 적정 대여가·보증금 제안. 사용자가 수정 후 등록.

## REQ-068 AI 금지 품목·부적절 콘텐츠 판별 [3]
등록 사진·설명 분석 → 금지 품목(`policies.md` §2)·부적절 게시물 탐지 → 관리자 검토 대상으로 분류(자동 삭제 아님).
