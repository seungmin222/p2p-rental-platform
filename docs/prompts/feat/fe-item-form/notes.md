# feat/fe-item-form 결정 기록

- **좌표 입력**: 주소 직접 입력 + "현재 위치로 가져오기"(`getCurrentCoords`). 지도·주소 검색은 REQ-030(2순위). 좌표는 폼 값에만 두고 화면에 숫자로 보이지 않는다.
- **좌표 한계**: 수정에서 주소만 바꾸면 기존 좌표를 유지해 함께 보내고 주소 칸 아래에 다시 가져오라고 안내한다(좌표를 비우면 오타 수정에도 그 장소에 가야 해서). 그래서 주소와 좌표가 어긋날 수 있다. 대표 동네 밖에서 위치를 가져오면 실제 서버 피드에 안 보일 수 있는데, 목은 regionId로 걸러 재현하지 못한다.
- **좌표 규칙 범위**: openapi `ItemDetailResponse`에 latitude·longitude가 있고 공개 API임을 확인해, frontend/CLAUDE.md 규칙을 "사용자의 현재 위치"로 좁혔다. 등록·수정 뮤테이션과 수정 조회는 그래도 `gcTime: 0`.
- **바뀐 필드**: notes의 `dirtyFields` 대신 `toUpdatePayload`가 처음 값과 비교한다(File·기간 배열을 정확히 비교하고 테스트하기 쉬워서). 바뀐 것이 없으면 요청하지 않는다.
- **imageOrder**: 기존 `{ imageId }`, 새 `{ newImageIndex }`, 사진이 그대로면 imageOrder·images 모두 생략. 최소 장수는 등록과 사진을 바꾼 수정에서만 본다(사진 없는 물품도 가격을 고칠 수 있게).
- **무효화 키**: 등록 → feed·search·mine. 수정 → edit `setQueryData`, detail(exact) `resetQueries`, calendar·feed·search·mine 무효화(invalidate는 이전 사진을 먼저 그려 새 대표 사진이 앞에 낄 때 캐러셀 스냅이 이전 사진을 따라가고, remove는 저장 중 뒤로 가 마운트된 상세의 관찰자를 갱신하지 않아 이전 내용에 멈춘다). 삭제 → 이동 후 `['items', id]` 제거, feed·search·mine·`['rentals']`. fe-item-pages notes에 calendar·rentals를 더했다(기간 변경과 "삭제된 물품" 표시 때문).
- **알려진 한계(edit 캐시)**: 저장 중 화면을 떠나면 `setQueryData`가 edit 캐시를 기본 gcTime으로 다시 만들어 5분 남는다. 좌표는 공개 API에도 있어 이번 범위에서 고치지 않는다.
- **삭제 후 이동**: fe-item-pages notes의 "상세에서는 홈"을 바꿨다(사용자 결정): 상세 → `/me/items` replace, `/me/items` → 그 자리 + 토스트.
- **가능 기간 입력**: `TextField type="date"` 행 추가·삭제. 공용 Calendar는 대여 기준이라 쓰지 않고 수정하지 않았다.
- **동네 인증 안내**: `primaryRegion.isVerified`가 false면 등록 진입 때도 안내와 `/regions` 버튼을 보여 준다. 입력은 막지 않고, 제출 시 `REGION_NOT_VERIFIED` 처리도 둔다.
- **목 파일 분리**: 쓰기 계산은 `itemLogic.js`가 아니라 `itemWriteLogic.js`에 뒀다(한 파일이 커져서). 권장 보증금 내림은 명세에 없어 임시 값이다.
- backend/CLAUDE.md의 문서 읽기 범위를 루트 문서 지도 기준으로 좁힘. docs/ 전체 읽기를 막기 위해서이고, frontend/CLAUDE.md와 implement-req.md는 작고 자주 필요한 핵심 문서라 통째로 읽는 방식을 유지함.

## 브라우저 확인 결과 (2026-10-31, 목 모드)
전 항목 통과. 찾은 버그 1건(새 대표 사진 수정 뒤 캐러셀이 이전 사진에 멈춤)은 위 무효화 키 항목대로 고쳤다.
- 등록: `request`는 application/json, `images`는 선택 순서대로 반복, 기간 2행·좌표 포함 → 201.
- 수정: 순서 변경+1장 삭제는 imageOrder만, 새 파일은 `newImageIndex`와 images 파트, 가격만은 그 필드만, 주소만은 기존 좌표 동반+안내 문구, 변경 없음은 요청 없이 이동.
- 삭제: 상세는 `/me/items` replace, 카드는 토스트, 진행 중인 대여는 409와 사유 표시. 수정·삭제 뒤 상세·피드·검색·`/me/items` 모두 최신.
- 권한·입력: deposit-guide 200과 권장 보증금 표시, user3·4 안내와 403 `REGION_NOT_VERIFIED`와 `/regions` 이동, user2 403 안내, 위치 거부 안내·재시도·제출 차단, `abc`·`1e3` id는 요청 없이 안내.
- 360px: 등록·수정 폼과 삭제 모달에 가로 넘침 없음.
- 확인 방법의 한계: 위치는 `getCurrentPosition` 스텁, 파일은 DataTransfer, 계정 전환은 `mock.session`으로 했다. 실제 권한 팝업·파일 선택창·서버 multipart 처리는 확인하지 않았다.

## 다음 브랜치에서 이어받을 것
- 실제 서버 연동 확인은 `feat/item-crud` 머지 후.
- `/me/items` 목은 회원 1 고정이라 내 물품 흐름은 `user1`로 확인한다.
- 지도·주소 검색(REQ-030)이 들어오면 주소·좌표 어긋남 안내를 없앤다.
