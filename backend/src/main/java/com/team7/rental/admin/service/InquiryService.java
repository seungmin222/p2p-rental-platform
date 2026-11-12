package com.team7.rental.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.admin.dto.InquiryCreateRequest;
import com.team7.rental.admin.dto.InquiryResponse;
import com.team7.rental.admin.entity.Inquiry;
import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.entity.InquiryType;
import com.team7.rental.admin.repository.InquiryRepository;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.payment.service.SettlementTxService;
import com.team7.rental.rental.dto.RentalParticipants;
import com.team7.rental.rental.entity.RentalStatus;
import com.team7.rental.rental.service.RentalParticipantReader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 문의·분쟁 접수 (REQ-063) */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryService {

	private final InquiryRepository inquiryRepository;
	private final RentalParticipantReader rentalParticipantReader;
	private final SettlementTxService settlementTxService;
	private final PolicyProperties policyProperties;

	/**
	 * 문의·분쟁을 접수한다(RECEIVED). 분쟁은 대여 행 락 안에서 접수하고 같은 트랜잭션에서 그 대여의 정산을 보류한다(SCHEDULED → HELD).
	 * 정산이 아직 없으면 정산을 만들 때 보류된다(SettlementDisputePort). 이미 지급(PAID)된 정산은 그대로 두고 문의만 접수한다.
	 */
	@Transactional
	public InquiryResponse create(Long memberId, InquiryCreateRequest request) {
		validateContent(request.content());
		if (request.type() == InquiryType.DISPUTE) {
			return createDispute(memberId, request);
		}
		if (request.rentalId() != null) {
			validateParticipant(rentalParticipantReader.get(request.rentalId()), memberId);
		}
		return save(memberId, request);
	}

	private InquiryResponse createDispute(Long memberId, InquiryCreateRequest request) {
		if (request.rentalId() == null) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("rentalId", "분쟁은 대여를 선택해야 합니다.")));
		}
		RentalParticipants rental = rentalParticipantReader.lock(request.rentalId());
		validateParticipant(rental, memberId);
		if (rental.status() == RentalStatus.PENDING_PAYMENT) {
			throw new BusinessException(ErrorCode.INQUIRY_DISPUTE_NOT_ALLOWED);
		}
		if (inquiryRepository.existsByRentalIdAndMemberIdAndTypeAndStatusNot(rental.rentalId(), memberId,
			InquiryType.DISPUTE, InquiryStatus.RESOLVED)) {
			throw new BusinessException(ErrorCode.INQUIRY_DISPUTE_ALREADY_OPEN);
		}
		InquiryResponse response = save(memberId, request);
		if (settlementTxService.holdForDispute(rental.rentalId())) {
			log.info("분쟁 접수로 정산 보류: rentalId={}, inquiryId={}", rental.rentalId(), response.inquiryId());
		}
		return response;
	}

	private InquiryResponse save(Long memberId, InquiryCreateRequest request) {
		return InquiryResponse.of(inquiryRepository.save(
			Inquiry.receive(memberId, request.rentalId(), request.type(), request.content())));
	}

	private void validateContent(String content) {
		int max = policyProperties.inquiry().contentMax();
		if (content.codePointCount(0, content.length()) > max) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("content", max + "자 이하로 입력해 주세요.")));
		}
	}

	private void validateParticipant(RentalParticipants rental, Long memberId) {
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
	}
}
