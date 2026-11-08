package com.team7.rental.payment.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.payment.dto.PaymentHistoryRequest;
import com.team7.rental.payment.dto.PaymentHistoryResponse;
import com.team7.rental.payment.repository.PaymentHistoryRow;
import com.team7.rental.payment.repository.PaymentRepository;
import com.team7.rental.rental.dto.RentalSummary;
import com.team7.rental.rental.service.RentalSummaryReader;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentHistoryQueryService {

	private final PaymentRepository paymentRepository;
	private final RentalSummaryReader rentalSummaryReader;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 결제·보증금 내역 (REQ-033). 본인이 대여자인 결제만. 기간은 createdAt 날짜 기준 [from, to] (Asia/Seoul).
	 *
	 * @throws BusinessException COMMON_INVALID_INPUT: from &gt; to (기본값을 채운 뒤 기준)
	 */
	public PageResponse<PaymentHistoryResponse> getMyPayments(Long memberId, PaymentHistoryRequest request) {
		LocalDate to = request.to() == null ? LocalDate.now(clock) : request.to();
		LocalDate from = request.from() == null
			? to.minusMonths(policyProperties.paymentHistory().defaultRangeMonths())
			: request.from();
		if (from.isAfter(to)) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("to", "종료일은 시작일보다 빠를 수 없습니다.")));
		}
		Page<PaymentHistoryRow> rows = paymentRepository.findRenterPayments(memberId, request.type(),
			from.atStartOfDay(), to.plusDays(1).atStartOfDay(),
			PageRequest.of(request.page(), policyProperties.paymentHistory().page()));
		Set<Long> rentalIds = rows.stream().map(PaymentHistoryRow::rentalId).collect(Collectors.toSet());
		Map<Long, RentalSummary> rentals = rentalSummaryReader.getSummaries(rentalIds);
		return PageResponse.of(rows, row -> PaymentHistoryResponse.of(row, rentalOf(rentals, row)));
	}

	private RentalSummary rentalOf(Map<Long, RentalSummary> rentals, PaymentHistoryRow row) {
		RentalSummary rental = rentals.get(row.rentalId());
		if (rental == null) {
			throw new IllegalStateException("결제가 참조하는 대여가 없습니다: paymentId=" + row.paymentId());
		}
		return rental;
	}
}
