package com.team7.rental.rental.batch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.rental.service.RentalInspectionService;

/** 자동 상태 확인 배치의 건별 처리. 한 건이 실패해도 나머지를 계속한다 */
class ReturnAutoConfirmJobTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 10, 0);

	private final RentalInspectionService service = mock(RentalInspectionService.class);
	private final ReturnAutoConfirmJob job = new ReturnAutoConfirmJob(service, Clock.system(ZoneId.of("Asia/Seoul")));

	@Test
	@DisplayName("예외가 난 건은 건너뛰고 나머지를 처리하며, 대상이 아니게 된 건은 세지 않는다")
	void continueOnFailure() {
		// given
		given(service.findAutoConfirmTargetIds(NOW)).willReturn(List.of(1L, 2L, 3L));
		given(service.autoConfirm(1L, NOW)).willThrow(new IllegalStateException("반납 기록이 없습니다: 1"));
		given(service.autoConfirm(2L, NOW)).willReturn(true);
		given(service.autoConfirm(3L, NOW)).willReturn(false);

		// when
		int confirmed = job.confirmDue(NOW);

		// then
		assertThat(confirmed).isEqualTo(1);
		then(service).should().autoConfirm(3L, NOW);
	}
}
