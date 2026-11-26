# feat/fe-ai-draft 결정 기록 (REQ-067 프론트)

## 요약
- SCR-06 물품 등록에 "AI로 채우기" 버튼 연동: `POST /api/items/ai-draft`(multipart images) 호출, 응답의 권장 보증금은 deposit-guide 캐시에 넣는다. 백엔드 변경 없음.
- 새 파일: `frontend/src/features/item/aiDraft.js`(덮어쓰기 판단·안내 문구), `features/item/components/AiDraftField.jsx`, `mocks/aiDraft.js`(첫 사진 파일 이름으로 결과 선택). `ItemForm.jsx`·`queries.js`(`useDraftItem`)·`api.js`(`draftItem`) 수정.
- 남은 일: 물품 수정 화면에는 버튼을 넣지 않았다(결정 참고). 테스트는 목 기준이다.

## 결정
- **"AI로 채우기" 버튼으로 부른다**(사용자 승인, 2026-11-26). 사진을 고를 때 자동으로 부르지 않고, 사진을 바꿔도 다시 부르지 않는다. 하루 10회 한도(`ai.draft.per-member-daily`)를 사진 고를 때마다 쓰지 않게 하려는 것이다. 다시 누르면 새로 부르고 횟수도 다시 쓴다. 새 사진이 없거나 요청 중이면 누를 수 없다.
- 사진은 폼의 새 파일을 화면 순서대로 최대 10장(item.images와 같은 백엔드 검사) 보낸다.
- **덮어쓰기 판단은 값 비교**(`aiDraft.js` `isUserEdited`): 응답이 도착한 때 칸 값이 처음 값과 다르고 AI가 마지막으로 넣은 값과도 다르면 사용자가 고친 칸으로 보고 건너뛴다. 계획에서는 react-hook-form `isDirty`를 쓰기로 했지만, AI가 넣은 칸을 다시 눌렀을 때 바꿔야 해서 AI가 넣은 값을 따로 들고 비교하는 쪽으로 바꿨다. 동작은 계획과 같다. 채우는 칸은 카테고리·이름·설명·일 대여료·물품 가격이고, 상태와 보증금은 채우지 않는다.
- **보증금 안내는 기존 deposit-guide 안내 하나로 둔다.** AI가 카테고리와 원가를 채우면 기존 훅이 같은 범위를 부르므로, 응답의 min/maxDeposit을 `itemKeys.depositGuide` 캐시에 먼저 넣어 요청을 한 번 아낀다(`draftItemMutationOptions`). 원가를 고치면 기존 400ms 디바운스로 다시 계산한다.
- 로딩 중에는 버튼에 스피너를 띄우고 "최대 30초" 안내(role=status)를 보인다. 입력과 등록은 막지 않는다. 응답이 오기 전에 화면을 떠나면 mutate 콜백이 불리지 않아 응답은 버려진다.
- 실패 안내: FAILED·네트워크 오류는 "AI 초안을 만들지 못했어요", LIMIT_EXCEEDED는 "오늘 AI 사용 횟수를 모두 썼어요"(둘 다 "직접 입력해 주세요"). 하루 한도와 월 한도를 응답으로 구분할 수 없어 횟수 숫자는 넣지 않는다. 사진 400은 기존 사진 오류 문구를 쓴다. 문구는 사용자 승인.
- 로딩 문구(`AiDraftField.jsx`)와 성공·ETC 안내 문구(`aiDraft.js` `getDraftNotice`)도 계획 단계에서 사용자가 승인한 문구다.
- 물품 수정 화면에는 버튼을 넣지 않는다(이미 채워진 칸이 대부분이라 덮을 칸이 거의 없음).
- 목은 첫 사진 파일 이름으로 결과를 고른다(`fail`·`limit`·`etc`·`partial`, 그 밖은 DONE 텐트), 지연 2초 + 공통 0.4초.
