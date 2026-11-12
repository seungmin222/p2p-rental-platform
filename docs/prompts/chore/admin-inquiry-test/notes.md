# chore/admin-inquiry-test 결정 기록 (REQ-063 후속 테스트)

## 결정
- **범위**: `feat/admin-inquiry` notes.md의 "후속 테스트 시나리오" 중 동시성(분쟁 접수 × 지급 배치 `pay()`)을 뺀 전부. 사용자 결정에 따라 미루는 것은 동시성·부하 테스트뿐이다.
- **나눈 방식**
  - `admin/controller/InquiryIntegrationTest`: 검증·권한·에러 코드·목록(MockMvc).
  - `admin/controller/DisputeSettlementHoldIntegrationTest`: 정산 상태. 접수·완료·취소는 실제 API로 하고, 정산 상태는 저장소에서 확인한다. 서비스를 직접 부르면 트랜잭션·락 경계가 운영과 달라진다.
  - `payment/entity/SettlementTest`: `hold`·`release` 전이 단위 테스트(허용·불허). backend/CLAUDE.md의 상태 전이 단위 테스트 필수 항목인데 없었다.
- **정산 생성 전 분쟁은 대여 완료 API(반납 확인 NORMAL)로 재현한다.** `SettlementDisputePort`가 `Propagation.MANDATORY`라 정산 생성 트랜잭션 안의 실제 경로를 지나야 한다.
- **heldTotal은 정산 목록 API(`GET /api/members/me/settlements`) 응답으로 확인한다.** 응답 DTO에 실제로 나가는지가 확인 대상이다.
- **"HELD는 지급 배치가 지급하지 않는다"는 따로 쓰지 않았다.** `SettlementPayoutJobTest`가 이미 다룬다.
- 운영 코드 변경 없음(테스트가 드러낸 버그 없음).

## 남은 일
- 동시성: 분쟁 접수와 지급 배치 `pay()`가 겹쳐도 PAID를 HELD로 덮어쓰지 않고, HELD를 지급하지 않는다. 성능 실험(`docs/experiments/`) 때 다룬다.
