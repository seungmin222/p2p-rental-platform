package com.team7.rental.rental.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.item.dto.ItemRentalTarget;
import com.team7.rental.rental.entity.Rental;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.entity.RentalStatusHistory;
import com.team7.rental.rental.repository.RentalRepository;
import com.team7.rental.rental.repository.RentalStatusHistoryRepository;

/** 결제 플래그·결제 금액에 따른 생성 상태 분기. 플래그만 바꾸려고 스프링 컨텍스트를 띄우지 않는다. */
class RentalReservationTxServiceTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 10, 0);
	private static final LocalDate START = LocalDate.of(2026, 11, 3);
	private static final LocalDate END = LocalDate.of(2026, 11, 5);
	private static final Long RENTER_ID = 3L;
	private static final ItemRentalTarget PAID_ITEM = new ItemRentalTarget(1L, 2L, 10_000, 50_000);
	private static final ItemRentalTarget FREE_ITEM = new ItemRentalTarget(1L, 2L, 0, 0);
	private static final int EXPIRE_MINUTES = 15;

	private final RentalRepository rentalRepository = mock(RentalRepository.class);
	private final RentalStatusHistoryRepository historyRepository = mock(RentalStatusHistoryRepository.class);
	private final RentalOccupancyReader occupancyReader = mock(RentalOccupancyReader.class);
	private final PolicyProperties policyProperties = new PolicyProperties(null, null, null, null,
		new PolicyProperties.Payment(EXPIRE_MINUTES), null, null, null, null, null, null, null, null, null, null, null,
		null);

	@BeforeEach
	void setUp() {
		given(rentalRepository.save(any(Rental.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(occupancyReader.isOccupied(any(), any(), any(), any())).willReturn(false);
	}

	@Test
	@DisplayName("결제를 켜고 결제 금액이 있으면 PENDING_PAYMENT이고 만료 시각은 now + payment.expire-minutes다")
	void pendingPaymentWhenPaymentEnabled() {
		// when
		Rental rental = service(true).create(RENTER_ID, PAID_ITEM, START, END, NOW);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.PENDING_PAYMENT);
		assertThat(rental.getPaymentExpiresAt()).isEqualTo(NOW.plusMinutes(EXPIRE_MINUTES));
	}

	@Test
	@DisplayName("결제를 켜도 결제 금액이 0원(무료 대여)이면 바로 RESERVED다")
	void reservedWhenFreeEvenIfPaymentEnabled() {
		// when
		Rental rental = service(true).create(RENTER_ID, FREE_ITEM, START, END, NOW);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(rental.getPaymentExpiresAt()).isNull();
	}

	@Test
	@DisplayName("결제를 끄면(1순위 단계) 결제 금액이 있어도 바로 RESERVED다")
	void reservedWhenPaymentDisabled() {
		// when
		Rental rental = service(false).create(RENTER_ID, PAID_ITEM, START, END, NOW);

		// then
		assertThat(rental.getStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(rental.getPaymentExpiresAt()).isNull();
	}

	@Test
	@DisplayName("생성 이력을 from=null, to=생성 상태, changedBy=대여자로 같은 호출 안에서 저장한다")
	void savesCreationHistory() {
		// when
		service(false).create(RENTER_ID, PAID_ITEM, START, END, NOW);

		// then
		ArgumentCaptor<RentalStatusHistory> captor = ArgumentCaptor.forClass(RentalStatusHistory.class);
		then(historyRepository).should().save(captor.capture());
		RentalStatusHistory history = captor.getValue();
		assertThat(history.getFromStatus()).isNull();
		assertThat(history.getToStatus()).isEqualTo(RentalStatus.RESERVED);
		assertThat(history.getChangedBy()).isEqualTo(RENTER_ID);
		assertThat(history.getChangedAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("점유 중인 기간과 겹치면 RENTAL_PERIOD_CONFLICT이고 저장하지 않는다")
	void conflict() {
		// given
		given(occupancyReader.isOccupied(PAID_ITEM.itemId(), START, END, NOW)).willReturn(true);

		// when, then
		assertThatThrownBy(() -> service(false).create(RENTER_ID, PAID_ITEM, START, END, NOW))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(ErrorCode.RENTAL_PERIOD_CONFLICT);
		then(rentalRepository).should(never()).save(any());
		then(historyRepository).should(never()).save(any());
	}

	private RentalReservationTxService service(boolean paymentEnabled) {
		return new RentalReservationTxService(rentalRepository, historyRepository, occupancyReader, policyProperties,
			paymentEnabled);
	}
}
