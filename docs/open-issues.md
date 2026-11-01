# 미결정·불일치 사항

명세서(v1.0)를 에이전트용 문서로 옮기면서 발견한 모순·공백과 그 결정 기록이다. **결정이 나면 "결정"을 채우고 관련 문서를 고친 뒤 ✅로 표시**한다. 에이전트는 미결(⬜) 항목에 걸린 영역을 작업할 때 임의로 정하지 말고 사용자에게 확인한다.

## 1. 미결·결정 목록

| # | 상태 | 영역 | 내용 | 결정 |
| --- | --- | --- | --- | --- |
| 1 | ✅ | ERD | 원본 ERD는 RENTAL–PAYMENT 1:1인데 연장(REQ-038)은 추가 결제를 만든다 | payments 1:N, `type=RENTAL/EXTENSION`. 보증금 상태는 RENTAL 결제에만 |
| 2 | ✅ | ERD | `Member.trustScore`가 int인데 칭찬 항목은 +0.5 단위 | `DECIMAL(4,1)` |
| 3 | ✅ | 연장 | 연장 추가 결제 중 연장 기간 선점 | rental_extensions `PENDING_PAYMENT`(15분 만료)를 겹침 검사 대상에 포함 |
| 4 | ✅ | API | 인계 사진 등록이 `PATCH /handover`와 `POST /inspections` 두 곳 | `PATCH /handover`(multipart) 하나로. `GET /inspections`는 조회 전용 |
| 5 | ✅ | 결제 | 보증금 반환 실패 시 재시도·대여 상태 | RETURNED 유지, 10분 간격 최대 5회 후 관리자 확인 플래그 |
| 6 | ✅ | 검색 | "대여 가능만 보기"의 의미 | 대여 희망 기간이 있으면 그 기간 기준, 없으면 "오늘 이후 가능 기간이 남아 있고 오늘이 예약되어 있지 않음" |
| 7 | ⬜ | 대여 | 연체 중인 물품에 다음 예약자가 있으면 다음 예약을 이행할 수 없음 | (제안) 다음 예약자에게 알림 + 다음 예약자 단독 취소 시 수수료 없이 전액 환불, 페널티 미반영 |
| 8 | ✅ | 대여 | 1순위 단계에서 제공자 '이상' 판정 | 판정만 기록하고 COMPLETED (차감 흐름은 2순위) |
| 9 | ✅ | 정산 | 정산 HELD 트리거 | 정산 생성 후 문의(REQ-063) 분쟁 접수 시에만 HELD |
| 10 | ✅ | 정산 | "영업일 +2"의 공휴일 처리 | 주말만 제외(공휴일 무시), 보고서에 명시 |
| 11 | ✅ | API | 검색 API에 기준 동네 파라미터 없음 | `regionId`, `range` 추가 (비회원도 전달) |
| 12 | ✅ | 파일 | 이미지 업로드 방식 | 서버 경유 multipart (서버에서 썸네일 생성). 저장소는 `FileStorage`(운영 S3, 로컬 디스크) |
| 13 | ✅ | 취소 | 시작일 이후 양측 동의 취소 흐름 | 한쪽 요청 → 상대 동의 시 취소, 요청은 48시간 후 만료. `rental_cancel_agreements` |
| 14 | ⬜ | STOMP | 목적지 접두사 확정 | (제안) api.md 표기대로 `/pub`, `/sub`, `/user/queue/*` |
| 15 | ⬜ | 후기 | 칭찬 항목을 대여자용/제공자용으로 나눌지 | (제안) 공통 목록으로 시작 |
| 16 | ✅ | API | itemPrice는 "소유자 수정 화면 응답만 예외"인데 그 화면을 채울 조회 API가 없음 | `GET /api/members/me/items/{itemId}`(소유자 전용, Item 태그, REQ-007). itemPrice·가능 기간·이미지 id 포함. 남의 물품 403 `ITEM_NOT_OWNER`, 없음·삭제 404 `ITEM_NOT_FOUND`. 물품 수정 응답도 같은 형태 |
| 17 | ✅ | API | 409 충돌 목록(`ITEM_PERIOD_CONFLICT` 충돌 기간, `ITEM_HAS_ACTIVE_RENTAL` 해당 건)을 실을 자리. 대여 쪽 선례 없음 | 공통 `ErrorResponse`를 바꾸지 않고 `error.details`에 한 건씩 `{ field, reason }`: field는 `availablePeriods`/`rentals`, reason은 화면에 그대로 보일 문구(예: `2026-11-03 ~ 2026-11-05`). 프론트는 reason을 나열만 한다 |
| 18 | ✅ | API | item.md의 이미지 오류 `FILE_INVALID`와 이미 있는 `IMAGE_*` 코드 | `IMAGE_COUNT_INVALID`·`IMAGE_INVALID_TYPE`·`IMAGE_SIZE_EXCEEDED`를 물품·프로필에도 쓴다. item.md 수정 |
| 19 | ✅ | API | 물품 수정의 이미지 변경 표현("유지할 기존 id + 새 파일 + 순서") | request part의 `imageOrder` 목록 하나: 각 칸 `{imageId}` 또는 `{newImageIndex}`(images 파트 0부터 순번) 중 정확히 하나, 순서 = sortOrder, 빠진 기존 이미지 삭제, null이면 변경 없음(이때 파일을 보내면 400). 최종 장수는 서비스가 `IMAGE_COUNT_INVALID`로 검사 |
| 20 | ✅ | API | 물품별 예약 현황(REQ-020) 응답 형태·"기간순" 정렬, 내 물품·예약 현황 페이지 크기 | 둘 다 `page` + `PageResponse`, size는 받지 않고 `my-items.page`·`item-rentals.page`(20) 고정. 예약 현황 정렬: 다가오는·진행 중 건(PENDING_PAYMENT~RETURNED) 시작일 오름차순 먼저, 완료·취소 건 시작일 내림차순 뒤에(지난 건이 쌓여도 다가오는 예약이 첫 페이지에 보이게). CANCELED 포함. 검색도 `search.page` 고정으로 size를 받지 않는다 |
| 21 | ✅ | API | 피드(인증 X)·검색의 "기본 대표 동네"는 로그인 정보가 있어야 앎 | regionId 생략 시 로그인 회원의 대표 동네, 비회원·대표 동네 없음이면 400 `COMMON_INVALID_INPUT`(details regionId). 피드 인증을 `옵션`으로 바꿈(api.md) |
| 22 | ✅ | 회원 | 같은 동네를 두 번 추가하는 경우의 에러 코드가 없음 | 409 `REGION_ALREADY_ADDED` "이미 추가한 동네입니다." |
| 23 | ✅ | ERD | 권장 보증금 API(`GET /api/items/deposit-guide`, api.md 1순위)가 쓰는 `damage_policies`가 erd.md에서 결제 [2] | `damage_policies` 테이블·시드를 1순위로 당긴다(erd.md [1]). 생성·시드는 `feat/item-crud` |
| 24 | ✅ | 인증 | 비밀번호 변경(REQ-005)은 "현재 요청의 family를 뺀 나머지"를 지우는데, RT 쿠키는 Path=`/api/auth`라 `/api/members/me/password`로 오지 않고 AT claim에도 familyId가 없음 | Access Token claim에 `familyId`를 추가한다(API 변경 없음, member.md REQ-002). 구현은 `feat/member-auth` |
| 25 | ✅ | API | 물품별 예약 현황의 "남의 물품" 403 코드(프론트 가정은 `RENTAL_OWNER_ONLY`) | 물품 소유자가 아니면 `ITEM_NOT_OWNER`로 통일. `RENTAL_OWNER_ONLY`는 대여 건의 제공자 검사(인계·상태 확인)에 그대로 쓴다 |
| 26 | ✅ | 회원 | 비밀번호 "특수문자"의 범위·공백·한글 허용 여부 | 공백 없는 ASCII 출력 문자(`!`~`~`)만 허용, 영문·숫자·ASCII 기호(영문·숫자가 아닌 ASCII 출력 문자)를 각각 1개 이상. policies.md `member.password` |
| 27 | ✅ | 물품 | 물품 삭제 검사(진행 중 대여)와 동시 예약의 경합 | 삭제도 `lock:item:{itemId}` 안에서 검사·삭제. 락 대기 실패는 409 `RENTAL_LOCK_TIMEOUT`(backend/CLAUDE.md 락 대상에 추가) |
| 28 | ✅ | 검색 | minPrice > maxPrice, keyword 길이 | minPrice > maxPrice면 400 `COMMON_INVALID_INPUT`(details minPrice). keyword 길이는 제한하지 않는다 |
| 29 | ⬜ | 물품 | REQ-030 명세는 지도 UI가 없을 때 주소 검색 결과 좌표로 대체하라고 하는데, 등록·수정 화면은 현재 위치만 지원한다. 거래 장소에 가서 눌러야 하고 주소와 좌표가 어긋날 수 있어 피드 거리 필터(동네 반경)에 영향을 준다 | (제안) 지금은 현재 위치를 유지하고, 주소 검색은 REQ-030 지도 작업 때 함께 넣는다 |
| 30 | ✅ | 프론트 공통 | `shared/api/client.js`의 응답 인터셉터(`response.data?.data`)가 2xx 응답의 공통 래퍼를 검사하지 않아, `CLIENT_INVALID_RESPONSE`는 실패 응답에서만 나온다. 프록시·폴백의 200 HTML은 `undefined`로 통과한다. 영향: 조회는 "data is undefined" 원문이 화면에 보이고, 쓰기 요청은 처리되지 않았어도 **성공으로 처리될 수 있다** | 2xx에서 `success === true`만 통과시키고 나머지(`success: false` 포함)는 `CLIENT_INVALID_RESPONSE`로 거부한다(`fix/fe-api-response-guard`) |
| 31 | ⬜ | 프론트 공통 | 401 뒤 재발급이 200 HTML을 받으면 토큰이 지워지고 로그인 화면으로 이동한다(`shared/api/reissue.js`·`client.js`). 세션이 만료되지 않았어도 일시적인 응답 이상으로 로그아웃될 수 있다 | (제안) 일시적 응답 이상(5xx·HTML·네트워크)에도 로그아웃할지 정한다 |
| 32 | ✅ | 결제 | REQ-025는 보증금 반환 후 항상 PARTIAL_CANCELED라고 썼지만, 대여료 0원 + 보증금 전액 반환은 결제 금액 전부를 취소하는 경우다(state-machines §4 CANCELED = 전액 취소) | 누적 취소가 결제 금액에 닿으면 CANCELED, 아니면 PARTIAL_CANCELED. payment.md 수정 |
| 33 | ⬜ | 결제 | 환불 재시도는 같은 멱등성 키를 쓰는데(`cancel-refund:`·`deposit-refund:`), 토스가 실패 응답까지 재생하면 일시적 오류도 재시도 5회가 모두 실패해 관리자 확인으로 넘어간다. 토스 문서로는 확정되지 않았고 실제 호출로 확인하지 못했다 | (제안) 결제 화면 브랜치에서 실제 토스로 확인한 뒤 정한다. payment-enabled를 켜기 전에 확인하고, 재생하면 재시도 키 전략을 바꾼다 |
| 34 | ✅ | 회원 | 카카오 좌표 변환 응답에 행정동(H)이 없는 경우의 인증 오류 코드가 미정 | PR #33 이승민 리뷰(2026-11-01): verify는 400 `REGION_VERIFY_MISMATCH`, by-coord는 404 `COMMON_NOT_FOUND` 유지. 경로 id 없음·다른 회원 동네의 404는 유지. 공용 카카오 클라이언트 대신 verify 서비스에서 변환. member.md REQ-004에 반영 |

## 2. 정책 결정 기록 (기획 담당 결정, 2026-10-08)

원본 명세서와 달라진 정책이다. 보고서·발표에서 "설계 중 변경 사항"으로 설명한다.

| # | 영역 | 원본 명세 | 결정 | 이유 | 반영 문서 |
| --- | --- | --- | --- | --- | --- |
| P1 | 취소 | 시작일 전날까지 전액 환불 | 대여자 취소: 3일 전까지 무료, 2일 전~전날 대여료 30% 수수료. 제공자 취소는 대여자 전액 환불 + 제공자 사유 취소 기록 | 직전 취소로 인한 제공자 손실 보전, 제공자 직전 취소 억제 | policies §2, rental REQ-015, payment |
| P2 | 연체 | 금전 불이익 없음 (신뢰 점수만, 3순위) | 연체 1일당 일 대여료 150%를 보증금에서 차감, 반납 하루 전 리마인더 | 2순위 범위에서도 반납을 유도할 장치 필요 | policies §1·§4, rental REQ-023 |
| P3 | 인계 | 인계 없으면 자동 취소하지 않음 | 시작일 + 3일까지 인계 없으면 자동 취소, 전액 환불 | 양측 무응답 시 결제금이 영구히 묶이는 문제 | rental REQ-015·017, state-machines |
| P4 | 반납 | 미반납 경로 없음 | 예정일 + 7일 지난 연체 건은 제공자가 미반납 신고 → 사진 없이 반납 완료 전환 → D 등급 차감 | 물건이 없으면 반납 사진을 찍을 수 없어 거래가 끝나지 않음 | rental REQ-018, payment REQ-049 |
| P5 | 보증금 분쟁 | 7일 미합의 시 관리자 확정(3순위) | 대여자 72시간 무응답 시 자동 수락. 관리자 확정 API 2개만 2순위로 앞당김 | 2순위 필수 기능(049)이 3순위 기능에 의존하던 구조 해소 | payment REQ-049, api, admin |
| P6 | 금액 | 0원 처리 규칙 없음 | 일 대여료·보증금은 0원 또는 1,000원 이상. 0원 결제·0원 반환은 PG 생략. 보증금 상태 `NONE`·`FORFEITED` 추가 | PG는 0원·소액 결제를 처리하지 못함 | policies §1, payment, state-machines |
| P7 | API | `POST /api/payments/{id}/cancel` 회원 공개 | 외부 API 제거, 서버 내부 호출만 | 대여자가 직접 환불을 호출할 수 있었음 | api, payment |
| P8 | 동시성 | 분산 락 대기 3초·임대 5초 | 임대 시간 미지정(watchdog). 비교 실험에 DB 유니크 인덱스(`rental_dates`) 추가 | 임대 시간 고정 시 긴 트랜잭션 중 락 해제 → 중복 예약 가능. 원본 도전 과제 ①의 비교 대상 복원 | rental REQ-013, architecture §8, erd |
| P9 | 인증 | RT를 회원당 1개(`rt:{memberId}`) | 로그인(기기)별 family로 저장, 재사용 감지 시 그 family만 무효화 | 두 기기 사용 시 정상 사용자가 로그아웃되던 문제 | member REQ-002·003·005, erd |
