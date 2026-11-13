package com.team7.rental.review.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.rental.dto.RentalReviewTarget;
import com.team7.rental.rental.service.RentalParticipantReader;
import com.team7.rental.review.dto.ReviewResponse;
import com.team7.rental.review.entity.ComplimentType;
import com.team7.rental.review.entity.Review;
import com.team7.rental.review.entity.Satisfaction;
import com.team7.rental.review.event.ReviewWrittenEvent;
import com.team7.rental.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

/** 후기 가드 확인과 저장 (REQ-034). {@link ReviewService}만 부른다 */
@Service
@RequiredArgsConstructor
public class ReviewTxService {

	private final ReviewRepository reviewRepository;
	private final RentalParticipantReader rentalParticipantReader;
	private final ApplicationEventPublisher eventPublisher;
	private final PolicyProperties policyProperties;
	private final Clock clock;

	/**
	 * 가드 순서: 대여 있음 → 당사자 → COMPLETED → 기한(completedAt + review.write-deadline-days 정각까지) → (대여, 작성자) 1회.
	 * 1회 검사를 지나 동시에 저장하면 유니크 위반이 이 메서드 밖으로 나간다(호출하는 쪽이 409로 바꾼다).
	 */
	@Transactional
	public ReviewResponse write(Long memberId, Long rentalId, Satisfaction satisfaction,
		Collection<ComplimentType> compliments, String content) {
		RentalReviewTarget rental = rentalParticipantReader.getReviewTarget(rentalId);
		if (!rental.isParticipant(memberId)) {
			throw new BusinessException(ErrorCode.RENTAL_NOT_PARTICIPANT);
		}
		if (!rental.isCompleted()) {
			throw new BusinessException(ErrorCode.RENTAL_INVALID_TRANSITION);
		}
		if (!rental.isWithinReviewDeadline(LocalDateTime.now(clock), policyProperties.review().writeDeadlineDays())) {
			throw new BusinessException(ErrorCode.REVIEW_DEADLINE_PASSED);
		}
		if (reviewRepository.existsByRentalIdAndWriterId(rentalId, memberId)) {
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_WRITTEN);
		}
		Long targetId = rental.counterpartOf(memberId);
		Review review = reviewRepository.saveAndFlush(
			Review.write(rentalId, memberId, targetId, satisfaction, compliments, content));
		eventPublisher.publishEvent(new ReviewWrittenEvent(review.getId(), rentalId, memberId, targetId));
		return ReviewResponse.of(review);
	}
}
