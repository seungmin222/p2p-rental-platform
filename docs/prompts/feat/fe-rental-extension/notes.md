# feat/fe-rental-extension 결정 기록 (REQ-038 프론트, open-issues #54 프론트)

## 요약
- API: `GET /api/rentals/{id}` 응답에 `extensionCount`(양측)·`pendingExtension`(대여자·RENTING·만료 전 결제 대기 연장) 추가(백엔드 `RentalDetailResponse`·`RentalQueryService`·`RentalExtensionRepository.findPending`). 프론트는 `POST /api/rentals/{id}/extensions`, `POST /api/payments { extensionId }`, `/api/payments/confirm`(type 분기)을 쓴다.
- 주요 파일: `features/rental/rentalExtension.js`(날짜 칩·maxEndDate 순수 함수), `features/rental/components/ExtensionSheet.jsx`, `rentalActions.js`(EXTEND·PAY_EXTENSION), `features/payment/startPayment.js`·`pendingPayment.js`·`paymentResult.js`(연장 분기), `mocks/rental.js`·`payment.js`(연장 목).
- 남은 일: 검색 필터·007 기간 수정 목은 결제 대기 연장 점유를 넣지 않았다(실제 백엔드 007은 `RentalOccupancyReader`를 써야 한다, rental-extension 요약). `RENTAL_EXTENDED` 알림은 백엔드가 로그 구현이라 화면 확인 못 함.

## 결제 대기 연장 다시 결제 (계획 질문 1 → A)
- **대여 상세 응답에 `pendingExtension`을 넣었다.** 결제창을 떠나면 extensionId를 다시 얻을 길이 없어 15분 동안 `RENTAL_EXTENSION_IN_PROGRESS`만 받는다. sessionStorage(B)는 다른 탭·기기에서 못 이어 간다. 대여 결제 대기(`paymentExpiresAt`)와 같은 모양이라 화면 규칙도 같게 갈 수 있다.
- **대여자 본인이고 RENTING일 때만 채운다.** 연장 결제는 대여자만 할 수 있고(PAYMENT_RENTER_ONLY), RENTING이 아니면 승인해도 환불된다(open-issues #45 A). 조회는 상태와 상관없이 해 쿼리 수를 일정하게 둔다(상세 쿼리 8 → 9).
- **만료 판단은 시각으로 한다(`expiresAt > now`, 배치 전이어도 숨김).** 엔티티 `isPendingAt`과 같은 기준이다.
- **`extensionCount`는 양측에 준다.** 제공자도 연장 여부를 알아야 하고, 개인 정보가 아니다.
- openapi.json 재생성 때 `PasswordResetConfirmRequest` 예시 토큰을 `example-reset-token000…`(43자)로 바꿨다. 이전 값이 GitGuardian에 실제 토큰처럼 잡혔다.

## 새 종료일 고르기 (질문 2 → A)
- **범위 캘린더 대신 날짜 칩 7개.** 연장 구간은 시작이 종료일 다음 날로 고정이라 범위 선택과 맞지 않고, 최대 7개뿐이다.
- **캘린더에서 처음 AVAILABLE이 아닌 날부터 뒤는 모두 막는다.** 연장 구간은 이어져야 해서다. 서버 maxEndDate가 오면 그 뒤도 막는다. 이어 붙은 두 가능 기간(캘린더상 모두 AVAILABLE)은 서버 400의 maxEndDate로만 걸러진다.
- **캘린더를 못 불러오면 모든 칩을 열어 둔다.** 서버가 최종 검사하고 maxEndDate로 안내하므로 막을 이유가 없다.
- **연장료는 `pricePerDay × 일수`로 미리 보여 준다(확정 값은 서버 `additionalFee`).** rental.md 공식 그대로이고 상세의 `pricePerDay`가 예약 시점 값이다.

## 버튼 표 (질문 3)
- RENTING 대여자: 결제 대기 연장 있음 → [반납 요청(secondary), 연장 결제하기(primary)] + 연장 결제 카운트다운 / 종료일 지남 → [반납 요청] / 그 밖 → [연장하기(secondary, 2회면 비활성), 반납 요청(primary)]. 제공자·OVERDUE는 그대로.
- **카운트다운 notice에 `paymentKind`를 더했다.** 대여 결제와 연장 결제의 만료 문구("예약이 자동 취소" / "연장이 취소")만 다르고 남은 시간 처리는 같다.
- **결제창을 여는 동안 "반납 요청"도 막는다.** 연장 결제 중 반납 요청을 하면 승인 때 환불 경로(#45 A)로 간다.

## 연장 이력 (질문 4)
- `extensionCount`만 "반납일을 n번 연장했어요"로 보인다. 연장별 기간 목록은 응답에 없어 그리지 않는다.

## 결제 복귀
- **결제 종류를 `pendingPayment`에 같이 저장한다(`kind`).** 실패 응답에는 type이 없어서, 연장 실패 문구(연장 취소·연장료 환불·다시 연장 요청)를 고를 근거가 이것뿐이다. 예전 저장값은 RENTAL로 읽는다.
- 성공 문구는 응답 `type`으로 고른다(저장값보다 서버 값이 정확).

## 목
- 결제 대기 연장 점유는 `rentalStore.listOccupied` 한곳에서 대여와 합쳐 캘린더·예약 겹침·연장 겹침이 같이 본다(백엔드 `RentalOccupancyReader`와 같은 구조).
- 시드 7은 같은 물품 105의 다음 예약(2) 때문에 연장할 날이 없다. 시드가 원래 겹쳐 있어 고치지 않고 "연장 불가" 확인용으로 표에 적었다.
