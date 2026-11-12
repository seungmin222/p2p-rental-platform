# feat/ai-category 결정 기록

## 범위
- 처음에는 REQ-067 전체(`feat/ai-item-assist`, `/api/items/ai-draft`)로 계획했으나, 시연이 쉬운 **카테고리 추천만** 하기로 줄였다(2026-11-12 사용자 결정). 브랜치 이름도 `feat/ai-category`로 바꿨다. 제목·설명 초안, 대여가·보증금 제안은 남은 일이다.

## 결정
- **API는 `POST /api/items/ai-category`(multipart `images`)**. `/ai-draft`는 초안·가격까지 담는 이름이라 남겨 두고(api.md 미구현 행), 카테고리만 하는 경로를 따로 둔다. 컨트롤러는 ai 패키지(`AiCategoryController`)이고 Swagger 태그는 경로에 맞춰 Item.
- **사진은 이 요청에서 받고 저장하지 않는다.** 등록 전이라 물품이 없고, 미리 올린 이미지를 참조하는 API도 없다. 검증은 물품 등록과 같은 `item.images`(장수·형식·용량·픽셀 수, 400). 메모리에서 긴 변 1024px로 줄이고, 작아도 항상 다시 인코딩한다(`ImageProcessor.resizeUpload`): EXIF 회전을 반영하고 GPS 등 메타데이터를 외부 API로 보내지 않기 위해서다.
- **동기 응답, AI 실패는 200 + status.** 등록 화면이 기다리는 기능이라 040처럼 큐에 넣지 않는다. 사진 오류만 400이고, 키 없음·호출 실패·시간 초과·형식 오류(재시도 후)·Redis 오류는 `FAILED`, 월 한도·회원 하루 횟수 초과는 `LIMIT_EXCEEDED`. 이때 추천 필드는 null이고 사용자가 직접 고른다("AI가 실패해도 거래 흐름은 멈추지 않는다").
- **시간 제한 20초는 요청 전체**(`ai.category.timeout-seconds`, 재시도 포함). 시도마다 남은 시간을 SDK `RequestOptions.timeout`으로 넘기고, SDK 재시도는 이 호출에서만 끈다(`withOptions(maxRetries(0))`). 끄지 않으면 SDK 재시도 때문에 20초를 넘길 수 있다. 040의 SDK 설정(30초·재시도 2회)은 그대로다.
- **응답에 `categoryId`도 준다.** 물품 등록은 `categoryId`를 받고 `GET /api/categories`에는 code가 없어서, code만 주면 프론트가 id로 바꿀 수 없다. code·name도 함께 준다.
- **카테고리 검증**: 활성 카테고리 code·name 목록을 사용자 메시지에 넣는다(시스템 프롬프트는 고정해 캐시가 맞게). 응답 code가 목록에 없으면 `ETC`로 바꾸고 AI의 `suggestedName`을 후보로 쓴다. 모델이 ETC가 아닌 목록 code를 고르면 `suggestedName`은 버린다. 활성 목록에 ETC가 없으면 `FAILED`. 활성 카테고리 code 목록은 캐시하지 않는다(8행, 관리자 변경이 바로 보이게).
- **category_suggestions는 추천 시점에 PENDING으로 기록**(open-issues #52). item_id는 NULL 허용으로 두고 `member_id`를 추가했다(V16). 같은 이름도 요청마다 한 행이고, 묶기는 검토 화면(REQ-057)에서 한다. 기록이 실패해도 추천 응답은 그대로 준다. 이름은 공백을 정리하고 30자(categories.name 길이)로 자른다.
- **한도**: 월 한도는 040과 같은 `ai:usage:{yyyyMM}`을 호출마다(재시도 포함) 센다. 회원 하루 10회(`ai.category.per-member-daily`, Redis `ai:category:{memberId}:{yyyyMMdd}`)는 추천 요청 단위로 센다. 회원 한도를 먼저 잡아, 넘은 요청은 월 한도를 쓰지 않는다. 사진 검증에 실패한 요청은 어느 쪽도 세지 않는다.
- **정책 키 공통화**: `ai.compare.max-retry` → `ai.max-retry`, `ai.compare.image-max-long-side` → `ai.image-max-long-side`(값은 그대로). 040과 067이 같은 값을 쓰기 때문이다. `ai.compare.processing-timeout-minutes`는 비교 전용이라 그대로 둔다.
- 키가 없으면(`DisabledAiClient`) 호출하지 않고 `FAILED`이지만, 040과 같게 하루·월 카운터는 이미 올라간 뒤다.

## 남은 일
- REQ-067 잔여: 제목·설명 초안(`item.name.max` 50자·`description.max` 2,000자), 유사 물품 기준 대여가·보증금 제안(`/api/items/ai-draft`). 가격 근거(DB 통계 vs AI)와 표본 최소 개수는 그때 정한다.
- 미룬 테스트(이번 브랜치는 테스트 코드 없음, `FakeAiClient.recommendCategory`는 항상 ETC·후보 없음을 돌려준다): ① 목록 code → DONE, 후보 기록 없음 ② 목록 밖 code → ETC + 후보 이름 + category_suggestions PENDING 1행 ③ ETC + suggestedName null → 기록 없음 ④ 형식 오류 2회 후 성공 / 3회 모두 실패 → FAILED, 월 카운터 3 증가 ⑤ 클라이언트 예외 → FAILED ⑥ 월 한도 초과 → LIMIT_EXCEEDED ⑦ 회원 하루 11번째 → LIMIT_EXCEEDED, 월 카운터 그대로 ⑧ 사진 0장·11장·형식 오류·용량 초과 → 400, 카운터 그대로 ⑨ 시간 초과(남은 시간 0) → FAILED ⑩ `AiCategoryParser` 키·타입·코드 펜스·30자 자르기 ⑪ 비로그인 401.
- 프론트: 등록 화면(SCR)에서 사진 선택 후 호출해 카테고리를 미리 채우고, `ETC`면 후보 이름을 안내. 별도 프론트 브랜치.
- 관리자 후보 검토(REQ-057): `idx_category_suggestions_status_id`로 PENDING 목록.
