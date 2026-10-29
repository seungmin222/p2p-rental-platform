# 물품·피드·검색·카테고리 (item)

담당: 신수정(B) · 화면: SCR-03, 04, 05, 06, 12 · 엔티티: items, item_images, item_available_periods, categories, damage_policies, wishes, item_view_logs

## REQ-006 물품 등록 [1]
`POST /api/items` (multipart)

| 필드 | 규칙 |
| --- | --- |
| name | 필수, ≤50자 |
| description | 필수, ≤2,000자 |
| images | 필수 1~10장, jpg/png, 장당 10MB, 순서 = sortOrder |
| categoryId | 필수, 활성 카테고리 |
| condition | `LIKE_NEW` / `LIGHTLY_USED` / `USED` |
| pricePerDay | 0원(무료) 또는 1,000원 이상, 상한은 `policies.md` `item.price-per-day` |
| itemPrice | 0 ~ 10,000,000 (**대여자에게 비공개**) |
| deposit | 0원 또는 1,000원 이상, 상한은 `policies.md` `item.deposit` |
| availablePeriods | 1개 이상 `[{startDate, endDate}]`, startDate ≥ 오늘, start ≤ end, 구간끼리 겹침 불가 |
| address, latitude, longitude | 필수 (카카오맵에서 선택) |

- 대표 동네가 인증된 회원만 (403 `REGION_NOT_VERIFIED`). 물품.regionId = 대표 동네
- 이미지 S3 업로드 + 썸네일(가로 400px) 생성, URL만 저장
- status=AVAILABLE, bumpedAt=now
- **권장 보증금**: 카테고리+물품가격 입력 시 `GET /api/items/deposit-guide`로 범위 안내(`itemPrice × minRate ~ maxRate`, `policies.md` §4). 범위 밖이어도 등록 허용
- itemPrice 용도: 권장 보증금 계산, D 등급 판단. 상세·목록 응답 DTO에 **절대 포함하지 않는다**(소유자용 수정 화면 응답만 예외)
- 예외: 누락·형식 400, 비활성·없는 카테고리 400 `COMMON_INVALID_INPUT`(details categoryId), 사진 400 `IMAGE_COUNT_INVALID`·`IMAGE_INVALID_TYPE`·`IMAGE_SIZE_EXCEEDED`(대여 사진과 같은 코드, open-issues #18)
- 3순위 연결: REQ-066 금지어 필터, REQ-067 AI 등록 보조, REQ-068 AI 금지 품목 판별이 이 흐름에 붙는다

## REQ-007 물품 수정 [1]
`PATCH /api/items/{itemId}` — 변경 항목만
- 소유자 검증(403 `ITEM_NOT_OWNER`)
- 수정 화면은 `GET /api/members/me/items/{itemId}`(소유자 전용, itemPrice·가능 기간·이미지 id 포함)로 채운다(open-issues #16)
- **대여 가능 기간 수정**: ACTIVE_FOR_PERIOD 대여가 차지한 날짜를 가능 기간에서 빼면 409 `ITEM_PERIOD_CONFLICT` + 충돌 기간 목록(`error.details`에 한 건씩, open-issues #17). 예약 생성과 **같은 락**(`lock:item:{id}`) 안에서 검사·저장
- 가격·보증금 변경은 이후 신규 예약부터 적용(대여 건에 스냅샷 저장되어 있음)
- 이미지 변경: 유지할 기존 이미지 id 목록 + 새 파일 + 순서 → `imageOrder` 하나로 표현(각 칸 `imageId` 또는 `newImageIndex`, 목록 순서 = sortOrder, 빠진 기존 이미지는 삭제, null이면 변경 없음)

## REQ-008 물품 삭제 [1]
`DELETE /api/items/{itemId}` — 소프트 삭제(status=DELETED)
- PENDING_PAYMENT/RESERVED/RENTING/OVERDUE/RETURNED 대여가 있으면 409 `ITEM_HAS_ACTIVE_RENTAL` + 해당 건(`error.details`에 한 건씩)
- 삭제 물품: 피드·검색 제외, 과거 대여 내역·채팅에서는 "삭제된 물품" 표시

## REQ-009 동네 물품 피드 [1]
`GET /api/items/feed?regionId=&range=&categoryId=&cursor=`
- 기준: 지정 regionId(생략하면 로그인 회원의 대표 동네, 비회원·대표 동네 없음이면 400)의 중심 좌표에서 범위(NEAR 1.5/NORMAL 3/FAR 6km) 내 **거래 장소 좌표**를 가진 AVAILABLE 물품
- 정렬: bumpedAt DESC, id DESC. 커서 = `(bumpedAt, id)` 인코딩. 20건
- 카드: 대표 썸네일, 이름, 동네명, 경과 시간(bumpedAt), pricePerDay, deposit, wishCount·chatCount, "대여 중" 배지(현재 RENTING/OVERDUE 대여 존재)
- 제외: HIDDEN·DELETED, 차단 사용자 물품(REQ-056 이후)
- 단계적 적용: 범위 선택(042)·끌어올리기(045)·숨김(044)·차단(056) 전에는 NORMAL·등록 시각 순으로 동작
- 비회원/동네 미설정: 프론트가 동네 선택 화면 먼저 표시, 비회원은 regionId를 쿼리로 직접 보냄
- 결과 없음 → 빈 목록 (프론트가 "범위 넓히기" 안내)
- **성능 과제 ④**: 1차 구현은 bounding box(`lat BETWEEN`, `lng BETWEEN`) 선필터 + Haversine(`ST_Distance_Sphere`) 정확 필터. 이후 공간 인덱스/Geohash, Redis 캐시를 실험하고 전후 수치를 `docs/experiments/feed.md`에 기록

## REQ-010 물품 필터링 검색 [1]
`GET /api/items?...&page=` — 비회원 가능, 페이지 기반 20건
- 조건: keyword(이름·설명), categoryId, minPrice~maxPrice(pricePerDay), startDate~endDate, availableOnly, sort(`ACCURACY` 기본 / `LATEST` / `PRICE_ASC`)
- 범위: 설정 동네 범위 내로 한정(피드와 같은 위치 조건)
- **대여 희망 기간 입력 시**: 그 기간 전체가 어떤 available_period 하나에 포함되고, ACTIVE_FOR_PERIOD 대여와 겹치지 않는 물품만
- availableOnly: 오늘 기준 대여 가능한 물품 (정의 확정 필요 → open-issues #6)
- DELETED 제외(HIDDEN도 제외)
- startDate > endDate → 400
- 로그인 회원 검색어 → 최근·인기 검색어 저장(REQ-046 구현 시)
- "정확도순": 초기에는 이름 일치 > 설명 일치 > 최신순 같은 단순 규칙으로 시작

## REQ-011 물품 상세 조회 [1]
`GET /api/items/{itemId}` — 비회원 가능
- 반환: 이미지들, 이름, 설명, 카테고리, condition, pricePerDay, deposit, 거래 장소(주소·좌표), bumpedAt, viewCount, wishCount, chatCount, 제공자 요약(id, 닉네임, 동네명, 신뢰 점수[054 이후]), `isOwner`, `isWished`
- 조회수: Redis `view:{itemId}:{memberId|ip}` 24시간 1회 → `viewcount:{itemId}` 증가 → 주기적 DB 반영
- 로그인 회원 조회 → item_view_logs 기록(REQ-035 구현 시)
- 예외: 없음/DELETED 404, HIDDEN은 소유자만(그 외 404)
- 본인 물품이면 프론트는 예약 대신 수정·관리 버튼

## REQ-030 거래 장소 지도 표시 [2] (주 담당: 프론트)
- 등록 화면: 카카오맵 JS API 주소 검색 또는 지도 클릭 → 주소·위경도를 REQ-006 필드로 전송 (1순위에서도 좌표는 필수 — 지도 UI가 없으면 주소 검색 결과 좌표로 대체)
- 상세 화면: 저장된 위경도에 마커 + 주소
- 지도 로드 실패 → 주소 텍스트만 표시

## REQ-022 관심(찜) [2]
POST/DELETE `/api/items/{itemId}/wishes`, `GET /api/members/me/wishes`
- 토글. UK(member, item). wishCount 함께 갱신(원자적 `UPDATE ... SET wish_count = wish_count ± 1`)
- 본인 물품 불가 400 `WISH_OWN_ITEM`, 비회원은 프론트에서 로그인 유도
- 목록 최근 추가순, 삭제 물품은 "삭제된 물품" 표시
- 관심 추가 시 추천 캐시 무효화(REQ-060 이후)

## REQ-035 / REQ-060 최근 본 상품 [2] · 개인화 추천 [3]
- 최근 본 상품: 상세 조회 시 (member, item) upsert(viewedAt 갱신), 회원당 최신 20건 유지. 삭제·숨김 물품 제외해서 반환
- 추천(규칙 기반, 학습 모델 아님):
  - 선호 카테고리 점수 = Σ(관심 3점 + 조회 1점) × 최근성(7일 이내 1.0, 8~30일 0.5, 이후 0)
  - 상위 3개 카테고리 × 내 동네 범위 × 오늘 대여 가능 → 최신순
  - 제외: 이미 본 물품, 관심 물품, 본인 물품, 차단 사용자 물품
  - 부족하면 동네 인기 물품(관심수·조회수)으로 채워 최대 10개. 그래도 4개 미만이면 섹션 숨김(빈 배열 반환)
  - Redis `recommend:{memberId}` TTL 1시간, 관심 추가 시 무효화
  - 비회원: 조회 이력 저장 안 함, 인기 물품 표시

## 카테고리 관리 [1 조회 / 3 관리]
- `GET /api/categories`: isActive=true, sortOrder 순. 애플리케이션 캐시, 변경 시 무효화
- 초기 데이터와 damage_policies는 Flyway 시드로 적재(`policies.md` §3·§4)
- 관리자 추가·이름 변경·순서·숨김, AI 제안 후보 승인/반려는 REQ-057·067

## 3순위 요약
- **044 숨기기**: status AVAILABLE ⇄ HIDDEN. 피드·검색 제외, 진행 중 대여는 영향 없음
- **045 끌어올리기**: `now - bumpedAt ≥ 24h`일 때만 bumpedAt=now. 위반 409 `ITEM_BUMP_TOO_EARLY`
- **046 최근·인기 검색어**: Redis List(최근, 회원별) / Sorted Set(인기)
- **047 다른·비슷한 물품**: 같은 제공자 물품 / 같은 카테고리 인근 물품
- **048 공유**: 링크 복사, OG 태그(SPA라 서버 사이드 메타 처리 필요 — 착수 시 결정)
- **064 자동완성**, **065 거리순 정렬·지도 탐색**

## 테스트 포인트
- 가능 기간 구간 겹침 검증, 예약 존재 기간 제거 시 409
- 상세 응답에 itemPrice 미포함
- 피드 커서 경계(같은 bumpedAt 다수), 범위 경계 거리
- 기간 검색: 가능 기간 경계·기존 예약 경계(겹침 조건 `<=`, `>=`)
