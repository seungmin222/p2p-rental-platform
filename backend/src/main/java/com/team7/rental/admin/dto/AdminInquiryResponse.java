package com.team7.rental.admin.dto;

import java.time.LocalDateTime;

import com.team7.rental.admin.entity.Inquiry;
import com.team7.rental.admin.entity.InquiryStatus;
import com.team7.rental.admin.entity.InquiryType;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 문의 목록·처리 결과 (REQ-057) */
public record AdminInquiryResponse(
	@Schema(description = "문의 ID", example = "1")
	Long inquiryId,

	@Schema(description = "접수한 회원 ID", example = "3")
	Long memberId,

	@Schema(description = "문의 종류: GENERAL 일반 문의, DISPUTE 분쟁")
	InquiryType type,

	@Schema(description = "연결한 대여 ID. 없으면 null", example = "10")
	Long rentalId,

	@Schema(description = "문의 내용", example = "반납 후 상태 확인 결과에 이의가 있습니다.")
	String content,

	@Schema(description = "처리 상태: RECEIVED 접수, IN_PROGRESS 처리 중, RESOLVED 처리 완료")
	InquiryStatus status,

	@Schema(description = "관리자 답변. 없으면 null", example = "상태 확인 사진을 검토해 정산 보류를 해제했습니다.")
	String answer,

	@Schema(description = "답변을 마지막으로 쓴 시각. 없으면 null", example = "2026-11-13T10:00:00")
	LocalDateTime answeredAt,

	@Schema(description = "접수 시각", example = "2026-11-12T15:20:00")
	LocalDateTime createdAt,

	@Schema(description = "마지막 변경 시각", example = "2026-11-13T10:00:00")
	LocalDateTime updatedAt) {

	public static AdminInquiryResponse of(Inquiry inquiry) {
		return new AdminInquiryResponse(inquiry.getId(), inquiry.getMemberId(), inquiry.getType(),
			inquiry.getRentalId(), inquiry.getContent(), inquiry.getStatus(), inquiry.getAnswer(),
			inquiry.getAnsweredAt(), inquiry.getCreatedAt(), inquiry.getUpdatedAt());
	}
}
