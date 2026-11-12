package com.team7.rental.admin.dto;

import java.time.LocalDateTime;

import com.team7.rental.admin.entity.Inquiry;
import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.entity.InquiryType;

import io.swagger.v3.oas.annotations.media.Schema;

/** 문의·분쟁 (REQ-063) */
public record InquiryResponse(
	@Schema(description = "문의 ID", example = "1")
	Long inquiryId,

	@Schema(description = "문의 종류: GENERAL 일반 문의, DISPUTE 분쟁")
	InquiryType type,

	@Schema(description = "연결한 대여 ID. 없으면 null", example = "10")
	Long rentalId,

	@Schema(description = "문의 내용", example = "반납 후 상태 확인 결과에 이의가 있습니다.")
	String content,

	@Schema(description = "처리 상태: RECEIVED 접수, IN_PROGRESS 처리 중, RESOLVED 처리 완료")
	InquiryStatus status,

	@Schema(description = "접수 시각", example = "2026-11-12T15:20:00")
	LocalDateTime createdAt) {

	public static InquiryResponse of(Inquiry inquiry) {
		return new InquiryResponse(inquiry.getId(), inquiry.getType(), inquiry.getRentalId(), inquiry.getContent(),
			inquiry.getStatus(), inquiry.getCreatedAt());
	}
}
