package com.team7.rental.payment.service;

/**
 * 정산이 묻는 대여의 분쟁 여부 (REQ-063). 구현은 문의 도메인(admin)에 있어 payment가 문의를 직접 알지 않는다.
 * 분쟁 접수와 정산 생성은 둘 다 대여 행 락 안에서 일어나므로, 접수가 먼저 커밋되면 정산을 만들 때 이 포트가 반드시 본다.
 */
public interface SettlementDisputePort {

	/** 이 대여에 끝나지 않은(RESOLVED가 아닌) 분쟁이 있는지 */
	boolean hasOpenDispute(Long rentalId);
}
