# feat/fe-item-form 결정 기록

- **좌표 입력**: 주소 직접 입력 + "현재 위치로 가져오기"(`getCurrentCoords`). 지도·주소 검색은 REQ-030(2순위). 좌표는 폼 값에만 두고 화면에 숫자로 보이지 않는다.
- **좌표 한계**: 수정에서 주소만 바꾸면 기존 좌표를 유지해 함께 보내고 주소 칸 아래에 다시 가져오라고 안내한다(좌표를 비우면 오타 수정에도 그 장소에 가야 해서). 그래서 주소와 좌표가 어긋날 수 있다. 대표 동네 밖에서 위치를 가져오면 실제 서버 피드에 안 보일 수 있는데, 목은 regionId로 걸러 재현하지 못한다.
- **좌표 규칙 범위**: openapi `ItemDetailResponse`에 latitude·longitude가 있고 공개 API임을 확인해, frontend/CLAUDE.md 규칙을 "사용자의 현재 위치"로 좁혔다. 등록·수정 뮤테이션과 수정 조회는 그래도 `gcTime: 0`.
- **바뀐 필드**: notes의 `dirtyFields` 대신 `toUpdatePayload`가 처음 값과 비교한다(File·기간 배열을 정확히 비교하고 테스트하기 쉬워서). 바뀐 것이 없으면 요청하지 않는다.
- **imageOrder**: 기존 `{ imageId }`, 새 `{ newImageIndex }`, 사진이 그대로면 imageOrder·images 모두 생략. 최소 장수는 등록과 사진을 바꾼 수정에서만 본다(사진 없는 물품도 가격을 고칠 수 있게).
- **무효화 키**: 등록 → feed·search·mine. 수정 → edit `setQueryData`, detail(exact), calendar, feed·search·mine. 삭제 → 이동 후 `['items', id]` 제거, feed·search·mine·`['rentals']`. fe-item-pages notes에 calendar·rentals를 더했다(기간 변경과 "삭제된 물품" 표시 때문).
- **삭제 후 이동**: fe-item-pages notes의 "상세에서는 홈"을 바꿨다(사용자 결정): 상세 → `/me/items` replace, `/me/items` → 그 자리 + 토스트.
- **가능 기간 입력**: `TextField type="date"` 행 추가·삭제. 공용 Calendar는 대여 기준이라 쓰지 않고 수정하지 않았다.
- **동네 인증 안내**: `primaryRegion.isVerified`가 false면 등록 진입 때도 안내와 `/regions` 버튼을 보여 준다. 입력은 막지 않고, 제출 시 `REGION_NOT_VERIFIED` 처리도 둔다.
- **목 파일 분리**: 쓰기 계산은 `itemLogic.js`가 아니라 `itemWriteLogic.js`에 뒀다(한 파일이 커져서). 권장 보증금 내림은 명세에 없어 임시 값이다.

## 다음 브랜치에서 이어받을 것
- 실제 서버 연동 확인은 `feat/item-crud` 머지 후.
- `/me/items` 목은 회원 1 고정이라 내 물품 흐름은 `user1`로 확인한다.
- 지도·주소 검색(REQ-030)이 들어오면 주소·좌표 어긋남 안내를 없앤다.
