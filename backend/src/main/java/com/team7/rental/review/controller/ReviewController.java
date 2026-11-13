package com.team7.rental.review.controller;

import static com.team7.rental.global.error.ErrorCode.*;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.team7.rental.global.config.OpenApiConfig;
import com.team7.rental.global.error.ApiErrorCodes;
import com.team7.rental.global.response.ApiResponse;
import com.team7.rental.global.response.PageResponse;
import com.team7.rental.global.security.LoginMember;
import com.team7.rental.review.dto.ReceivedReviewListRequest;
import com.team7.rental.review.dto.ReceivedReviewResponse;
import com.team7.rental.review.dto.ReviewCreateRequest;
import com.team7.rental.review.dto.ReviewResponse;
import com.team7.rental.review.service.ReviewQueryService;
import com.team7.rental.review.service.ReviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Review", description = "거래 후기")
@RestController
@RequiredArgsConstructor
public class ReviewController {

	private final ReviewService reviewService;
	private final ReviewQueryService reviewQueryService;

	@Operation(summary = "후기 작성 (REQ-034)", description = "거래 완료(COMPLETED)된 대여의 당사자가 상대에게 후기를 쓴다(대여자→제공자, 제공자→대여자). "
		+ "completedAt + policies.md review.write-deadline-days(7일) 정각까지 쓸 수 있고, 한 대여에 회원당 1번이다. "
		+ "COMPLETED가 아니면 RENTAL_INVALID_TRANSITION. 저장되면 상대에게 REVIEW_RECEIVED 알림(대상 RENTAL)을 보낸다. 수정·삭제는 없다.")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	@ApiErrorCodes({COMMON_INVALID_INPUT, AUTH_UNAUTHORIZED, RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT,
		RENTAL_INVALID_TRANSITION, REVIEW_DEADLINE_PASSED, REVIEW_ALREADY_WRITTEN})
	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping("/api/rentals/{rentalId}/reviews")
	public ApiResponse<ReviewResponse> write(@LoginMember
	Long memberId, @PathVariable
	Long rentalId, @Valid @RequestBody
	ReviewCreateRequest request) {
		return ApiResponse.ok(reviewService.write(memberId, rentalId, request));
	}

	@Operation(summary = "받은 후기 목록 (REQ-034)", description = "회원이 받은 후기, 최신 작성 순. 비회원도 볼 수 있다. "
		+ "writerRole로 대여자가 쓴 후기(RENTER)와 제공자가 쓴 후기(OWNER)를 구분한다. 탈퇴한 작성자는 닉네임·사진이 null이다. "
		+ "페이지 크기는 policies.md review.page로 고정이다.")
	@ApiErrorCodes({COMMON_INVALID_INPUT, MEMBER_NOT_FOUND})
	@GetMapping("/api/members/{memberId}/reviews")
	public ApiResponse<PageResponse<ReceivedReviewResponse>> getReceivedReviews(@PathVariable
	Long memberId, @ParameterObject @Valid @ModelAttribute
	ReceivedReviewListRequest request) {
		return ApiResponse.ok(reviewQueryService.getReceivedReviews(memberId, request.page()));
	}
}
