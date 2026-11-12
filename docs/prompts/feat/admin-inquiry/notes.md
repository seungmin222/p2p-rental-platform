# feat/admin-inquiry 결정 기록 (REQ-063)

## 정해진 값 (2026-11-12 사용자 결정, open-issues #51·#9)
- Enum: `InquiryType` = `GENERAL`·`DISPUTE`, `InquiryStatus` = `RECEIVED`·`IN_PROGRESS`·`RESOLVED`. 이번 브랜치는 RECEIVED만 만든다.
- 정책 값: `inquiry.content-max` 1,000자(공백만 불가), `inquiry.page` 20 고정.
- 알림은 보내지 않는다. 명세에 없고 NotificationType에도 값이 없다. 관리자 처리 결과 알림은 057에서 정한다.

## 분쟁 접수
- **분쟁은 rentalId 필수, 대여자·제공자만, PENDING_PAYMENT는 409.** 결제 전에는 다툴 돈이 없다. 그 밖의 상태(CANCELED 포함)는 허용한다. 수수료 취소 정산도 분쟁 대상이기 때문이다.
- **중복은 같은 회원·대여 기준으로 막는다.** 같은 대여라도 상대방은 따로 접수할 수 있다. 양측 모두 자기 주장을 남길 수 있어야 해서다. 정산이 이미 HELD면 그대로 둔다.
- **일반 문의에 rentalId를 넣으면 당사자 확인만 한다(락 없음).** 정산과 상관이 없어서다.

## 정산 보류 (B안, open-issues #9 변경)
- **정산이 생기기 전에 접수한 분쟁도 보류한다.** 정산을 만들 때 `SettlementDisputePort.hasOpenDispute`가 true면 처음부터 HELD로 만든다. #9의 원래 결정(정산 생성 후 접수만)대로면 대여 중·반납 대기에 접수한 분쟁의 대금이 완료 직후 지급된다.
- **포트는 payment에 두고 admin(`InquiryDisputeReader`)이 구현한다.** `RentalSettlementPort`와 같은 방식이다. admin → payment(`SettlementTxService.holdForDispute`) 호출과 payment → 포트가 있지만, 빈 사이에 순환은 없다(포트 구현은 InquiryRepository만 쓴다).
- **동시성**
  - 분쟁 접수는 대여 행 락(`RentalParticipantReader.lock`, FOR UPDATE) 안에서 한다. 대여 완료(정산 생성)도 대여 행 락 안이라 둘이 한 줄로 선다. 접수가 먼저 커밋되면, 완료 트랜잭션은 락을 얻은 뒤 분쟁을 본다.
  - 정산 생성 쪽의 분쟁 조회는 잠금 읽기(FOR SHARE)로 한다. 완료 트랜잭션의 REPEATABLE READ 스냅샷이 락보다 먼저 잡혔으면, 일반 읽기로는 방금 커밋된 분쟁이 안 보일 수 있어서다.
  - 접수 쪽 보류는 정산 행 FOR UPDATE를 잡고 다시 본다. 지급 배치의 `pay()`도 같은 행 락을 잡으므로 PAID를 HELD로 덮어쓰지 않는다. 정산 id는 projection(`findIdByRentalId`)으로 찾는다. 엔티티를 먼저 일반 읽기로 올리면 락 쿼리가 영속성 컨텍스트의 옛 상태를 돌려줘서다.
  - 정산 id 조회도 잠금 읽기(FOR SHARE)로 바꿨다(리뷰 반영). 일반 읽기는 "대여 행 락 → 정산 생성" 순서 덕에 맞았을 뿐이라, 스냅샷 순서에 기대지 않게 했다. 정산이 없으면 FOR SHARE도 rental_id 인덱스에 갭 락(S)을 건다. 정산 생성(INSERT)은 대여 행 락으로 이미 한 줄로 서 있어 실제 대기는 생기지 않는다.
- **PAID는 그대로 둔다.** 이미 지급된 대금은 회수 흐름이 없다(범위 외). 문의만 접수한다.
- **종료(HELD → SCHEDULED)는 엔티티 `Settlement.release()`까지만 만들었다.** 부르는 곳은 057 관리자 처리다. 그 대여의 분쟁이 모두 RESOLVED일 때만 부른다. 지급 예정일은 그대로 두어, 지났으면 다음 지급 배치가 지급한다.
- **정산 목록 합계에 `heldTotal`을 추가했다**(payment-settlement 요약의 남은 일). 보류 금액이 어디에도 보이지 않으면 제공자가 혼란스럽다.
- **보증금 HELD와는 서로 영향을 주지 않는다.** 보증금 HELD는 RETURNED 동안 생기고, 정산은 완료 뒤에 생긴다. 분쟁은 depositStatus를 바꾸지 않는다.

## 구조
- 패키지는 `admin/`(backend/CLAUDE.md 구조). 대여 당사자 확인은 `rental/service/RentalParticipantReader`로 한다. 다른 도메인 Repository를 직접 부르지 않기 위해서다.
- 내용 글자 수는 서비스에서 code point로 센다(`CancelReasonValidator`와 같은 방식). 초과하면 400 `COMMON_INVALID_INPUT`(details content).
- 테스트 코드는 사용자 지시("테스트는 나중에")로 이 브랜치에서 쓰지 않았다. 기존 테스트만 돌렸다.

## 남은 일: 후속 테스트 시나리오
- 분쟁 접수 → 대여 완료: 정산이 처음부터 HELD로 생성된다.
- 대여 완료 → 분쟁 접수: 정산 SCHEDULED → HELD.
- 이미 PAID면 PAID 유지, 이미 HELD면 HELD 유지(두 번째 접수).
- 수수료 취소(CANCELED)로 생긴 정산도 분쟁 접수 시 HELD.
- PENDING_PAYMENT 대여 분쟁 409, 같은 회원·대여 중복 409, 상대방 당사자 접수는 허용.
- 비당사자 403, DISPUTE에 rentalId 누락 400.
- 내용 1,001자 400(1,000자는 통과, 공백만 400).
- 정산 목록 합계 `heldTotal`이 HELD 지급액 합과 같다.
- 동시성: 분쟁 접수와 지급 배치 `pay()`가 겹쳐도 PAID를 HELD로 덮어쓰지 않고, HELD를 지급하지 않는다.
