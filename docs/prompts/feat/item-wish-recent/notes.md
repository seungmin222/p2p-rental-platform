# feat/item-wish-recent 결정 기록

REQ-022 관심(찜), REQ-035 최근 본 상품 (백엔드). 프론트 화면·REQ-060 추천은 범위 밖.

## REQ-022 관심

- **추가·해제 멱등, 응답 200 고정**: 이미 관심인데 POST, 관심이 아닌데 DELETE는 아무것도 바꾸지 않고 현재 상태 `{itemId, isWished, wishCount}`를 돌려준다. 하트 버튼 연타·재시도에도 결과가 같게 하려고. 새로 담을 때만 201로 나누지 않았다(사용자 승인 C).
- **물품 행을 먼저 잠근다**(`ItemRepository.findWithLockById`, SELECT ... FOR UPDATE): 추가·해제 둘 다 첫 문장. 승인 계획은 "insert + 원자적 UPDATE"였는데, 해제는 wishes 삭제 → 카운트 UPDATE 순서라 같은 회원의 추가와 엇갈리면 교착이 날 수 있어 락을 앞에 두었다. 카운트 변경은 여전히 `wish_count ± 1` 벌크 UPDATE이고, 락을 잡고 읽은 값 ±1을 응답 wishCount로 쓴다(재조회 쿼리 없음). 같은 물품의 관심 변경은 이 락에서 줄을 선다.
- **UK 위반은 한 번 재시도**(`WishService`): 락 때문에 보통 나지 않지만, 나면 롤백 뒤 다시 시도해 "이미 관심"으로 응답한다. 다시 실패하면(FK 등) 원래 예외.
- **해제 시 음수 방지**: 지운 행이 있을 때만 `wish_count - 1 WHERE wish_count > 0`.
- **물품 상태별**: 추가는 상세와 같은 공개 규칙(`Item.isVisibleTo`로 추출해 상세와 공유) → 404 뒤에 본인 물품 400 `WISH_OWN_ITEM`(숨김인 본인 물품도 400). 해제는 없는 물품만 404이고 삭제·숨김 물품도 해제 가능 — 목록에 남은 항목을 지울 수 있어야 해서.
- **관심 목록**: 숨김 물품도 status=HIDDEN으로 남긴다(사용자 승인 A). 빼면 숨긴 동안 해제할 수 없다. 정렬은 wishes.id 내림차순(= 추가순), 인덱스 `(member_id, id)`. 카드는 피드 카드 + `status`, `wishedAt`(= wishes.created_at). 쿼리 4개(목록·count·동네 이름·썸네일).
- **페이지 크기 `wish.page` 20 고정**: 명세에 값이 없어 my-items·profile-items와 같게 policies.md에 추가.
- **상세 isWished**: 로그인 + 본인 물품 아님일 때만 exists 1쿼리. 피드·검색 카드에는 넣지 않았다(문서에 없음, 사용자 승인 B).
- **wishes.updated_at**: erd에는 created_at만 있었지만 conventions §4(모든 테이블 created_at·updated_at)와 BaseTimeEntity를 따라 추가하고 erd를 고쳤다.
- **추천 캐시 무효화**: `WishTxService.add`에 REQ-060 확장 지점 주석만. Redis 키는 만들지 않았다.
