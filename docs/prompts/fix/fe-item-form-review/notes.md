# fix/fe-item-form-review 결정 기록

## 요약
- feat/fe-item-form(PR #23) 사후 리뷰 반영. 새 API 없음(프론트만).
- 새 파일: `features/item/errorMessages.test.js`, `features/item/components/ItemForm.test.js`, `TradePlaceField.test.js`, `docs/prompts/fix/fe-item-form-review/notes.md`.
- 삭제 결과는 `{ alreadyDeleted }`, 이동·토스트는 mutate 콜백. 거래 장소 위치는 고정밀 + 자체 상한 `TRADE_PLACE_LOCATE_DEADLINE_MS`.
- 남은 일: open-issues #29(주소 검색은 REQ-030 때), #30(2xx 공통 래퍼 미검사), 실제 기기·서버 확인.

## 결정

- **좌표 방식**: 현재 위치를 유지하고 명세와의 차이는 open-issues #29에 올렸다. 거래 장소 위치만 고정밀·캐시 없음으로 받는다(좌표가 그대로 장소로 공개되어서). timeout은 새 값을 만들지 않고 기존 10초를 쓴다. 동네용 옵션은 그대로다.
- **위치 상한**: 거래 장소 요청에 15초 상한을 뒀다(geolocation timeout은 권한 대기를 포함하지 않아, 권한 창에 답하지 않으면 제출이 계속 막혀서). 넘으면 안내를 보이고 늦게 온 응답은 버린다. 코드 `LOCATION_NO_RESPONSE`는 거래 장소 전용이라 동네 쪽 `LOCATION_ERROR` 표(전 코드에 동네 문구를 요구)에 넣지 않고 물품 상수에 뒀다. 권한을 늦게 허용하면 측위 중 상한에 걸릴 수 있지만 다시 누르면 된다.
- **수정 후 무효화**: 수정 후 `['rentals']` 무효화 추가(대여 내역의 물품 이름 갱신).
- **checklist.X**: 반납 폼 `checklist.X` 오류가 첫 항목에 붙는 변화는 상위 키 매핑의 결과이지만 실제로 발생하지 않는 경로라 고치지 않았다.
- **테스트 위치**: `itemFormReview.test.js`를 대상 파일 옆으로 나눴다.
- **카테고리 칸**: 로딩·실패에도 `Controller`를 마운트한다(빠지면 검증이 등록되지 않아 categoryId 없이 제출됐다). 재시도 중은 로딩으로 본다. 받은 목록이 있으면 재조회가 실패해도 칩을 유지한다.
- **제출 막기**: 카테고리 로딩·위치 가져오는 중만 막는다. 카테고리 실패는 막지 않는다(새 등록은 검증에 걸리고, 수정은 이미 고른 값으로 저장할 수 있게). 엔터 제출도 onSubmit에서 막는다.
- **중첩 필드 오류**: 정확히 맞는 키를 먼저 찾고, 없으면 `[`·`.` 앞의 첫 조각으로 찾는다. 같은 필드에 여러 건이 오면 마지막 문구만 보인다(기존 setError 동작 유지, 사용자 확인).
- **삭제 구조**: 캐시 정리는 훅 onSuccess, 이동·토스트는 mutate() 콜백. 삭제 중 화면을 떠나면 이동도 토스트도 없다(사용자 확인). feat/fe-item-form notes의 "이동 후 캐시 제거" 순서가 뒤집혔지만, 정리와 콜백 사이에 상세가 다시 그려지지 않아 재요청·404가 나지 않는 것을 브라우저로 확인했다.
- **삭제 404**: mutationFn이 `ITEM_NOT_FOUND`를 `{ alreadyDeleted: true }`로 돌려 성공 경로로 보낸다. 캐시 정리(`['rentals']` 포함)와 이동은 성공과 같고, 토스트만 "이미 삭제된 물품이에요"(사용자 결정).
- **수치 문구 범위**: 물품 폼 힌트와 `features/item/errorMessages.js`만 constants 값으로 바꿨다. `dev/UiPreviewPage`와 대여 쪽 문구는 그대로다(사용자 확인).
- **테스트**: 환경이 node이고 testing-library가 없어, 의존성을 더하지 않고 `react-dom/server`로 필드를 그려 검증 등록과 문구를 확인한다.

## 브라우저 확인 결과 (2026-10-31, 목 모드)
- 2번: 로딩 중 버튼 비활성·`requestSubmit`에 반응 없음. 실패 시 칩 자리에 오류·재시도, 제출하면 "카테고리를 선택해 주세요", 재시도로 칩 복구.
- 5번: 상세에서 삭제 → `/me/items` + 토스트, `GET /items/108` 재요청과 오류 문구 없음. 삭제 중 `/search`로 이동 → 그대로 머물고 토스트 없음. 409는 모달 안에 사유 표시.
- 6번: 위치 대기 중 버튼 비활성·`requestSubmit`에 반응 없음, 응답 후 풀림. 요청 옵션 `enableHighAccuracy: true, maximumAge: 0, timeout: 10000`.
- 7번: 먼저 지운 뒤 삭제 → 이동 + "이미 삭제된 물품이에요", 재요청 없음.
- 확인 방법의 한계: 카테고리 지연·실패와 DELETE 지연은 XHR 패치, 위치는 `getCurrentPosition` 스텁, 화면 이탈은 history 이동으로 했다. 떠난 뒤의 캐시 정리는 단위 테스트로 확인했다.
- 위치 상한: 답하지 않는 `getCurrentPosition` 스텁으로 15초 뒤 제출이 풀리고 안내가 뜨며, 늦게 온 응답이 좌표로 들어가지 않는 것을 확인했다.
- 실제 기기의 고정밀 위치는 확인하지 못했다(정확도·소요 시간, 실제 권한 창 포함).
- 실패 재현이 200 HTML이라 오류 문구가 `["categories"] data is undefined`로 보였다. 원인은 open-issues #30이고 이 브랜치에서 고치지 않았다.
