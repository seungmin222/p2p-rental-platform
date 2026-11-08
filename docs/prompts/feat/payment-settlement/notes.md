# feat/payment-settlement 결정 기록 (REQ-039, REQ-033)

## 정산 생성
- **대여 완료·수수료 취소와 같은 트랜잭션에서 `RentalSettlementPort`로 만든다.** backend/CLAUDE.md는 정산 생성을 AFTER_COMMIT 이벤트로 적었지만, 커밋 뒤 리스너가 실패하거나 서버가 죽으면 정산 없는 COMPLETED가 남고 이를 메우는 복구 배치가 따로 필요하다. 같은 트랜잭션이면 둘이 함께 커밋되거나 함께 롤백돼 복구 배치가 필요 없다. `RentalRefundPort`와 같은 포트 방식이라 rental이 payment를 직접 알지 않는다. backend/CLAUDE.md 문장을 고쳤다.

## 033 결제·보증금 내역
- type 의미는 open-issues #44의 임시 해석으로 구현한다(확정 전).
