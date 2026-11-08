package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team7.rental.rental.event.RentalNotificationEvent;
import com.team7.rental.rental.service.RentalNotificationPort.Type;

class RentalNotificationEventListenerTest {

	@Test
	@DisplayName("포트가 예외를 던져도 리스너는 밖으로 던지지 않는다")
	void swallowsPortFailure() {
		// given
		RentalNotificationPort port = mock(RentalNotificationPort.class);
		willThrow(new IllegalStateException("boom")).given(port)
			.send(anyLong(), any(), any(), anyLong(), anyString());
		var listener = new RentalNotificationEventListener(port);

		// when & then
		assertThatCode(() -> listener.onNotification(
			new RentalNotificationEvent(1L, Type.RESERVATION_CONFIRMED, 10L, "RESERVATION_CONFIRMED:10")))
			.doesNotThrowAnyException();
	}
}
