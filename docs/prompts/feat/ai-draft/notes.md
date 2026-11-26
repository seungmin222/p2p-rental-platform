# feat/ai-draft 결정 기록

## 요약
- 구현: `POST /api/items/ai-draft`(multipart `images`, 사진 저장 안 함). 응답 한 번에 카테고리(categoryId·code·name, ETC면 후보 이름), 제목·설명 초안, 추정 원가 `itemPrice`, 일 대여료 `pricePerDay`, 권장 보증금 범위 `minDeposit`·`maxDeposit`. 결과 `DONE`·`FAILED`·`LIMIT_EXCEEDED`. `/api/items/ai-category`는 이 API에 합쳐 없앴다.
- 주요 파일: `ai/controller/AiDraftController`, `ai/service/AiDraftService`·`AiDraftParser`·`AiUsageCounter.tryAcquireDraft`, `ai/client/DraftPrompt`·`AiClient.draft`, `ai/dto/AiDraftResponse`·`AiDraftStatus`(ai-category 파일을 이름만 바꿔 이었다).
- 정책 키 `ai.category.*` → `ai.draft.timeout-seconds`(30초)·`ai.draft.per-member-daily`(10회), Redis `ai:draft:{memberId}:{yyyyMMdd}`. 설정 `app.ai.draft-max-tokens`(2048) 추가.
- 테스트: `AiDraftIntegrationTest`·`AiDraftServiceTest`·`AiDraftParserTest`, `FakeAiClient.respondDraft`. 동시성·부하 테스트는 계속 미룬다.
- 남은 일: 프론트 등록 화면 연동(아래 "프론트가 할 일"), 관리자 후보 검토(057).

## 결정
- **ai-category를 ai-draft로 합쳤다**(2026-11-26 사용자 결정). 프론트에서 ai-category를 부르는 곳이 없어 깨지는 화면이 없고, 둘을 두면 사진을 두 번 보내고 월 한도·하루 횟수도 두 번 쓴다. 카테고리 결정 규칙(활성 code 목록, ETC 대체, 후보 기록 #52)은 그대로 옮겼다.
- **일 대여료는 AI 추정만**(사용자 결정, A안). 프롬프트에서 "비슷한 물품의 개인 간 대여 시세"를 기준으로 하게 한다. DB의 같은 카테고리 가격 통계(B안)는 시드가 적어 표본 최소 개수 정책만 늘고 거의 항상 AI 값으로 떨어지므로 쓰지 않았다. ai.md "유사 물품 기준"을 이에 맞게 고쳤다.
- **보증금은 AI에게 묻지 않고 범위만 준다**(사용자 결정). 추정 원가에 `ItemQueryService.getDepositGuide`(deposit-guide API와 같은 계산)를 그대로 적용한다. 단일 값은 미리 채우지 않는다: 범위 하한이 1,000원 미만이 될 수 있어(item.deposit 규칙) 고르는 규칙을 새로 만들어야 하기 때문이다. 범위는 #35처럼 등록 상한으로 자르지 않는다. 계산이 실패하면 범위만 null.
- **타임아웃 30초**(사용자 결정, 이전 20초). 제목·설명까지 받아 출력이 길어졌다. 응답 최대 토큰은 상태 비교(1024)와 따로 `draft-max-tokens` 2048로 둔다(설명 2,000자). 정책이 아니라 연결 설정이라 policies.md에 넣지 않았다.
- **형식 오류(재시도) 기준은 categoryCode뿐**: JSON 객체가 아니거나 categoryCode가 공백 아닌 문자열이 아닐 때만 다시 부른다. 카테고리는 폼에 바로 선택되는 필수 결과이고, 나머지는 사용자가 채울 수 있는 초안이라 필드 하나 때문에 한도를 더 쓰지 않는다. 그 밖의 필드는 없거나 틀리면 그 필드만 null, 모르는 키는 무시한다(ai-category는 키 집합이 정확히 맞아야 했다).
- **범위 밖 값은 보정하지 않고 null**: 원가 0 ~ item.item-price.max, 대여료 0 또는 item.price-per-day. 소수·문자열 숫자도 null. 반올림·자르기 규칙을 새로 만들지 않기 위해서다. 제목·설명만 item.name.max·description.max(코드 포인트)로 자른다(글자 수 상한은 프롬프트에도 넣는다).
- 하루·월 카운터 규칙은 ai-category와 같다(회원 한도 먼저, 월 한도는 호출마다, 사진 오류는 세지 않음). Redis 키 이름이 바뀌어 배포 당일 ai:category 카운트는 이어지지 않는다(하루 단위라 영향 없음).

## 프론트가 할 일 (별도 프론트 브랜치)
- 등록 화면(SCR)에서 사진을 고른 뒤 `POST /api/items/ai-draft`를 부르고, `DONE`이면 카테고리 select에 `categoryId`를 넣는다(목록은 `GET /api/categories`). 응답 전에 사용자가 이미 고친 칸은 덮지 않는다.
- `ETC`면 `suggestedCategoryName`을 안내한다. 제목·설명·원가·일 대여료는 null이 아닌 칸만 채운다.
- 보증금은 `minDeposit`~`maxDeposit`를 권장 범위로 보여 주고 값은 비워 둔다. 사용자가 원가를 고치면 `GET /api/items/deposit-guide`로 다시 계산한다.
- `FAILED`·`LIMIT_EXCEEDED`면 "직접 입력해 주세요"로 안내하고 등록은 막지 않는다. 응답이 최대 30초 걸릴 수 있어 로딩 표시가 필요하다.
