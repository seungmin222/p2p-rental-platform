# feat/fe-my-pages 결정 기록

SCR-12 나의 대여 중 1순위 세 화면: `/me` 메뉴, `/me/rentals` 빌린 내역(REQ-019), `/me/items` 내 물품 관리(REQ-020). `feat/fe-rental-pages`에서 분리한 브랜치(그쪽 notes "범위·의존성").

## 범위 밖과 어디서 할지
| 넣지 않은 것 | 이유 | 어디서 |
| --- | --- | --- |
| `/me` 프로필 카드·`/me/profile` | 회원 정보 조회·수정 API(REQ-005)가 아직 없다. `/me/profile`은 자리표시 그대로 | 회원 API 머지 후 프론트 회원 화면 브랜치 |
| 로그아웃 | 로그아웃 API가 없다(openapi.json에 auth 없음) | feat/fe-auth-region(로그인과 함께) |
| 관심·최근 본·결제/정산 내역 메뉴 | 2순위(REQ-022·035·033·039) | 각 2순위 브랜치에서 `/me` 메뉴에 줄 추가 |
| 후기 쓰기(빌린 내역 `canWriteReview`) | 후기는 2순위(REQ-034). 응답 필드는 오지만 읽지 않는다 | 후기 브랜치에서 빌린 내역 카드에 버튼 |
| 내 물품 삭제 | REQ-008(물품 수정·삭제)과 함께 | feat/fe-item-pages |
| 숨기기·끌어올리기·HIDDEN 탭 | 3순위(REQ-044·045) | 3순위 단계 |

## 임시 API (openapi.json에 없음, `// TODO: 백엔드 확정 전 임시`)
| API | 가정한 응답 | 읽는 곳 |
| --- | --- | --- |
| `GET /api/members/me/items?tab=AVAILABLE\|RENTING&page=` (REQ-020) | 빌린 내역과 같은 PageResponse. 항목 `{ itemId, name, thumbnailUrl, pricePerDay, deposit, viewCount, wishCount, chatCount, upcomingRentalCount }` — item.md REQ-020 카드 + erd.md items 카운터 이름 | `features/item/api.js` `toMyItem` |
| `GET /api/items/{itemId}/rentals?page=` (REQ-020) | 같은 PageResponse. 항목 `{ rentalId, startDate, endDate, status, renter { memberId, nickname } }`, 기간순. 남의 물품 403 `RENTAL_OWNER_ONLY`, 없는 물품 404 `ITEM_NOT_FOUND` | `features/rental/api.js` `toItemRental` |

- 빌린 내역(`GET /api/members/me/rentals`)은 openapi.json 그대로라 TODO가 없다. size를 보내지 않고, 정렬도 다시 하지 않는다.
- **내 물품 목 정렬은 최근 등록(itemId 큰) 순으로 가정했다.** REQ-020에 정렬이 없다.
- **내 물품·예약 현황의 페이지 크기는 20으로 가정했다**(빌린 내역 `rental-history.page`와 같게). 화면은 hasNext만 보므로 값이 달라도 동작은 같다.

## 백엔드에 확인할 것
- 위 임시 API 두 개의 경로·page 형식·항목 필드 이름.
- **물품별 예약 현황 "기간순"의 방향**: 목은 startDate 오름차순(오래된 것부터)이다. 지난 대여가 20건 넘게 쌓인 물품(시드 107)은 다가오는 예약이 둘째 페이지에 가서 시트를 끝까지 내려야 보인다. 다가오는 예약을 먼저 보여 줄지(예: 진행·예정 먼저, 또는 내림차순) 정해야 한다. 정해지면 목(`mocks/pageList.js` `listItemRentals`)만 바꾸면 된다.
- 남의 물품 예약 현황의 403 코드(목은 `RENTAL_OWNER_ONLY`로 가정).
- `upcomingRentalCount`가 결제 시간이 지난 PENDING_PAYMENT도 세는지. 목은 REQ-020 문장 그대로(상태·시작일만) 센다.

## 목 (`mocks/`)
- **목록 계산은 `mocks/pageList.js` 순수 함수**(탭 표·정렬·자르기·다가오는 예약 수)로 빼서 node에서 테스트한다. 핸들러는 대여 목과 **같은 메모리 저장소(db)**에서 만들어, 대여 상세에서 상태를 바꾸고 돌아오면 목록 탭이 바뀐다.
- 빌린 내역 목: `role`이 없거나 renter가 아니면 400 `COMMON_INVALID_INPUT`(details role), 모르는 tab 400(details tab), page가 0 이상 정수가 아니면 400(details page). size는 보내도 무시. `canWriteReview`도 openapi 설명대로 계산한다(completedAt + 7일 정각 포함).
- 내 물품 목: tab 생략은 AVAILABLE로 가정, HIDDEN은 빈 목록(1순위엔 숨김이 없음).
- 물품 카탈로그에 내 물품 107·108을 더했다(`mocks/item.js` 맨 위 표). 108은 이미지가 없어(`thumbnailUrl: null`) 썸네일 자리 면을 본다. 내 물품에 `counts`(조회·관심·채팅 수)를 두었다.

| 추가한 시드 id | 상태 | 쓰임 |
| --- | --- | --- |
| 32 | RESERVED(D+5~D+6) | 물품 107 다가오는 예약 1건(대여 가능 탭) |
| 41~65 | COMPLETED(4건마다 CANCELED), D-30부터 과거 | 대여자 쪽 → 빌린 내역 완료 탭 27건(무한 스크롤 2페이지) |
| 71~92 | COMPLETED, D-10부터 과거 | 제공자 쪽 물품 107 → 예약 현황 23건(시트 안 더 불러오기) |

기존 시드 id·의미는 바꾸지 않았다. 새 예약 id는 지금도 900 미만 최대값 + 1(이제 93부터).

## 공통
- **`shared/lib/listParams.js`에 `getNextPageParam`·`flattenPages`·`normalizeTab`을 모았다**(파일 수를 줄이려고 한 파일). 미확정 응답은 `flattenPages(data, toMyItem)`처럼 항목 함수를 끼워 `select`에서 바꾼다. select 함수는 모듈 함수라 참조가 고정이다.
- **`Tabs`는 탭 줄과 패널(children)을 함께 그린다.** id 연결(aria-controls·aria-labelledby)을 컴포넌트 안에서 `useId`로 끝내려고. 탭 줄 sticky 같은 배치는 `listClassName`으로 화면이 정한다. 화살표·Home·End는 이동하면서 바로 선택(선택한 탭만 Tab 순서).
- **`InfiniteListFooter`(shared/ui)**: sentinel + 불러오는 중 스피너 + 실패 시 "다시 시도". `useInfiniteScroll`은 상태가 바뀔 때마다 observer를 새로 만들어, 다음 페이지를 받은 뒤에도 끝이 보이면 이어서 부르고, 다음 페이지 실패(`isFetchNextPageError`)면 멈춘다(자동 재시도 반복 방지).
- **다음 페이지 실패는 재조회 실패 토스트에서 뺐다**(`queryState.getRefetchErrorKey`가 `isFetchNextPageError`를 건너뜀). 헤드리스 확인에서 "다시 시도"와 "최신 정보를 불러오지 못했어요" 토스트가 함께 떠서. 무효화 뒤 다시 불러오기가 실패하는 경우는 그대로 토스트.
- **`Thumbnail`(shared/ui)**: 목록 썸네일 + 이미지 없을 때 자리 면. 피드에서도 쓸 수 있게 shared에 뒀다.
- **`['items', 'me']`는 `applyRentalDetail`에서 항상 무효화한다**(예약 생성 포함). 물품별 예약 현황(`['items', id, 'rentals']`)은 기존대로 기간 점유가 바뀔 때만 무효화하고, 시트는 열 때마다 마운트되며 staleTime이 5초라 상태만 바뀐 경우도 곧 다시 부른다.

## 화면 (시안 없음)
- **기준**: 카드는 디자인 시스템 물품 카드(Main.html)·홈 목록(Home.html)의 줄 구분(위아래 divider, 14px 간격), 탭은 시안이 없어 하단 탭 색 규칙(선택 Ink 600 + 2px 밑줄, 비선택 Ink muted), 높이 48px. 색·글자는 토큰만.
- **썸네일 64px**(지시대로). 내 물품도 64px로 맞췄다 — 관리 목록이라 버튼 줄이 있어 104px이면 카드가 너무 길다.
- **빌린 내역 카드**: 이름·상태 배지 / 제공자 / 기간 / 금액을 줄마다. 제공자와 기간을 한 줄에 두면 360px에서 기간이 줄바꿈돼서 나눴다. 금액 이름은 결제 꺼짐 "합계", 켜짐 "결제 금액"(REQ-019 표현. 대여 상세 금액 카드는 "총 결제 금액").
- **내 물품 카드 메뉴는 "수정" 버튼 하나**: 항목이 하나뿐이라 ⋮ 메뉴 대신 "예약 현황" 옆에 버튼으로 뒀다. 두 버튼 모두 aria-label에 물품 이름. 이름·썸네일·가격 영역이 물품 상세 링크.
- **다가오는 예약은 `info` 색 caption**(대여 예정 배지와 같은 색), 0건이면 숨김.
- **빈 상태**: 빌린 내역은 탭별 문구 + "홈으로", 내 물품 대여 가능 탭은 "물품 등록하기"(`/items/new`), 대여 중 탭은 문구만. 시트는 "아직 예약이 없어요".
- **`/me`는 하단 탭 화면이라 뒤로 가기 없이 제목만**(h-14, 헤더와 같은 높이), 메뉴 줄은 56px.
- **탭마다 목록 컴포넌트를 `key={tab}`으로 새로 그린다** — 스켈레톤·빈 상태·오류가 탭별로 정확히 나오고, 앞 탭의 무한 스크롤 상태가 섞이지 않는다.

## 확인
- 헤드리스 Chrome(CDP), `VITE_USE_MOCK=true`, 360px·390px(가로 넘침 없음).
  - `/me` 메뉴 2줄(56px, 링크), 하단 탭 보임.
  - 빌린 내역: 기본 대여 중 탭, 클릭·화살표(→ 순환, End)로 탭 이동하면 URL `?tab=`만 바뀌고 history 길이는 그대로. 대여 예정 정렬 1·2·3·5·4·6(startDate DESC, id DESC), 대여 중 8(연체) 먼저.
  - 완료 탭 끝까지 스크롤 → 20 → 27건, 더 부르지 않음.
  - 카드 → 시드 7 상세 → 반납 요청(체크리스트·사진) → 뒤로 → 같은 탭에서 7이 "상태 확인 대기"로 바뀜.
  - 스크롤 위치: 완료 탭 1500px에서 상세 → 뒤로 → 1500px 그대로(캐시가 남아 있어 브라우저 복원이 맞음). 그래서 "다음에"에 ScrollRestoration을 적지 않았다. 캐시가 지워진 뒤(5분 gc)에 돌아오면 복원되지 않을 수 있고, 라우트 lazy 분리 chore에서 같이 본다.
  - 다음 페이지 실패(worker.use로 page≥1을 500) → 목록 20건 유지 + "다시 시도", 토스트 없음 → 원래 핸들러로 돌린 뒤 다시 시도 → 27건.
  - 내 물품: 대여 가능 108·107(다가오는 예약 1건), 대여 중 104(3건)·103(1건). 버튼 높이 44px. 107 예약 현황 시트 20 → 시트 안 스크롤로 23건 → 마지막 줄(시드 32) → 대여 상세 제공자 뷰("대여자 텐트초보"), 시트 닫힘. 108 시트는 빈 상태, Esc로 닫힘.
- `npm run build`: JS 번들에 MSW·목 코드 없음. develop(5e9fe17) 507.82kB → 이 브랜치 521.90kB(+14.08kB, gzip +4.18kB). 500kB 청크 경고는 별도 chore에서(fe-rental-pages notes "다음에").

## 다음에
- 백엔드 로컬 서버 연동은 로그인 API 머지 전이라 아직 못 한다(fe-rental-pages notes와 같음). 내 물품·예약 현황 API가 머지되면 `toMyItem`·`toItemRental`과 목을 openapi.json에 맞춘다.
