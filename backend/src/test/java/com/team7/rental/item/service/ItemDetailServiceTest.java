package com.team7.rental.item.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

import com.team7.rental.item.dto.ItemDetailResponse;

/** 최근 본 상품 기록 실패가 상세 응답을 막지 않는지 (REQ-035). 커넥션·커밋 실패는 DB로 재현하기 어려워 목으로 확인한다 */
class ItemDetailServiceTest {

	private final ItemQueryService itemQueryService = mock(ItemQueryService.class);
	private final RecentItemWriter recentItemWriter = mock(RecentItemWriter.class);
	private final ItemDetailService service = new ItemDetailService(itemQueryService, recentItemWriter);

	static Stream<RuntimeException> recordFailures() {
		return Stream.of(new DataIntegrityViolationException("fk"),
			new CannotCreateTransactionException("no connection"), new TransactionSystemException("commit failed"));
	}

	@ParameterizedTest
	@MethodSource("recordFailures")
	@DisplayName("기록이 쿼리·트랜잭션 예외로 실패해도 상세를 그대로 돌려준다")
	void recordFailureDoesNotBreakDetail(RuntimeException failure) {
		ItemDetailResponse detail = mock(ItemDetailResponse.class);
		given(detail.isOwner()).willReturn(false);
		given(itemQueryService.getDetail(1L, 2L, "192.0.2.1")).willReturn(detail);
		willThrow(failure).given(recentItemWriter).record(2L, 1L);

		assertThat(service.getDetail(1L, 2L, "192.0.2.1")).isSameAs(detail);
	}
}
