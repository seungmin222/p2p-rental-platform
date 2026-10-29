package com.team7.rental.rental.batch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.service.RentalInspectionTxService;

/** 자동 상태 확인 배치의 건별 처리. 한 건이 실패해도 나머지를 계속한다 */
class ReturnAutoConfirmJobTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

	private final RentalInspectionTxService txService = mock(RentalInspectionTxService.class);
	private final ReturnAutoConfirmJob job = new ReturnAutoConfirmJob(txService, Clock.system(ZoneId.of("Asia/Seoul")));

	@Test
	@DisplayName("낙관적 락 충돌·예외가 난 건은 건너뛰고 나머지를 처리하며, 대상이 아니게 된 건은 세지 않는다")
	void continueOnFailure() {
		// given
		given(txService.findAutoConfirmTargetIds(NOW)).willReturn(List.of(1L, 2L, 3L, 4L));
		given(txService.autoConfirm(1L, NOW)).willThrow(new ObjectOptimisticLockingFailureException(Rental.class, 1L));
		given(txService.autoConfirm(2L, NOW)).willThrow(new IllegalStateException("반납 기록이 없습니다: 2"));
		given(txService.autoConfirm(3L, NOW)).willReturn(true);
		given(txService.autoConfirm(4L, NOW)).willReturn(false);

		// when
		int confirmed = job.confirmDue(NOW);

		// then
		assertThat(confirmed).isEqualTo(1);
		then(txService).should().autoConfirm(4L, NOW);
	}
}
