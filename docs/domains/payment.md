# 결제·보증금·차감·정산 (payment)

담당: 이승민(A) · 화면: SCR-07, 08, 12 · 엔티티: payments, payment_transactions, deduction_proposals, damage_policies, settlements
**모든 단계 2순위.** 1순위에서는 이 도메인 코드를 만들지 않는다 (`payment-enabled=false`). 수치는 `policies.md`가 기준이다.

## 원칙
1. **금액은 서버가 계산**한다. 클라이언트 금액은 검증용으로만 받는다.
2. **모든 PG 호출에 멱등성 키**를 쓴다(토스 `Idempotency-Key` 헤더). 재시도는 같은 키로.
3. **결제 상태와 대여 상태를 분리**한다. PG 호출(외부 I/O)을 DB 트랜잭션 안에 넣지 않는다:
   `[TX1] 시도 기록 → PG 호출 → [TX2] 결과 반영(결제 상태 + 대여 상태)`.
4. 모든 시도·결과를 `payment_transactions`에 남긴다. 실패도 남긴다.
5. **조정(reconciliation) 배치**가 PG 조회 결과와 내부 상태를 대조해 불일치를 보정한다.
6. 결제 수단: 카드만. 토스페이먼츠 **결제창(API 개별 연동)**, 테스트 키로 **실제 API 호출**(모킹으로 대체하지 않는다, 단위 테스트 제외). 결제위젯이 아니므로 API 개별 연동용 클라이언트 키·시크릿 키를 쓴다.
7. **0원은 PG를 부르지 않는다.** 결제 금액 0원 → 결제 레코드 없이 진행. 반환액 0원 → 부분 취소 없이 상태만 반영.
8. **취소·환불 PG 호출은 외부 API로 열지 않는다.** 대여 취소, 보증금 처리, 관리자 확정 같은 업무 API 안에서 서버가 내부적으로만 호출한다.

## REQ-024 대여료·보증금 결제 [2 필수]

```
대여자 → POST /api/payments {rentalId}
  서버: rental PENDING_PAYMENT & 만료 전 확인 → amount = rentalFee + deposit (> 0 보장)
        orderId(UUID), idempotencyKey 생성 → payments(status=READY) 저장
  ← {orderId, amount, orderName, customerKey}
프론트 → 토스 결제창(orderId, amount) → 성공 리다이렉트(paymentKey, orderId, amount)
프론트 → POST /api/payments/confirm {paymentKey, orderId, amount}
  서버: amount == 저장된 amount ?
     ├ 불일치 → payments FAILED, rental CANCELED(AMOUNT_MISMATCH), 400 PAYMENT_AMOUNT_MISMATCH
     └ 일치 → 토스 승인 API(Idempotency-Key)
          ├ 성공 → [TX] payment APPROVED, depositStatus=FROZEN(보증금 0원이면 NONE), rental RESERVED, 알림
          ├ 타임아웃/응답 유실 → 같은 키로 재요청 또는 결제 조회 API → 결과대로 반영 (중복 승인 없음)
          └ PG 오류 → rental PENDING_PAYMENT 유지 → 15분 만료 배치가 취소
```
- 결제 금액 0원(무료 대여)은 예약 시 바로 RESERVED라 이 API를 타지 않는다
- 이미 APPROVED인 orderId로 confirm 재호출 → 기존 결과 반환(멱등)
- 승인 성공 직후 rental이 이미 만료 취소됐다면(경합) → 즉시 전액 취소 + 사용자 안내 (조정 배치에서도 탐지)

## 취소 시 환불 [2] (REQ-015 연계)
- 환불액은 `policies.md` §2 취소 수수료 표대로 서버가 계산한다: `refund = (rentalFee − cancelFee) + deposit`
- refund = 결제 금액 → 전액 취소(payment CANCELED). 그 외 → 부분 취소(payment PARTIAL_CANCELED)
- 보증금은 항상 전액 반환 → depositStatus=REFUNDED (보증금 0원이면 NONE 유지)
- 멱등성 키 = `cancel-refund:{rentalId}`. 실패 시 REFUND_FAILED + 재시도 배치
- cancelFee > 0 → 정산 생성(REQ-039)

## REQ-025 보증금 반환 [2 필수]
- 트리거: 상태 확인 NORMAL 또는 3일 자동 처리
- 반환액 = deposit − overdueFee (`rental.md` REQ-023)
  - deposit = 0 → depositStatus NONE 그대로, PG 호출 없음
  - 반환액 > 0 → 토스 부분 취소(cancelAmount = 반환액, 멱등성 키 = `deposit-refund:{rentalId}`) → depositStatus=REFUNDED, refundedAmount += 반환액. 누적 취소가 결제 금액에 닿으면(대여료 0원 + 보증금 전액 반환) payment CANCELED, 아니면 PARTIAL_CANCELED (open-issues #32)
  - 반환액 = 0 (연체료가 보증금 전액) → PG 호출 없이 depositStatus=FORFEITED
- 처리 완료 → rental COMPLETED, 정산 생성
- PG 실패 → depositStatus=REFUND_FAILED, rental은 **RETURNED 유지**. 재시도 배치(10분, 최대 5회)가 성공시키면 COMPLETED. 5회 실패 → `needs_admin_check=true`

## REQ-049 보증금 보류·차감 [2 필수]
`PATCH /api/rentals/{id}/deposit { action: PROPOSE | ACCEPT | REJECT, ... }`

**데이터 역할**: `deduction_proposals`는 협의 이력(제안·거절·수락 전부), `payments`의 `damage_grade`·`deduction_amount`·`deduction_reason`은 **확정된 결과**만 담는다(손상 발생률 집계용). 확정 시점에 한 번만 복사하고 이후 수정하지 않는다.

1. 상태 확인 ABNORMAL 또는 미반납 신고 → depositStatus=HELD, `held_at=now`, 정산 보류
2. **제안(PROPOSE, 제공자)**: grade(B/C/D), amount, reason(500자), evidence 사진 필수. C는 수리 견적서·영수증 필수, B 소모품 실비(TOOL)는 구매 내역 필수. 미반납 신고 건은 D 등급만
   - 상한 계산(`GET /api/rentals/{id}/deposit/policy?grade=`): `policies.md` §4 계산 규칙(연체료를 뺀 **남은 보증금** 기준)
   - 검증: amount > 남은 보증금 → 400 `DEDUCTION_EXCEEDS_DEPOSIT`, amount > 상한 → 400 `DEDUCTION_EXCEEDS_CAP` + 상한 금액, C인데 증빙 없음 → 400 `DEDUCTION_EVIDENCE_REQUIRED`
   - 진행 중인 PROPOSED 제안이 있으면 409. deduction_proposals 저장, 대여자 알림 `DEDUCTION_PROPOSED`, 채팅방에 SYSTEM 메시지
3. **수락(ACCEPT, 대여자)** 또는 **72시간 무응답 자동 수락**(배치 1시간, 제안 status=AUTO_ACCEPTED): 확정 처리로 이동
4. **거절(REJECT, 대여자)**: 제안 REJECTED, 제공자 알림 `DEDUCTION_REJECTED`. 제공자는 다시 제안 가능
5. **보류 7일 초과 → 관리자 확정** (2순위로 앞당김): `PATCH /api/admin/rentals/{id}/deposit { grade, amount, reason }` (ADMIN 전용). 상한 검증은 같고, 제안 status=ADMIN_DECIDED로 기록. 관리자 화면 전체(REQ-057)는 3순위라, 2순위에서는 이 API와 "보류 7일 초과 목록" API(`GET /api/admin/deposits/held`)만 만든다
6. **확정 처리 (3·5 공통)**: 반환액 = deposit − overdueFee − amount
   - 반환액 > 0 → 부분 취소(멱등성 키 `deposit-refund:{rentalId}`) → depositStatus=REFUNDED
   - 반환액 = 0 → PG 호출 없이 depositStatus=FORFEITED
   - payments에 확정 결과 복사 → rental COMPLETED → 정산 생성. 제공자 알림 `DEDUCTION_ACCEPTED`
- 당사자 아님 403. 부분 취소 실패 → REFUND_FAILED + 재시도 배치
- A 등급 판정 손상은 차감 불가(UI에서 등급 선택지에 A 없음)

## REQ-039 제공자 대금 정산 [2]
- 생성 시점: rental COMPLETED, 또는 cancelFee > 0인 취소
- 금액 (모두 원 미만 절사):
  - 거래 완료: `gross = rentalFee + 연장료 합계 + overdueFee`, `feeAmount = floor(gross × 0.10)`, `payoutAmount = gross − feeAmount + 손상 차감액`
  - 수수료 있는 취소: `gross = cancelFee`, `feeAmount = floor(gross × 0.10)`, `payoutAmount = gross − feeAmount`
  - 손상 차감액은 수리비 보전이라 수수료를 떼지 않는다
- scheduledDate = 완료(취소)일 + 영업일 2일 (주말만 제외)
- 배치(매일): `SCHEDULED AND scheduledDate <= 오늘` → PAID (실제 송금 없음)
- 분쟁 진행 중(문의 REQ-063 접수) → HELD
- `GET /api/members/me/settlements`: 지급 예정·완료 금액, 수수료 내역
- 실제 계좌 송금·출금 신청은 범위 외(보고서 명시)

## REQ-033 결제·보증금 내역 [2]
`GET /api/members/me/payments?type=ALL|PAYMENT|REFUND|DEPOSIT&from=&to=` (기본 최근 3개월)
- 항목: 일시, 물품명, 대여 기간, 구분, 금액(대여료·보증금·연장료 분리), 결제 상태, 보증금 상태(없음/동결/보류/반환/전액 차감/반환 실패), 연체료, 취소 수수료, 차감 금액·사유
- 클릭 → 대여 상세. 정산 내역은 같은 화면 별도 탭

## 배치 목록 (이 도메인)
주기는 `policies.md` "배치 주기" 표가 기준이다.

| 배치 | 내용 |
| --- | --- |
| 결제 만료 | PENDING_PAYMENT 15분 초과 → CANCELED (rental.md) |
| 연장 결제 만료 | extension PENDING_PAYMENT 15분 초과 → CANCELED |
| 환불 재시도 | REFUND_FAILED → 같은 멱등성 키로 재시도, `refund_retry_count` 증가, 5회 초과 시 `needs_admin_check` |
| 자동 상태 확인 | RETURNED + 3일 → NORMAL 처리 → 보증금 반환 |
| 차감 제안 자동 수락 | PROPOSED + 72시간 → AUTO_ACCEPTED → 확정 처리 |
| 보류 7일 초과 표시 | HELD + 7일 → 관리자 확정 대상 목록에 노출 |
| 정산 지급 | SCHEDULED → PAID |
| 조정 | 최근 결제의 PG 조회 결과 ↔ 내부 상태 대조, 불일치 보정·로그 |

모든 배치는 ShedLock으로 단일 실행하고 멱등하게 만든다.

## 장애 주입 테스트 (도전 과제 ②)
- 승인 응답 유실 → 재시도 시 중복 승인 0
- 취소 실패 → REFUND_FAILED → 재시도 성공 → 중복 환불 0
- confirm 중복 호출(더블 클릭) → 1회만 승인
- 승인 성공 + 만료 배치 경합
- 0원 경로: 무료 대여, 보증금 0원, 반환액 0원(FORFEITED)에서 PG 호출 0회
- 결과: 금액 불일치 0, PG↔내부 상태 불일치 0을 표로 기록 (`docs/experiments/payment.md`)
- PG 클라이언트는 인터페이스(`PaymentGateway`)로 감싸 테스트에서 지연·실패 주입용 구현체로 교체
