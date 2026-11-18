# feat/fe-review 결정 기록

## 요약

- 화면: SCR-14 `/rentals/:id/review`(`pages/ReviewWritePage.jsx`, `features/review/`), 대여 상세·빌린 내역(완료 탭)에서 진입. 연동 API: `POST /api/rentals/{rentalId}/reviews`, `GET /api/members/{memberId}/reviews`(목·`ReceivedReviewList`까지만).
- 목: `mocks/review.js`·`reviewLogic.js`, 대여 시드 14·15·33 추가(후기는 대여 목 db에 둬 `canWriteReview`·`reviewWritten`이 바뀜).
- 남은 일: `ReceivedReviewList`는 SCR-13 프로필(REQ-021 백엔드 후)에 붙이고 비회원 공개(`isPublicApi`)도 그때 정한다. 빌린 내역 "작성 완료" 표시는 백엔드 `reviewWritten` 추가 대기(open-issues #54).
- 백엔드 확인: 탈퇴한 상대의 대여에서 상세 `counterpart.nickname`이 어떻게 오는지(null이면 후기 제목이 "null님"). 아래 "남은 일" 참고.

SCR-14 후기 작성 화면(REQ-034 프론트)과 진입 경로. 백엔드는 `feat/review`(머지됨).

## 결정

- **빌린 내역에는 "후기 쓰기"만 둔다.** `RentalHistoryResponse`에 `reviewWritten`이 없어 `canWriteReview` false가 "이미 씀"인지 "기한 지남"인지 목록에서 알 수 없다. 작성 완료 표시는 대여 상세(`reviewWritten`)에만 두고, 백엔드에 필드 추가를 요청한다(open-issues #54). 프론트에서 필드를 지어내지 않는다.
- **받은 후기 목록은 api·queries·목·`ReceivedReviewList`까지만.** 보일 화면(SCR-13 프로필)은 REQ-021 백엔드가 없어 그 브랜치에서 붙인다. 확인용으로 `/dev/ui` 미리보기에만 넣었다. `GET /members/{id}/reviews`는 비회원 공개지만 `isPublicApi`에 넣지 않았다 — 로그인했으면 토큰을 붙여도 결과가 같고, 비회원 화면이 생기는 SCR-13에서 정한다.
- **대여자·제공자 같은 화면.** 받는 사람은 상세 `counterpart`. 부제 "빌려준 분에게"(내가 대여자)/"빌린 분에게"(내가 제공자)는 UI 문구로 정했다(사용자 승인). 칭찬 라벨은 trust.md 그대로, 역할별로 나누지 않는다(#15).
- **쓸 수 있는지는 서버 값만 본다.** `canWriteReview`면 폼, 아니면 `reviewWritten` → "이미 보냈어요", COMPLETED → 기한 지남, 그 밖 → 완료 전. 기한을 프론트에서 다시 계산하지 않는다(서버 시각과 어긋날 수 있어서).
- **직접 들어왔는데 못 쓰면 안내 + "대여 상세로"(replace), 자동 이동 없음.** 자동 replace는 뒤로 가기와 섞여 루프가 생길 수 있다(홈 동네 안내와 같은 이유).
- **서버가 막으면(이미 씀·기한 지남·상태·당사자·없는 대여) 토스트 + 상세 무효화.** 다시 받은 상세로 페이지가 안내 화면으로 바뀌고 쓰던 내용은 버린다(다시 보내도 통과할 수 없어서). `COMMON_INVALID_INPUT`은 폼 안 오류로 두고 입력을 유지한다.
  - 없는 대여·당사자 아님(`RENTAL_NOT_FOUND`·`RENTAL_NOT_PARTICIPANT`)은 다시 받기도 같은 코드로 실패해 데이터가 남는다. 처음엔 "처음 불러오기 실패"만 봐서 폼이 그대로 남고 토스트가 두 번(제출 거절 + 다시 받기 실패) 떴다(리뷰 지적). 그래서 `isRentalUnavailable`로 데이터가 있어도 이 코드면 조회 실패와 같은 "볼 수 없어요" 화면으로 바꾸고, 이때는 다시 받기 실패 토스트를 빼서 제출 거절 토스트 한 번만 남긴다.
- **성공하면 무효화를 기다리지 않고 상세로 replace.** 기다리면 다시 받은 상세가 먼저 "이미 보냈어요" 안내로 바뀐 뒤 이동한다.
- **내용은 다듬지 않고 원문을 보낸다.** 공백뿐이면 빼서 보낸다. 백엔드 `normalizeContent`가 trim하지 않으므로(공백뿐→null만) 프론트도 같게 맞췄다 — 처음엔 trim했으나 리뷰에서 불일치로 고쳤다. 글자 수 검사도 원문 기준이라 서버와 같다.
- **탈퇴한 작성자는 `writer.isWithdrawn`으로 판정한다.** `nickname == null`로 보던 것을 응답의 명시 필드(openapi `ReceivedReviewWriter`)로 바꿨다(리뷰 제안). 탈퇴면 사진도 보이지 않는다.
- **내용 글자 수는 코드 포인트로 센다.** 백엔드 `ReviewService`가 `codePointCount`로 검사한다. 그래서 `TextField maxLength`(UTF-16) 대신 `ItemForm`처럼 힌트에 "n/300"을 보이고 validate로 막는다.
- **목 시드**: 대여 14(어제 완료, 대여자로 쓸 수 있음)·15(내가 씀)·33(제공자로 쓸 수 있음)을 더하고 10은 기한 지남으로 쓴다. 후기는 대여 목 db에 둬서 `canWriteReview`·`reviewWritten`이 실제로 바뀐다. 예전 sessionStorage에는 reviews가 없어 후기만 시드로 채운다.
- **알림**: `REVIEW_RECEIVED`는 대상이 RENTAL이라 기존 `getNotificationPath`로 대여 상세에 간다(테스트만 추가). 목에서 상대에게 알림을 만들지는 않는다.

## 남은 일

- 백엔드 확인 거리: 탈퇴한 상대의 대여에서 상세 `counterpart.nickname`이 어떻게 오는지(null인지, 탈퇴 표시 필드가 있는지). 후기 작성 화면 제목이 "{nickname}님과의 거래는 어땠나요?"라 null이면 "null님"이 된다. 코드는 바꾸지 않았다.

## 확인

- 헤드리스 Chrome(CDP)으로 목 서버 화면을 확인했다: 14 상세 "후기 쓰기" → 폼 → 만족도 없이 제출하면 오류 → 제출 후 상세로 이동·"후기를 보냈어요", 15·10·7 안내, 33 제공자 부제, 빌린 내역 완료 탭 "후기 쓰기", 미리보기의 받은 후기 목록. 앱 브라우저 패널은 MSW 서비스 워커를 등록하지 못해 쓰지 않았다.
