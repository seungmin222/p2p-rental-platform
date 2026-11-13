package com.team7.rental.rental.service;

import java.util.Collection;
import java.util.Set;

/**
 * 빌린 내역(REQ-019)·대여 상세(REQ-016)가 읽는 후기 작성 여부 (REQ-034). 구현은 후기 도메인에 있어 rental이 review를 직접 알지 않는다
 * ({@link RentalDepositQueryPort}와 같은 방식).
 */
public interface RentalReviewQueryPort {

	/** @return 이 회원이 이 대여에 후기를 썼으면 true */
	boolean isWritten(Long rentalId, Long writerId);

	/** @return rentalIds 중 이 회원이 후기를 쓴 대여 ID. 빈 입력이면 쿼리를 내지 않는다 */
	Set<Long> findWrittenRentalIds(Long writerId, Collection<Long> rentalIds);
}
