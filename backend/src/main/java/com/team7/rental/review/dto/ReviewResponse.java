package com.team7.rental.review.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.team7.rental.review.entity.ComplimentType;
import com.team7.rental.review.entity.Review;
import com.team7.rental.review.entity.Satisfaction;

import io.swagger.v3.oas.annotations.media.Schema;

/** 작성한 후기 (REQ-034) */
public record ReviewResponse(
	@Schema(description = "후기 ID", example = "1")
	Long reviewId,

	@Schema(description = "대여 ID", example = "10")
	Long rentalId,

	@Schema(description = "후기를 받은 회원(상대 당사자) ID", example = "2")
	Long targetId,

	@Schema(description = "만족도")
	Satisfaction satisfaction,

	@Schema(description = "칭찬 항목 (Enum 선언 순서). 없으면 빈 배열")
	List<ComplimentType> compliments,

	@Schema(description = "후기 내용. 없으면 null", example = "깨끗하게 잘 썼어요.", nullable = true)
	String content,

	@Schema(description = "작성 시각", example = "2026-11-14T10:00:00")
	LocalDateTime createdAt) {

	public static ReviewResponse of(Review review) {
		return new ReviewResponse(review.getId(), review.getRentalId(), review.getTargetId(), review.getSatisfaction(),
			review.getCompliments().stream().sorted().toList(), review.getContent(), review.getCreatedAt());
	}
}
