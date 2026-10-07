# 결제·보증금·차감·정산 (payment)

담당: 이승민(A) · 화면: SCR-07, 08, 12 · 엔티티: payments, payment_transactions, deduction_proposals, damage_policies, settlements
**모든 단계 2순위.** 1순위에서는 이 도메인 코드를 만들지 않는다 (`payment-enabled=false`).

## 원칙
1. **금액은 서버가 계산**한다. 클라이언트 금액은 검증용으로만 받는다.
2. **모든 PG 호출에 멱등성 키**를 쓴다(토스 `Idempotency-Key` 헤더). 재시도는 같은 키로.
3. **결제 상태와 대여 상태를 분리**한다. PG 호출(외부 I/O)을 DB 트랜잭션 안에 넣지 않는다:
   `[TX1] 시도 기록 → PG 호출 → [TX2] 결과 반영(결제 상태 + 대여 상태)`.
4. 모든 시도·결과를 `payment_transactions`에 남긴다. 실패도 남긴다.
5. **조정(reconciliation) 배치**가 PG 조회 결과와 내부 상태를 대조해 불일치를 보정한다.
6. 결제 수단: 카드만. 토스페이먼츠 테스트 키로 **실제 API 호출**(모킹으로 대체하지 않는다, 단위 테스트 제외).

## REQ-024 대여료·보증금 결제 [2 필수]

```
대여자 → POST /api/payments {rentalId}
  서버: rental PENDING_PAYMENT & 만료 전 확인 → amount = rentalFee + deposit
        orderId(UUID), idempotencyKey 생성 → payments(status=READY) 저장
  ← {orderId, amount, orderName, customerKey}
프론트 → 토스 결제창(orderId, amount) → 성공 리다이렉트(paymentKey, orderId, amount)
프론트 → POST /api/payments/confirm {paymentKey, orderId, amount}
  서버: amount == 저장된 amount ?
     ├ 불일치 → payments FAILED, rental CANCELED, 400 PAYMENT_AMOUNT_MISMATCH
     └ 일치 → 토스 승인 API(Idempotency-Key)
          ├ 성공 → [TX] payment APPROVED, depositStatus=FROZEN, rental RESERVED, 알림
          ├ 타임아웃/응답 유실 → 같은 키로 재요청 또는 결제 조회 API → 결과대로 반영 (중복 승인 없음)
          └ PG 오류 → rental PENDING_PAYMENT 유지 → 15분 만료 배치가 취소
```
- 이미 APPROVED인 orderId로 confirm 재호출 → 기존 결과 반환(멱등)
- 승인 성공 직후 rental이 이미 만료 취소됐다면(경합) → 즉시 전액 취소 + 사용자 안내 (조정 배치에서도 탐지)

## REQ-025 보증금 반환 [2 필수]
- 트리거: 상태 확인 NORMAL 또는 3일 자동 처리
- 토스 부분 취소(cancelAmount = deposit, 멱등성 키 = `deposit-refund:{rentalId}`)
- 성공 → depositStatus=REFUNDED, refundedAmount += deposit, payment PARTIAL_CANCELED, rental COMPLETED, 정산 생성
- 실패 → depositStatus=REFUND_FAILED, rental은 **RETURNED 유지**(명세상 거래 완료는 보증금 처리 후). 재시도 배치가 성공시키면 COMPLETED. 반복 실패 시 관리자 확인 대상 플래그 (횟수·처리 방침 → `open-issues.md` #5)
- 예약 취소(REQ-015): 대여료+보증금 **전액** 취소 → payment CANCELED, depositStatus=REFUNDED

## REQ-049 보증금 보류·차감 [2 필수]
`PATCH /api/rentals/{id}/deposit { action: PROPOSE | ACCEPT | REJECT, ... }`

1. 제공자가 상태 확인에서 ABNORMAL → depositStatus=HELD, 정산 보류, 보류 시작 시각 기록
2. **제안(PROPOSE, 제공자)**: grade(B/C/D), amount, reason, evidence 사진 필수. C는 수리 견적서·영수증 필수, B 소모품 실비(TOOL)는 구매 내역 필수
   - 상한 계산(`GET /api/rentals/{id}/deposit/policy?grade=`): `policies.md` §4 계산 규칙
   - 검증: amount > deposit → 400 `DEDUCTION_EXCEEDS_DEPOSIT`, amount > 상한 → 400 `DEDUCTION_EXCEEDS_CAP` + 상한 금액, C인데 증빙 없음 → 400 `DEDUCTION_EVIDENCE_REQUIRED`
   - deduction_proposals 저장, 대여자 알림 `DEDUCTION_PROPOSED`, 채팅방에 SYSTEM 메시지(제안 내용)
3. **수락(ACCEPT, 대여자)**: 부분 취소(deposit − amount, 멱등성 키 = `deposit-refund:{rentalId}`) → depositStatus=REFUNDED, payments에 damageGrade·deductionAmount·reason 기록 → rental COMPLETED → 정산(대여료 − 수수료 + 차감액)
   - deposit − amount = 0 이면 PG 호출 없이 처리
4. **거절(REJECT, 대여자)**: 제안 REJECTED. 제공자는 다시 제안 가능
5. **7일 미합의**: 관리자 조정 대상으로 표시(REQ-057). 관리자 기능 없으면 보류 유지
- 당사자 아님 403. 부분 취소 실패 → REFUND_FAILED + 재시도 배치
- A 등급 판정 손상은 차감 불가(UI에서 등급 선택지에 A 없음)

## REQ-039 제공자 대금 정산 [2]
- rental COMPLETED 시 settlements 생성(취소·환불 건 제외):
  - feeAmount = floor(rentalFee_total × 0.10), rentalFee_total = 최초 대여료 + 연장료
  - payoutAmount = rentalFee_total − feeAmount + deductionAmount
  - scheduledDate = 완료일 + 영업일 2일 (주말 제외, 공휴일 처리는 단순화 가능 — 결정 시 기록)
- 배치(매일): `SCHEDULED AND scheduledDate <= 오늘` → PAID (실제 송금 없음)
- 분쟁 진행 중(문의 REQ-063 접수 등) → HELD
- `GET /api/members/me/settlements`: 지급 예정·완료 금액, 수수료 내역
- 실제 계좌 송금·출금 신청은 범위 외(보고서 명시)

## REQ-033 결제·보증금 내역 [2]
`GET /api/members/me/payments?type=ALL|PAYMENT|REFUND|DEPOSIT&from=&to=` (기본 최근 3개월)
- 항목: 일시, 물품명, 대여 기간, 구분, 금액(대여료·보증금·연장료 분리), 결제 상태, 보증금 상태(동결/반환/보류/반환 실패), 차감 금액·사유
- 클릭 → 대여 상세. 정산 내역은 같은 화면 별도 탭

## 배치 목록 (이 도메인)
| 배치 | 주기 | 내용 |
| --- | --- | --- |
| 결제 만료 | 1분 | PENDING_PAYMENT 15분 초과 → CANCELED (rental.md) |
| 환불 재시도 | 10분 | REFUND_FAILED → 같은 멱등성 키로 재시도, N회 실패 시 관리자 플래그 |
| 자동 상태 확인 | 1시간 | RETURNED + 3일 → NORMAL 처리 → 보증금 반환 |
| 보류 만료 표시 | 1일 | HELD 7일 초과 → 관리자 조정 대상 |
| 정산 지급 | 1일 | SCHEDULED → PAID |
| 조정 | 1일(또는 1시간) | 최근 결제의 PG 조회 결과 ↔ 내부 상태 대조, 불일치 보정·로그 |

모든 배치는 ShedLock으로 단일 실행.

## 장애 주입 테스트 (도전 과제 ②)
- 승인 응답 유실 → 재시도 시 중복 승인 0
- 취소 실패 → REFUND_FAILED → 재시도 성공 → 중복 환불 0
- confirm 중복 호출(더블 클릭) → 1회만 승인
- 승인 성공 + 만료 배치 경합
- 결과: 금액 불일치 0, PG↔내부 상태 불일치 0을 표로 기록 (`docs/experiments/payment.md`)
- PG 클라이언트는 인터페이스(`PaymentGateway`)로 감싸 테스트에서 지연·실패 주입용 구현체로 교체
