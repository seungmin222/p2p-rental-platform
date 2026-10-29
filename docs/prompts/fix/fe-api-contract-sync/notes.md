# fix/fe-api-contract-sync 결정 기록

머지된 프론트(feat/fe-rental-pages·feat/fe-my-pages)가 "백엔드 확정 전 임시"로 가정한 API를 develop의 회원·물품 계약(docs/openapi.json, #17)에 맞춘다. 새 화면·새 API 연동은 하지 않는다. 백엔드 서비스는 아직 미구현이라 개발은 계속 MSW 목으로 한다.

## 이전 notes 대조 결과

### feat/fe-rental-pages "임시 API"·"백엔드에 확인할 것"
| 항목 | openapi.json 결과 | 고친 것 |
| --- | --- | --- |
| `GET /api/items/{id}` 예약 화면이 쓰는 필드(`images[].url` 첫 장을 썸네일로) | 바뀜: 썸네일은 `ItemImageResponse.thumbnailUrl`(`url`은 원본) | `toItemSummary`가 `images[0].thumbnailUrl`을 읽는다(없으면 null) |
| 같은 API의 `pricePerDay`, `deposit`, `owner{ nickname, regionName }`, `isOwner`, `itemId`, `name` | 맞음 | TODO만 지움 |
| 같은 API 목 응답 | 바뀜: ItemDetailResponse 필드 전부 | `mocks/item.js`가 images{imageId,url,thumbnailUrl}·category·condition·description·address·위경도·regionName·status·bumpedAt·카운터·isWished(false)·owner{memberId,nickname,profileImageUrl,regionName}·isOwner를 돌려준다. 108은 images 빈 배열 |
| `POST /api/auth/reissue` 응답 `{ accessToken }` | 맞음(AuthTokenResponse) | `extractAccessToken` TODO만 지움(격리 유지). 실패 코드 AUTH_REFRESH_TOKEN_INVALID는 shared/api가 401 상태만 보므로 동작 그대로 |
| 재발급 목 "항상 성공" | 범위 밖(로그인 화면 전이라 남김) | 주석만 고침. feat/fe-auth-region에서 교체 |
| 2순위: 대여 상세에 `depositStatus`·차감 정보 | 범위 밖(회원·물품 계약에 없음) | — |

### feat/fe-my-pages "임시 API"·"백엔드에 확인할 것"
| 항목 | openapi.json 결과 | 고친 것 |
| --- | --- | --- |
| `GET /api/members/me/items` page(0부터) + PageResponse(`items·page·size·totalElements·hasNext`) | 맞음 | TODO만 지움 |
| 항목 `itemId, name, thumbnailUrl, pricePerDay, deposit, viewCount, wishCount, chatCount, upcomingRentalCount` | 맞음. `isRenting, regionName, status, bumpedAt`이 더 있음 | 화면(`toMyItem`)은 새 필드를 읽지 않는다(쓰는 곳 없음). 목은 MyItemResponse 필드 전부 |
| 정렬 최근 등록(itemId 내림차순) | 맞음 | — |
| 페이지 크기 20 | 맞음(`my-items.page`, size 받지 않음) | 목 주석을 정책 키로 고침 |
| tab 생략 시 AVAILABLE, HIDDEN 빈 목록 | 맞음 | 목 주석 |
| `upcomingRentalCount`는 결제 시간이 지난 PENDING_PAYMENT를 세지 않음 | 맞음("결제 만료 전 중 시작일 ≥ 오늘") | — |
| `GET /api/items/{itemId}/rentals` page + PageResponse, 항목 `rentalId, startDate, endDate, status, renter{memberId,nickname}` | 맞음(ItemRentalResponse·Renter) | TODO만 지움. 화면은 다시 정렬하지 않는다(확인) |
| 정렬 제안(다가오는·진행 중 오름차순 먼저, 지난 건 내림차순 뒤) | 채택(open-issues #20). 같은 시작일은 id 오름차순 | 목 `listItemRentals` 정렬 변경 + 테스트(두 그룹 순서·그룹 안 정렬·동률·시드 107) |
| CANCELED 포함 | 맞음 | — |
| 페이지 크기 20 | 맞음(`item-rentals.page`) | — |
| 남의 물품 403 `RENTAL_OWNER_ONLY` | 바뀜: `ITEM_NOT_OWNER` | 목 403 코드 변경, 목 메시지·상태 표에 추가(백엔드 ErrorCode 메시지). 화면 문구 추가 |
| 없는·삭제된 물품 404 `ITEM_NOT_FOUND` | 맞음 | — |
| api.md 경로에 `page=` 표기 | 맞음(#17에서 반영됨) | — |

## 결정
- **`ITEM_NOT_OWNER` 문구는 "내 물품만 볼 수 있어요"**. 이 브랜치에서 이 코드를 받는 화면은 예약 현황 시트뿐이고, 시트는 오류 코드로 분기하지 않고 `ErrorState`에 `getErrorMessage`를 보여 준다(RENTAL_OWNER_ONLY 분기 없음 확인). 시트는 내 물품 목록에서만 열려 실제로는 거의 나오지 않는다. RENTAL_OWNER_ONLY는 대여 API가 계속 쓰므로 그대로 둔다.
- **목 시드의 카테고리 id·이름은 백엔드 `V3__insert_categories.sql`의 넣은 순서**(1 캠핑·아웃도어, 2 공구·DIY, 3 촬영·영상 장비, 6 취미·시즌 용품). 물품 동네·거래 장소는 제공자 동네를 따른다(등록 시 대표 동네).
- **목 물품 상세는 조회 수를 올리지 않는다**: 새로고침마다 숫자가 바뀌면 화면 확인이 어려워서. 숨김 물품 404는 시드에 숨김 물품이 없어(REQ-044 전) 넣지 않았다.
- **목 `bumpedAt`은 오늘 기준 며칠 전 10:00**(오프셋 없는 서울 시각, 다른 목 시각과 같은 형식).
- **예약 화면 동네명은 `owner.regionName`**: item.md REQ-011 "제공자 요약(동네명)"을 따른다. 물품 동네(`ItemDetailResponse.regionName`, 등록 시 대표 동네)와 다를 수 있다(제공자가 대표 동네를 바꾼 경우).
- **403 시트의 "다시 시도" 버튼은 그대로 둔다**: 시트가 내 물품에서만 열려 403은 거의 나오지 않는다. 다시 시도해도 같은 403이지만 문제 될 일이 드물어 분기를 넣지 않았다.
- **path `itemId` 형식 400 (리뷰 반영)**: 물품 상세·물품별 예약 현황·캘린더 목은 itemId가 Long으로 바뀌지 않으면(숫자 아님·소수·범위 초과) 400 `COMMON_INVALID_INPUT`(details itemId "형식이 올바르지 않습니다.")을 낸다. 백엔드가 `@PathVariable Long`(`@Positive` 없음) + `MethodArgumentTypeMismatchException` → 400이라 `0`·음수는 변환이 되어 404로 간다. 목도 같게(`pageList.js` `readPathId`, 테스트 있음). 검사 순서 400 → 404 → 403. `/api/rentals/:rentalId` 목도 같은 문제(숫자 아님 → 지금은 404)지만 이 브랜치 범위(회원·물품 계약) 밖이라 두었다.

## 로직 브랜치에 알릴 것
- **feat/item-crud (물품 상세 REQ-011)**: `images` 0번이 대표, `thumbnailUrl`은 원본과 별도 URL, 이미지가 없으면 빈 배열(null 아님). `owner.profileImageUrl`·`owner.regionName`은 nullable. `isWished`는 REQ-022 전 항상 false. 숨김 물품은 등록자 외 404. 숫자가 아닌 itemId는 400(타입 변환 실패). 목이 이 전제로 만들어져 있다.
- **물품별 예약 현황 구현 브랜치 (REQ-020)**: 완료·취소 그룹은 시작일 내림차순, 같은 시작일은 두 그룹 모두 id 오름차순으로 목을 구현했다. openapi 설명의 "같은 시작일은 id 순"을 "id 오름차순"으로 분명히 해 주면 좋겠다.
- **내 물품 구현 브랜치 (REQ-020)**: `MyItemResponse.status` enum에 DELETED가 있지만 description은 "AVAILABLE 또는 HIDDEN"뿐이다(삭제된 물품은 목록에 없음). enum을 좁힐지 결정 필요.
- **공통**: `bumpedAt` 등 date-time 필드는 오프셋 없는 서울 시각(LocalDateTime)을 전제로 화면·목을 만들었다.

## 확인
- `npm run lint`·`npm test`·`npm run build` 통과. 500kB 청크 경고는 fe-rental-pages notes의 "다음에" 그대로.
- 헤드리스 Chrome(CDP), `VITE_USE_MOCK=true`, 390px: 예약 화면(물품 101) 상단 썸네일·"캠핑조아 · 연수동", 내 물품 관리 대여 가능 탭(108 → 107, 다가오는 예약 1건), 물품 107 예약 현황 시트(32 대여 예정이 맨 위, 거래 완료는 10월 19일부터 과거 순). 목 직접 호출: `/api/items/101` 키가 ItemDetailResponse와 같음, 108 images `[]`, `/api/items/101/rentals` 403 ITEM_NOT_OWNER, `/api/items/106` 404 ITEM_NOT_FOUND.
