# feat/ai-compare 결정 기록 (REQ-040)

## 요약
- 흐름: 반납 요청(018, 대리 접수 포함) 커밋 → `ReturnRequestedEvent` → AFTER_COMMIT에 RabbitMQ `ai.compare` 적재 `{rentalId}` → 리스너 → 인계·반납 사진 비교(Claude) → `item_inspections(RETURN).ai_result` 저장 → DONE이면 양측 `AI_COMPARE_DONE`(로그 구현). API는 `GET /api/rentals/{id}/inspections` 응답의 반납 기록에 `aiResult` 필드 추가만.
- 주요 파일: `ai/client/AiClient`·`ClaudeAiClient`·`DisabledAiClient`·`AiClientConfig`·`AiProperties`·`ComparePrompt`, `ai/service/AiCompareService`·`AiCompareParser`·`AiUsageCounter`, `ai/worker/AiCompareQueue`·`AiCompareEnqueuer`·`AiCompareListener`, `global/config/RabbitConfig`, `rental/service/RentalAiCompareService`(ai 쪽 창구), `rental/entity/AiCompareRecord`·`AiCompareResult`·`AiCompareStatus`·`AiChangeType`, `rental/dto/RentalAiResultResponse`, `FileStorage.read`, `ImageProcessor.downscale`, `V13__add_ai_result_to_item_inspections.sql`. 테스트 `support/FakeAiClient`.
- 정해진 규칙: 대여 건당 1회(ai_result가 비어 있을 때만 PROCESSING으로 맡음), 월 한도는 호출 단위·재시도 포함(#47), 오래된 PROCESSING은 응답에서 FAILED(#46), 알림은 DONE만(#48), 키가 없으면 호출하지 않고 FAILED.
- 남은 일: 검증 실험(`docs/experiments/ai-compare.md`)은 표 틀과 실행 도구만 있다(샘플 60쌍·키 필요). 화면(SCR-08·06·16)의 "조명·각도 차이로 오판 가능" 고지와 결과 표시는 프론트 브랜치. 적재가 실패한 대여는 다시 적재하지 않는다(aiResult null = "AI 결과 없음"). `AI_COMPARE_DONE` 실제 저장은 REQ-032. 그런데 `notifications.event_id`는 단일 컬럼 UK(erd.md)이고 `AI_COMPARE_DONE:{rentalId}`(OVERDUE도)는 양측에 같은 키를 쓰므로, REQ-032에서 알림을 저장할 때 키(수신자 포함 등)나 UK(event_id+user_id)를 바꿔야 한다. AFTER_COMMIT 적재는 요청 스레드에서 동기로 돌아 브로커가 막히면 응답이 늦어질 수 있다(connect-timeout 3s로 제한) — 나중에 `@Async` 검토. 동시 소비 테스트는 없다. 반납 기록 행 락 + 리스너 동시성 1(Spring 기본값, 따로 설정하지 않음)로 설계상 안전하다.

## 큐·트리거
- **RabbitMQ 설정은 이 브랜치에서 최소로 넣었다.** 의존성·접속 설정·Testcontainers는 이미 있었고 큐·리스너 코드가 없었다. durable 큐 `ai.compare` + 기본 exchange, JSON 변환기(`JacksonJsonMessageConverter`, Boot 4 Jackson 3), 리스너 예외 재큐잉 끔(`default-requeue-rejected: false`). 명세(RabbitMQ)를 바꾸지 않았다.
- **적재는 `@TransactionalEventListener(AFTER_COMMIT)`.** 롤백되면 적재하지 않는다. 적재 예외는 삼키고 로그만 남긴다(반납은 이미 커밋). 요청 스레드에서 돌므로 `spring.rabbitmq.connection-timeout: 3s`로 브로커 장애 때 응답이 오래 묶이지 않게 했다.
- **미반납 신고는 적재하지 않는다.** 사진이 없다. 맡는 쪽(`RentalAiCompareService.start`)도 lostReported면 건너뛴다.
- **리스너는 예외를 밖으로 내지 않는다.** 실패는 ai_result FAILED로 남기고 메시지는 ack한다.
- **test 프로필은 리스너를 자동 시작하지 않는다**(`spring.rabbitmq.listener.simple.auto-startup: false`). 테스트마다 TRUNCATE로 id가 1부터 다시 시작해서, 앞 테스트가 쌓은 메시지가 다음 테스트의 같은 id 대여를 건드릴 수 있기 때문이다. 큐 경로는 `AiCompareQueueIntegrationTest`만 큐를 비우고 리스너를 켜서 검증한다. 다른 테스트는 `AiCompareService.compare`를 직접 부른다.

## 대여 건당 1회·동시성
- **맡기 = 반납 기록 행 비관 락 + ai_result가 null일 때만 PROCESSING.** 같은 메시지가 두 번 와도 한 워커만 부른다. 대여 행은 잠그지 않아 상태 확인(대여 → 기록 순)과 락 순서가 엇갈리지 않는다. 저장도 PROCESSING일 때만 바꾼다.
- **"같은 사진 조합 결과 캐싱"은 대여 건당 1회로 충족된다.** 사진 URL이 UUID 키라 대여가 다르면 조합도 다르다. 별도 캐시를 두지 않았다.
- **ItemInspection에 `@DynamicUpdate`.** 판정(018)과 AI 저장이 같은 행을 따로 쓴다. 전체 컬럼 UPDATE면 판정 트랜잭션이 먼저 읽은 옛 ai_result(null)로 덮어쓴다. 판정 커밋 사이에 AI 결과가 저장되는 순서를 통합 테스트로 고정했다.
- **ai_result JSON은 Hibernate가 Jackson 2로 직렬화한다**(rental-return의 JSON 매핑과 같음). `AiCompareRecord.isProcessing()`이 `processing` 키로 들어가 역직렬화가 깨져서 `@JsonIgnore`를 붙였다. LocalDateTime 왕복은 통합 테스트로 확인했다.

## 호출·재시도·한도
- **사진은 base64로 보낸다.** 로컬 디스크 저장소의 URL은 Anthropic이 열 수 없다. 운영 S3도 같은 방식으로 맞췄다. `FileStorage.read(key)`를 추가했다(Local·S3).
- **보내기 전 긴 변 1024px로 축소**(`ai.compare.image-max-long-side`, 명세 외). 원본은 이미 1600px 이하로 저장돼 있다.
- **형식 오류만 다시 부른다**(첫 호출 + `ai.compare.max-retry` 2회 = 최대 3회). 호출 자체의 실패(키 없음, API 오류, 거절 stop_reason)는 다시 부르지 않고 FAILED. 429·5xx·연결 오류는 SDK가 2회 재시도한다(`app.ai.sdk-max-retries`, 과금 없음, 한도에 안 셈).
- **월 한도는 호출 직전에 Redis Lua로 INCR, 처음 올릴 때 EXPIREAT(월말 + 1일).** 한도를 넘은 호출은 하지 않고 카운터는 올라간 채로 둔다. 재시도 중에 한도에 닿아도 LIMIT_EXCEEDED. Redis 장애면 FAILED.
- **스키마 검증은 직접 한다**(Jackson 3 트리). 키 다섯 개 정확히, 허용 changeTypes·중복 없음, confidence 0~1, description 비지 않음, imageIndex는 반납 사진 범위, changed와 changeTypes·regions 일관성. 앞뒤 ```json 코드 블록만 벗긴다.
- **모델은 ai.md의 `claude-haiku-4-5`를 `app.ai.model`로 뺐다.** 구조화 출력(output_config.format)은 쓰지 않았다. 명세가 "Jackson + 스키마 검증 → 재시도"를 정해 두었기 때문이다.
- **키가 없으면 `DisabledAiClient`**(로컬 기본). 호출하지 않고 FAILED로 남는다. `.env.example`의 `ANTHROPIC_API_KEY=`는 이미 있었다.
- SDK `com.anthropic:anthropic-java:3.2.0`. Boot BOM이 Jackson 2를 2.21.x로 맞춘다. 요청 구성은 단위 테스트로만 확인했고 실제 호출은 하지 않았다(`ClaudeAiClientLiveTest`, `-PaiLive`).

## 응답
- **`aiResult`는 반납 기록에만 붙고 `{status, result, analyzedAt}`.** null = 비교 전(적재 실패·미반납 신고 포함, 화면은 "AI 결과 없음"). FAILED·LIMIT_EXCEEDED는 result null. PROCESSING이 10분을 넘으면 FAILED로 내려간다(DB는 그대로, #46).
- **오판 가능 고지 문구는 응답에 싣지 않는다.** 화면 고정 문구로 둔다.
- 결과가 늦거나 없어도 상태 확인을 막지 않는다(테스트로 고정). 판정 뒤에 온 결과도 저장한다.

## 알림
- **`RentalNotificationPort.Type`에 `AI_COMPARE_DONE`을 추가했다.** ai → rental 방향이라 포트를 새로 만들지 않았다. event_id `AI_COMPARE_DONE:{rentalId}`, 양측 같은 키(OVERDUE 선례). DONE만 보낸다(#48).

## 명세 외로 넣은 것
- `ai.compare.processing-timeout-minutes`(10분), `ai.compare.image-max-long-side`(1024px), `app.ai.timeout`(30초)·`sdk-max-retries`(2)·`max-tokens`(1024). 상태 enum `AiCompareStatus`, `AiChangeType`(state-machines.md §5에 추가).
