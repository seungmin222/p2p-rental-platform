# feat/item-wish-recent 결정 기록

## 요약
- API: `POST`·`DELETE /api/items/{itemId}/wishes`(멱등, 200, `{itemId, isWished, wishCount}`), `GET /api/members/me/wishes`(`wish.page` 20 고정, 삭제·숨김도 status로 남김), `GET /api/members/me/recent-items`(피드 카드 배열 최대 20, 삭제·숨김 제외). 상세 `isWished` 실제 값, 상세 조회 시 최근 본 상품 기록(본인 물품 제외).
- 주요 파일: `item/service/WishService`·`WishTxService`(물품 행 FOR UPDATE 후 wish_count ± 1), `item/service/ItemDetailService`(상세 + 기록, 트랜잭션 밖)·`RecentItemWriter`, `item/controller/WishController`·`RecentItemController`, `item/repository/WishRepository`·`ItemViewLogRepository`(네이티브 upsert), `V20__create_wishes.sql`·`V21__create_item_view_logs.sql`. 새 ErrorCode `WISH_OWN_ITEM`, 정책 `wish.page`·`recent-items.max` 바인딩.
- 남은 일: 프론트(상세 하트·관심 목록·최근 본 목록 화면), 피드·검색 카드 isWished는 넣지 않음(필요하면 목록마다 1쿼리), 추천(060)은 `WishTxService.add`의 캐시 무효화 지점과 item_view_logs를 쓴다, 차단 사용자 제외(056)는 `findRecentItems` 조건에 더한다.

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

## REQ-035 최근 본 상품

- **기록 위치는 상세 트랜잭션 밖**: 트랜잭션 없는 `ItemDetailService`가 `ItemQueryService.getDetail`(readOnly) 뒤에 `RecentItemWriter.record`(@Transactional)를 부른다. 상세 안에서 REQUIRES_NEW로 하면 요청 하나가 커넥션 두 개를 잡아 풀 고갈 위험이 있어서. ItemController 상세는 이제 ItemDetailService를 부른다.
- **조회할 때마다 upsert**(조회수 24시간 중복 제거와 별개): `INSERT ... AS new ON DUPLICATE KEY UPDATE viewed_at = GREATEST(...)` 네이티브 1쿼리. 저장 여부를 알 필요가 없어 알림(NotificationTxService)이 이 방식을 피한 이유가 해당하지 않는다. 동시 조회도 한 행, viewed_at은 앞으로만.
- **본인 물품은 기록하지 않는다**(사용자 승인 D): 수정·관리 목적 조회라 잡음이고, 060 추천도 본인 물품을 뺀다. 비회원·404도 기록 없음.
- **기록 실패는 상세를 막지 않는다**: DataAccessException을 잡고 경고 로그(회원 정보 없음). 조회수 Redis 실패와 같은 방식. 테스트는 회원 행이 없는 토큰의 FK 위반으로 재현했다(목 빈 없이).
- **20건 유지는 쓰기 때**: upsert 뒤 최근 본 순 20번째 이후 id를 조회해 삭제(최대 3쿼리). 같은 회원 동시 조회로 잠깐 넘을 수 있지만 다음 기록 때 정리되고, 읽을 때도 limit 20이라 응답은 항상 20건 이하.
- **목록은 피드 카드 배열**(사용자 승인 E): `List<ItemSummaryResponse>`, 페이지 없음, AVAILABLE만, viewed_at·id 내림차순. 보관 20건 안에 삭제·숨김이 있으면 20건보다 적다(문서대로). 쿼리 3개(목록·동네 이름·썸네일, toSummaries 재사용).
- **item_view_logs에 created_at·updated_at 추가**: wishes와 같은 이유(conventions §4). 엔티티 `ItemViewLog`는 스키마 검증·조회용이고 쓰기는 네이티브 upsert로만.
- **추천(060) 확장 지점**: 조회 기록은 이 테이블을 그대로 쓰면 된다. Redis 키·새 정책 값은 만들지 않았다.
