package com.team7.rental.review.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.team7.rental.rental.entity.RentalRole;
import com.team7.rental.review.entity.ComplimentType;
import com.team7.rental.review.entity.Satisfaction;

import io.swagger.v3.oas.annotations.media.Schema;

/** 받은 후기 목록 항목 (REQ-034, 프로필 SCR-13) */
public record ReceivedReviewResponse(
	@Schema(description = "후기 ID", example = "1")
	Long reviewId,

	@Schema(description = "작성자")
	Writer writer,

	@Schema(description = "작성자가 그 대여에서 맡은 역할: RENTER면 대여자가 쓴 후기(받은 회원은 제공자), OWNER면 제공자가 쓴 후기(받은 회원은 대여자)")
	RentalRole writerRole,

	@Schema(description = "만족도")
	Satisfaction satisfaction,

	@Schema(description = "칭찬 항목 (Enum 선언 순서). 없으면 빈 배열")
	List<ComplimentType> compliments,

	@Schema(description = "후기 내용. 없으면 null", example = "깨끗하게 잘 썼어요.", nullable = true)
	String content,

	@Schema(description = "작성 시각", example = "2026-11-14T10:00:00")
	LocalDateTime createdAt) {

	@Schema(name = "ReceivedReviewWriter", description = "후기 작성자")
	public record Writer(
		@Schema(description = "작성자 회원 ID", example = "3")
		Long memberId,

		@Schema(description = "닉네임. 탈퇴한 작성자면 null(화면이 \"탈퇴한 사용자\"로 표시)", example = "캠핑러", nullable = true)
		String nickname,

		@Schema(description = "프로필 이미지 URL. 없거나 탈퇴한 작성자면 null", nullable = true)
		String profileImageUrl,

		@Schema(description = "탈퇴(WITHDRAWN) 회원이면 true")
		boolean isWithdrawn) {
	}
}
