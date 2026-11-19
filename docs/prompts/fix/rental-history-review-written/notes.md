# fix/rental-history-review-written 결정 기록

## 요약

- API: `GET /api/members/me/rentals?role=renter`(빌린 내역) 항목에 `reviewWritten` 추가. 경로 변경 없음, `canWriteReview = !reviewWritten && 작성 기간 안`(대여 상세와 같은 식).
- 변경 파일: `rental/dto/RentalHistoryResponse.java`, `rental/service/RentalHistoryService.java`, `RentalHistoryIntegrationTest.java`. 새 파일 없음.
- 후기 작성 여부는 COMPLETED 건 전체를 페이지당 쿼리 1개로 조회(COMPLETED 없으면 쿼리 없음).
- 남은 일: 탈퇴한 상대 표시(`isWithdrawn`·마스킹)는 open-issues #55, REQ-043 때 정한다. 프론트는 빌린 내역에서 `reviewWritten`으로 "후기 작성 완료" 표시를 붙일 수 있다.

빌린 내역 응답에 `reviewWritten` 추가(open-issues #54), 탈퇴한 상대의 `counterpart.nickname` 확인. REQ-019, REQ-034 백엔드.

## 결정

- **`reviewWritten`은 대여 상세와 같은 뜻이다(조회한 회원이 이 대여에 후기를 썼는지).** `canWriteReview`는 `!reviewWritten && 작성 기간 안`으로 상세(`RentalQueryService`)와 같은 식이다.
- **후기 작성 여부 조회 대상을 "작성 기간 안의 건"에서 "COMPLETED 건 전체"로 넓혔다.** 기한 지난 건도 썼는지를 줘야 하고, 후기는 COMPLETED에서만 쓸 수 있어 다른 상태는 볼 필요가 없다. 페이지 단위 `findWrittenRentalIds` 1번 그대로이고 COMPLETED 건이 없으면 쿼리를 내지 않는다. 그 결과 기한 지난 완료 건만 있는 페이지는 쿼리가 1개 늘었다(4→5).
- **탈퇴한 상대는 이번에 바꾸지 않고 open-issues #55에 기록했다(사용자 결정 A).** 확인 결과: 코드에 회원을 WITHDRAWN으로 바꾸는 곳이 없고(REQ-043 3순위 미구현, 테스트 SQL에서만 생김), `members.nickname`이 NOT NULL이라 상세(`getNickname`)·빌린 내역(`getNicknames`)은 탈퇴 상대에게도 원래 닉네임을 준다. 지금은 null이 오지 않아 후기 작성 화면이 "null님"이 되지 않는다. `isWithdrawn` 추가(B안)는 회원 조회를 `getPublicSummaries`로 바꿔 쿼리 2개가 늘고 마스킹 방식도 정해야 해서, REQ-043 때 함께 정한다.
