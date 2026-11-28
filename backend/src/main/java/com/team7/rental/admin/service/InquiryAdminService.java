package com.team7.rental.admin.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.admin.dto.AdminInquiryProcessRequest;
import com.team7.rental.admin.dto.AdminInquiryResponse;
import com.team7.rental.admin.entity.Inquiry;
import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.repository.InquiryRef;
import com.team7.rental.admin.repository.InquiryRepository;
import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.payment.service.SettlementTxService;
import com.team7.rental.rental.service.RentalParticipantReader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 관리자 문의 처리·분쟁 종료 (REQ-057) */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InquiryAdminService {

	private final InquiryRepository inquiryRepository;
	private final RentalParticipantReader rentalParticipantReader;
	private final SettlementTxService settlementTxService;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 문의 처리 상태를 바꾸고 답변을 남긴다. 분쟁을 RESOLVED로 바꾸면, 그 대여에 남은 열린 분쟁이 없을 때 정산 보류를 푼다(HELD → SCHEDULED).
	 * 분쟁은 접수와 같은 순서(대여 행 락 → 문의)로 락을 잡아, 처리 도중 접수된 상대방 분쟁·대여 완료(정산 생성)와 한 줄로 선다.
	 */
	@Transactional
	public AdminInquiryResponse process(Long adminId, Long inquiryId, AdminInquiryProcessRequest request) {
		String answer = request.answer();
		validateAnswer(answer);
		InquiryRef ref = inquiryRepository.findRefById(inquiryId)
			.orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
		if (ref.isDispute()) {
			rentalParticipantReader.lock(ref.rentalId());
		}
		Inquiry inquiry = inquiryRepository.findByIdForUpdate(inquiryId)
			.orElseThrow(() -> new BusinessException(ErrorCode.INQUIRY_NOT_FOUND));
		if (!inquiry.canTransitTo(request.status())) {
			throw new BusinessException(ErrorCode.INQUIRY_INVALID_TRANSITION);
		}
		if (request.status() == InquiryStatus.RESOLVED && answer == null && inquiry.getAnswer() == null) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("answer", "처리 완료에는 답변이 필요합니다.")));
		}
		inquiry.process(request.status(), answer, LocalDateTime.now(clock));
		// 응답의 updatedAt을 채우고, 아래 남은 분쟁 조회가 바뀐 상태를 보게 먼저 내보낸다
		inquiryRepository.flush();
		log.info("문의 처리: inquiryId={}, status={}, adminId={}", inquiryId, inquiry.getStatus(), adminId);
		if (inquiry.isDispute() && inquiry.getStatus() == InquiryStatus.RESOLVED) {
			releaseIfNoOpenDispute(inquiry.getRentalId(), inquiryId);
		}
		return AdminInquiryResponse.of(inquiry);
	}

	/** 잠금 읽기로 남은 분쟁을 본다. 대여 행 락 안이라 그사이 새 분쟁이 접수되지 않는다 */
	private void releaseIfNoOpenDispute(Long rentalId, Long inquiryId) {
		if (!inquiryRepository.findOpenDisputeIdsForShare(rentalId).isEmpty()) {
			return;
		}
		if (settlementTxService.releaseForDispute(rentalId)) {
			log.info("분쟁 종료로 정산 보류 해제: rentalId={}, inquiryId={}", rentalId, inquiryId);
		}
	}

	private void validateAnswer(String answer) {
		if (answer == null) {
			return;
		}
		if (answer.isBlank()) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("answer", "답변을 입력해 주세요.")));
		}
		int max = policyProperties.inquiry().answerMax();
		if (answer.codePointCount(0, answer.length()) > max) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("answer", max + "자 이하로 입력해 주세요.")));
		}
	}
}
