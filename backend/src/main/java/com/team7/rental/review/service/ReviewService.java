package com.team7.rental.review.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.team7.rental.global.error.BusinessException;
import com.team7.rental.global.error.ErrorCode;
import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.global.response.FieldErrorDetail;
import com.team7.rental.review.dto.ReviewCreateRequest;
import com.team7.rental.review.dto.ReviewResponse;
import com.team7.rental.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 후기 작성 (REQ-034). 저장은 {@link ReviewTxService}가 하고, 여기서는 입력 검사와 동시 중복 작성의 유니크 위반을 409로 바꾼다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewTxService reviewTxService;
	private final ReviewRepository reviewRepository;
	private final PolicyProperties policyProperties;

	/**
	 * @throws BusinessException COMMON_INVALID_INPUT(내용 길이), RENTAL_NOT_FOUND, RENTAL_NOT_PARTICIPANT,
	 *     RENTAL_INVALID_TRANSITION(COMPLETED 아님), REVIEW_DEADLINE_PASSED, REVIEW_ALREADY_WRITTEN
	 */
	public ReviewResponse write(Long memberId, Long rentalId, ReviewCreateRequest request) {
		String content = normalizeContent(request.content());
		try {
			return reviewTxService.write(memberId, rentalId, request.satisfaction(), request.compliments(), content);
		} catch (DataIntegrityViolationException e) {
			// 같은 (대여, 작성자)가 동시에 들어와 늦은 쪽이 유니크 위반이면 이미 작성이다. 아니면(FK 등) 원래 예외를 던진다
			if (!reviewRepository.existsByRentalIdAndWriterId(rentalId, memberId)) {
				throw e;
			}
			log.info("Review write race resolved as duplicate: rentalId={}, writerId={}", rentalId, memberId);
			throw new BusinessException(ErrorCode.REVIEW_ALREADY_WRITTEN);
		}
	}

	/** 공백만이면 null. 앞뒤 공백은 그대로 둔다 */
	private String normalizeContent(String content) {
		if (content == null || content.isBlank()) {
			return null;
		}
		int max = policyProperties.review().contentMax();
		if (content.codePointCount(0, content.length()) > max) {
			throw new BusinessException(ErrorCode.COMMON_INVALID_INPUT,
				List.of(new FieldErrorDetail("content", max + "자 이하로 입력해 주세요.")));
		}
		return content;
	}
}
