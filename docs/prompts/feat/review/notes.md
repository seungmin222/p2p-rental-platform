# feat/review 결정 기록

## 요약
- API: `POST /api/rentals/{rentalId}/reviews`(작성), `GET /api/members/{memberId}/reviews`(받은 후기, `review.page` 고정). 대여 상세에 `canWriteReview`·`reviewWritten` 추가, 빌린 내역 `canWriteReview`에 미작성 조건 반영.
- 주요 파일: `review/` 패키지(`ReviewService`·`ReviewTxService`·`ReviewQueryService`·`ReviewNotificationListener`), `rental/service/RentalReviewQueryPort`·`RentalParticipantReader`, `V18__create_review_tables.sql`. 새 ErrorCode `MEMBER_NOT_FOUND`.
- 다음: 신뢰 점수(054)는 `ReviewWrittenEvent`를 구독해 trust_score_events를 쌓는다. 프로필(021)은 `MEMBER_NOT_FOUND`를 재사용하고 탈퇴 회원 후기 목록 공개 여부를 정한다. 프론트 SCR-14(후기 작성)·프로필 받은 후기 목록은 미구현.
- `AiCategoryIntegrationTest` 5개 실패는 develop에서도 재현(이 브랜치와 무관).

REQ-034 거래 후기(백엔드). 프론트 SCR-14 화면과 신뢰 점수(054)는 범위 밖. 마이그레이션 `V18__create_review_tables.sql`.

## 스키마
- **`review_compliments`는 JPA `@ElementCollection`(PK review_id+type)이고 시각 컬럼을 두지 않았다.** 후기와 함께 한 번 저장하고 바꾸지 않는 값이라 `BaseTimeEntity` 규칙의 예외로 erd.md에 적었다. 같은 항목이 중복으로 와도 PK 때문에 하나로 합쳐 저장한다(요청에서 400으로 막지 않음).
- **reviews에 작성자 역할 컬럼을 두지 않는다.** 목록의 `writerRole`은 대여의 renter_id와 비교해 정한다(대여 일괄 조회 쿼리 1개). erd.md에 없는 컬럼을 늘리지 않으려고.
- `INDEX(target_id, id)`: 받은 후기 목록(대상별 id DESC).

## 작성
- **가드 순서: 대여 → 당사자(403) → COMPLETED(409 `RENTAL_INVALID_TRANSITION`) → 기한(400) → 1회(409).** trust.md는 COMPLETED가 아닐 때의 코드를 정하지 않아, 상태 때문에 안 되는 요청에 쓰는 기존 코드를 재사용했다.
- **중복은 서비스 검사 + UK 둘 다.** 동시 요청이 검사를 같이 지나면 늦은 쪽이 UK 위반 → 바깥 서비스(`ReviewService`, 트랜잭션 밖)가 같은 (대여, 작성자) 행이 있으면 409로 바꾸고, 없으면(FK 등) 원래 예외를 던진다. 채팅방 생성과 같은 구조. 동시 8건 테스트로 1건 저장·알림 1건을 고정.
- **내용은 공백만이면 null, 길이는 코드 포인트 기준 `review.content.max`.** 문의(063)와 같은 검사 방식(서비스에서, 400 details content).
- **알림 키는 `REVIEW_RECEIVED:{rentalId}`, 대상은 RENTAL.** 계획 때는 reviewId 키를 제안했지만, notification notes의 "한 대여에 한 번인 알림은 `{TYPE}:{rentalId}`" 규칙에 맞췄다(받는 회원 기준 대여당 1번이라 같은 뜻). 대상을 RENTAL로 둔 것은 받은 쪽이 대여 상세에서 바로 답 후기를 쓸 수 있어서다.
- **`ReviewWrittenEvent`(reviewId, rentalId, writerId, targetId)를 커밋 뒤 리스너가 받는다.** REQ-054는 이 이벤트를 구독해 trust_score_events를 쌓으면 된다(source_id = reviewId). 후기는 수정·삭제가 없어 reviews·review_compliments만으로 delta를 다시 계산할 수 있다. 이번엔 Member.trustScore를 건드리지 않는다.

## 조회
- **받은 후기는 `review.page`(20) 고정, size를 받지 않는다**(policies.md에 추가, 사용자 승인). 최신순 id DESC. 쿼리: 회원 확인 1 + 후기 1 + count 1 + 칭찬 1 + 대여 1 + 작성자 요약 3.
- **작성자는 `MemberPublicSummary`에서 회원 ID·닉네임·프로필 사진·탈퇴 여부만. 탈퇴 작성자는 닉네임·사진을 null로 비운다**(채팅 상대와 같은 방식, 화면이 "탈퇴한 사용자" 표시). 동네 이름·물품 이름은 넣지 않았다.
- **없는 회원은 404 `MEMBER_NOT_FOUND`(새 ErrorCode).** 021 프로필도 같은 코드를 쓰면 된다. 대상 회원이 탈퇴했을 때의 비공개 처리는 021에서 정한다(지금은 그대로 목록을 준다).

## 대여 쪽 표시 (019·016)
- **rental이 review를 직접 알지 않게 `RentalReviewQueryPort`(rental에 정의, `ReviewQueryService`가 구현).** `RentalDepositQueryPort`와 같은 방식. 후기 쪽은 `RentalParticipantReader`(`getReviewTarget`, `getAll`)로 대여를 읽는다.
- **빌린 내역은 작성 기간 안의 건만 모아 쿼리 1개로 작성 여부를 확인한다.** 기간 안 건이 없으면 쿼리를 내지 않아 기존 쿼리 수 테스트가 그대로다.
- **대여 상세에 `canWriteReview`·`reviewWritten`을 추가했다.** 제공자는 빌린 내역이 없어 상세가 후기 작성 진입점이다. 상세는 상태와 상관없이 1개 쿼리를 더해 쿼리 수를 일정하게 둔다(6 → 7).
