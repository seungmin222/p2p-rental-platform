package com.team7.rental.payment.entity;

/**
 * 정산 금액 (REQ-039, docs/domains/payment.md). 모두 원 미만 절사. 손상 차감액은 수리비 보전이라 수수료를 떼지 않는다.
 *
 * @param gross 수수료 대상 금액: 대여료 + 연장료 + 연체료, 또는 취소 수수료
 * @param fee floor(gross × feeRatePercent / 100)
 * @param deduction 손상 차감액. 취소면 0
 * @param payout gross − fee + deduction
 */
public record SettlementAmount(int gross, int fee, int deduction, int payout) {

	/**
	 * 거래 완료.
	 *
	 * @param extensionFee 승인된 연장 결제 합계(REQ-038 전에는 0)
	 * @param overdueFee 보증금에서 받은 연체료(rentals.overdue_fee, 보증금 한도)
	 * @param deduction 확정된 손상 차감액(payments.deduction_amount, 연체료 제외)
	 */
	public static SettlementAmount ofCompletion(int rentalFee, long extensionFee, int overdueFee, int deduction,
		int feeRatePercent) {
		return of(rentalFee + extensionFee + overdueFee, deduction, feeRatePercent);
	}

	/** 수수료가 있는 취소. gross = 취소 수수료 */
	public static SettlementAmount ofCancelFee(int cancelFee, int feeRatePercent) {
		return of(cancelFee, 0, feeRatePercent);
	}

	private static SettlementAmount of(long gross, int deduction, int feeRatePercent) {
		if (gross < 0 || deduction < 0) {
			throw new IllegalArgumentException("정산 금액은 0원 이상이어야 합니다: gross=" + gross + ", deduction=" + deduction);
		}
		long fee = gross * feeRatePercent / 100;
		return new SettlementAmount(Math.toIntExact(gross), Math.toIntExact(fee), deduction,
			Math.toIntExact(gross - fee + deduction));
	}
}
